# ME Storage Controller

[![Version 0.4.17](https://img.shields.io/badge/version-0.4.17-8b6caf?style=flat-square)](CHANGELOG.md)
![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-5c9b45?style=flat-square)
![Forge](https://img.shields.io/badge/loader-Forge-476f95?style=flat-square)
![AE2 15.4.10](https://img.shields.io/badge/AE2-15.4.10-8b6caf?style=flat-square)
[![Code license MIT](https://img.shields.io/badge/code-MIT-3b8c78?style=flat-square)](LICENSE)
[![Asset license CC BY-NC-SA 3.0](https://img.shields.io/badge/assets-CC_BY--NC--SA_3.0-b88444?style=flat-square)](docs/ASSETS.md)

[Download 0.4.17](https://github.com/sitbat/me-storage-controller/releases/tag/v0.4.17) · [简体中文](README.md) · [Documentation](docs/README.md) · [Changelog](CHANGELOG.md)

An **Applied Energistics 2 addon for Minecraft 1.20.1 Forge** for inspecting storage capacity, contents and resource counts by network, folder, device or individual cell. Resources can be inserted or extracted within the selected scope.

## Features

- **Storage browsing:** a hierarchical tree of devices and cells, with search and sorting.
- **Capacity and counts:** capacity units scale in powers of 1024; unknown capacity, unlimited capacity and infinite supply are displayed separately.
- **Shared folders:** select and group devices or cell slots into named, nested folders shared across the ME network. Grouping only changes metadata; cell membership follows its slot.
- **Resource transfers:** move items, fill or empty compatible fluid and chemical containers, and manage complete cells through dedicated cell slots.
- **Interface and location:** light and dark themes, personal tree-state memory and device highlighting, using Minecraft's current GUI scale.

## Installation and use

The development and validation baseline is **Java 17, Minecraft 1.20.1, Forge 47.4.0, AE2 15.4.10 and GuideME 20.1.7**. Use Forge builds of the dependencies.

1. Download the [0.4.17 installation JAR](https://github.com/sitbat/me-storage-controller/releases/download/v0.4.17/me-storage-controller-forge-1.20.1-0.4.17.jar) and place it in `mods` alongside its Forge dependencies. Install the same mod version on both client and server.
2. Connect an **ME Storage Controller** to a powered ME network with **one free channel**.
3. Right-click to open it. Hold **G** over the item or use the sidebar Guide button for the in-game guide, available in English and Simplified Chinese.

The block does not supply channels or replace AE2's ME Controller. Transfers follow native AE2 power, filtering and permission rules. Protocol 10 is incompatible with 0.4.15 and earlier.

The [Release page](https://github.com/sitbat/me-storage-controller/releases/tag/v0.4.17) also provides a source ZIP and `SHA256SUMS.txt`. The ZIP contains source code; the checksum file verifies downloads. Neither belongs in `mods`.

## Build from source

To build the mod yourself, use **JDK 17**:

```powershell
.\gradlew.bat build
```

On Linux/macOS, use `./gradlew build`. Install the JAR without `-sources` from `build/libs/`.

## Optional addon support

| Addon | Support |
| --- | --- |
| ExtendedAE | Extended drives and custom infinity cells, including lava and matter balls |
| AE2 Omni Cells | Omni, Complex Omni and Quantum capacities, type limits and known unlimited bounds |
| Applied Mekanistics + Mekanism | Chemical contents and containers supported by registered AE2 strategies |

Storage buses expose connected contents and any capacity available through the container interface, while retaining their filters and access modes. Only storage visible to the current network is included. Folder transfers stay within their members; unavailable or ambiguous overlapping storage may prevent an aggregate transfer. OmniCells quantities beyond AE2's public 64-bit counters cannot be represented exactly. See the [user guide](docs/USER_GUIDE.md) for details.

The [documentation](docs/README.md) is primarily in Chinese. It includes [development instructions](docs/DEVELOPMENT.md), [validation records](docs/VALIDATION.md) and the [changelog](CHANGELOG.md).

## License

Original code, scripts and documentation use [MIT](LICENSE). Included AE2 artwork, derived models and visual adaptations use **CC BY-NC-SA 3.0**; see [asset provenance and attribution](docs/ASSETS.md) and the [bundled notice](src/main/resources/AE2-ASSET-NOTICE.txt).

This is an independent addon, not an official AE2 project.
