#!/usr/bin/env python3
"""Validate an existing release JAR and package Git-tracked working-tree sources.

Run after Gradle's build task. This script never builds the mod itself.
Uncommitted changes to tracked files are included; no commit or clean tree is required.
Use --include-untracked only for a development snapshot that also includes new files.
Examples: python scripts/package-release.py --check-only
          python scripts/package-release.py --output-dir dist
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import sys
import tempfile
from zipfile import BadZipFile, ZIP_DEFLATED, ZipFile, ZipInfo


ARCHIVE_BASE = "me-storage-controller-forge-1.20.1"
SOURCE_PREFIX = "me-storage-controller"
EXCLUDED_DIRECTORIES = {".git", ".gradle", "build", "run", "runs", "dist",
                        "cache", "caches", ".cache", "__pycache__"}
REQUIRED_CLASSES = (
    "client/ControllerGuide", "storage/compat/OmniCellCapacity",
    "config/ControllerConfig", "config/ServerConfigWatcher",
    "network/DirectoryTransfer", "folder/FolderService", "folder/FolderSavedData",
    "client/FolderOverlay", "client/TreeStateStore", "network/FolderTransfer",
)
REQUIRED_SOURCE_FILES = (
    "build.gradle", "gradle.properties", "gradlew", "scripts/package-release.py",
    "src/main/java/dev/mestorage/controller/MEStorageController.java",
    "src/main/resources/assets/minecraft/atlases/blocks.json",
)


class ValidationError(Exception):
    """An input artifact does not satisfy release requirements."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def read_version(repo: Path) -> str:
    properties = repo / "gradle.properties"
    match = None
    if properties.is_file():
        match = re.search(r"(?m)^\s*mod_version\s*[:=]\s*([^\r\n]+)",
                          properties.read_text(encoding="utf-8-sig"))
    if match is None:
        match = re.search(r"(?m)^\s*version\s*=\s*['\"]([^'\"]+)['\"]",
                          (repo / "build.gradle").read_text(encoding="utf-8-sig"))
    require(match is not None, "Set mod_version in gradle.properties (or a literal build.gradle version).")
    version = match.group(1).strip()
    require(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._+\-]*", version) is not None,
            f"Unsafe or invalid release version: {version!r}")
    return version


def validate_jar(jar: Path, version: str) -> None:
    require(jar.is_file(), f"Release JAR not found: {jar}. Run Gradle build first.")
    with ZipFile(jar) as archive:
        require(archive.testzip() is None, "JAR CRC failure")
        entries = archive.namelist()
        names = set(entries)
        require(len(names) == len(entries), "Duplicate JAR entries")
        require(not any("/ClientSmokeTest" in n or "/test/" in n for n in names),
                "Development harness leaked into release JAR")
        require("data/me_storage_controller/structures/empty.nbt" not in names,
                "GameTest structure leaked into release JAR")
        require(not any("controller_atlas.png" in n for n in names), "Obsolete generated texture remains")
        for name in ("assets/minecraft/atlases/blocks.json", "AE2-ASSET-NOTICE.txt",
                     "licenses/CC-BY-NC-SA-3.0.txt", "META-INF/licenses/MIT.txt", "META-INF/mods.toml",
                     "META-INF/MANIFEST.MF", "me_storage_controller.mixins.json"):
            require(name in names, f"Required release resource missing: {name}")
        atlas = json.loads(archive.read("assets/minecraft/atlases/blocks.json"))
        require(len(atlas.get("sources", [])) == 9, "Expected 9 block-atlas sources")
        modern_textures = [n for n in names if n.startswith("assets/me_storage_controller/textures/ae2_1_21/")
                           and n.endswith(".png")]
        require(len(modern_textures) == 13, "Expected 13 licensed AE2 textures")
        metadata = archive.read("META-INF/mods.toml").decode("utf-8")
        require(re.search(r"(?m)^\s*version\s*=\s*['\"]" + re.escape(version) + r"['\"]", metadata) is not None,
                f"JAR mods.toml version does not match {version}")
        for relative in REQUIRED_CLASSES:
            name = f"dev/mestorage/controller/{relative}.class"
            require(name in names, f"Required release class missing: {name}")
        manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8").replace("\r\n", "\n").replace("\n ", "")
        require("MixinConfigs: me_storage_controller.mixins.json" in manifest, "Missing release mixin manifest entry")
        mixins = json.loads(archive.read("me_storage_controller.mixins.json"))
        require("EnergyServiceMixin" in mixins.get("mixins", []), "EnergyServiceMixin is not registered")
        require(not any("FluxSourceAccess" in n or "MEFluxDiagnostic" in n for n in names),
                "Obsolete energy helper remains")
        require(not any(n.startswith("sonar/fluxnetworks/") for n in names), "Optional Flux dependency was bundled")
        require(not any(n.startswith("com/wintercogs/") for n in names), "Optional OmniCells addon was bundled")
        for language in ("", "_zh_cn/"):
            path = "assets/me_storage_controller/ae2guide/" + language + "controller.md"
            require(path in names, f"Guide article missing: {path}")
            article = archive.read(path).decode("utf-8").replace("\r\n", "\n")
            require("item_ids:\n- me_storage_controller:controller" in article,
                    f"Guide item mapping missing: {path}")
            require("1000" in article and "256" in article, f"Guide compatibility details missing: {path}")


def is_within(path: Path, directory: Path) -> bool:
    try:
        path.relative_to(directory)
        return True
    except ValueError:
        return False


def source_files(repo: Path, output: Path, git: str, include_untracked: bool = False) -> list[tuple[str, Path]]:
    command = [git, "-c", f"safe.directory={repo.as_posix()}", "-C", str(repo), "ls-files", "--cached"]
    if include_untracked:
        command.append("--others")
    command.extend(("--exclude-standard", "-z"))
    result = subprocess.run(command,
                            check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    files = []
    for relative in sorted(set(os.fsdecode(result.stdout).split("\0"))):
        if not relative:
            continue
        parts = PurePosixPath(relative).parts
        require(not PurePosixPath(relative).is_absolute() and ".." not in parts,
                f"Unsafe source path returned by Git: {relative}")
        if any(part in EXCLUDED_DIRECTORIES for part in parts[:-1]):
            continue
        if (parts[-1] == "SHA256SUMS.txt"
                or parts[-1].startswith(ARCHIVE_BASE + "-") and parts[-1].endswith(".jar")
                or parts[-1].startswith(SOURCE_PREFIX + "-") and parts[-1].endswith("-source.zip")):
            continue
        path = repo.joinpath(*parts)
        resolved = path.resolve()
        require(is_within(resolved, repo), f"Source symlink escapes the repository: {relative}")
        # An external output directory may itself be an ancestor of this repository.
        # Exclude its subtree only when that subtree is actually inside the source root.
        if (output != repo and is_within(output, repo) and is_within(resolved, output)) or not path.is_file():
            continue
        files.append((relative, path))
    require(files, "Git returned no source files")
    included = {relative for relative, _ in files}
    missing = [relative for relative in REQUIRED_SOURCE_FILES if relative not in included]
    require(not missing,
            "Required source files are missing from the package: " + ", ".join(missing)
            + ". Restore them if deleted, or run git add for these files before packaging; no commit is required."
            + (" For a development snapshot, --include-untracked also includes new, non-ignored files." if not include_untracked else ""))
    return files


def write_source_zip(destination: Path, files: list[tuple[str, Path]]) -> None:
    with ZipFile(destination, "w", compression=ZIP_DEFLATED, compresslevel=9) as archive:
        for relative, path in files:
            # Stable timestamps/order make repeated packaging of unchanged sources reproducible.
            entry = ZipInfo(f"{SOURCE_PREFIX}/{relative}", date_time=(1980, 1, 1, 0, 0, 0))
            entry.create_system = 3
            entry.external_attr = (0o100755 if relative == "gradlew" or relative.endswith(".sh") else 0o100644) << 16
            archive.writestr(entry, path.read_bytes(), compress_type=ZIP_DEFLATED, compresslevel=9)
    with ZipFile(destination) as archive:
        require(archive.testzip() is None, "Source ZIP CRC failure")
        for relative in REQUIRED_SOURCE_FILES:
            require(f"{SOURCE_PREFIX}/{relative}" in archive.namelist(), f"Source ZIP is missing {relative}")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def package(repo: Path, output: Path, jar: Path, version: str, git: str, include_untracked: bool = False) -> None:
    files = source_files(repo, output, git, include_untracked)
    output.mkdir(parents=True, exist_ok=True)
    jar_name = f"{ARCHIVE_BASE}-{version}.jar"
    source_name = f"{SOURCE_PREFIX}-{version}-source.zip"
    with tempfile.TemporaryDirectory(prefix=".package-", dir=output) as staging:
        stage = Path(staging)
        staged_jar = stage / jar_name
        shutil.copyfile(jar, staged_jar)
        validate_jar(staged_jar, version)
        write_source_zip(stage / source_name, files)
        artifact_names = sorted((jar_name, source_name))
        sums = "".join(f"{sha256(stage / name)}  {name}\n" for name in artifact_names)
        (stage / "SHA256SUMS.txt").write_text(sums, encoding="utf-8", newline="\n")
        # All artifacts have passed validation before publishing. Each rename is atomic;
        # publish checksums last so readers can detect an interrupted multi-file release.
        for name in (*artifact_names, "SHA256SUMS.txt"):
            os.replace(stage / name, output / name)
    for name in artifact_names:
        artifact = output / name
        print(f"{artifact}  {artifact.stat().st_size} bytes  sha256={sha256(artifact)}")
    print(output / "SHA256SUMS.txt")


def main(argv: list[str] | None = None) -> int:
    repo = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--output-dir", type=Path, default=repo / "dist", help="Release destination (default: repository/dist)")
    parser.add_argument("--jar", type=Path, help="Existing JAR to validate (default: build/libs/<mod>-<version>.jar)")
    parser.add_argument("--git", help="Git executable (default: git from PATH)")
    parser.add_argument("--include-untracked", action="store_true",
                        help="Development snapshot: also include non-ignored untracked source files (default: tracked files only)")
    parser.add_argument("--check-only", action="store_true", help="Validate the JAR without writing any files or invoking Git")
    args = parser.parse_args(argv)
    try:
        version = read_version(repo)
        jar = (args.jar or repo / "build" / "libs" / f"{ARCHIVE_BASE}-{version}.jar").resolve()
        validate_jar(jar, version)
        if args.check_only:
            print(f"Validated {jar} (version {version})")
            return 0
        git = args.git or shutil.which("git")
        require(bool(git), "Git was not found on PATH; pass --git /path/to/git")
        package(repo, args.output_dir.resolve(), jar, version, git, args.include_untracked)
        return 0
    except (ValidationError, OSError, BadZipFile, ValueError, subprocess.CalledProcessError) as failure:
        detail = failure.stderr.decode(errors="replace").strip() if isinstance(failure, subprocess.CalledProcessError) and failure.stderr else str(failure)
        print(f"Packaging failed: {detail}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
