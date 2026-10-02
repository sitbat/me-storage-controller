[CmdletBinding()]
param(
    [ValidateSet('Build', 'Test', 'TestServer', 'Smoke', 'Demo')]
    [string]$Task = 'Build',
    [string]$JavaHome,
    [string]$GradleHome,
    [string]$RunDir,
    [string]$DemoWorld = 'ME-Controller-Demo',
    [string]$CompatModsDir,
    [string]$SmokeOutput,
    [switch]$Eae,
    [switch]$Mek,
    [switch]$Omni,
    [switch]$Flux,
    [switch]$AllCompat,
    [switch]$Offline,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$localWork = [System.IO.Path]::GetFullPath((Join-Path $repoRoot '../../work'))

function Resolve-RepoPath([string]$Path) {
    if ([System.IO.Path]::IsPathRooted($Path)) { return [System.IO.Path]::GetFullPath($Path) }
    return [System.IO.Path]::GetFullPath((Join-Path $repoRoot $Path))
}

# Explicit parameters take precedence, followed by the environment and this
# workspace's existing tools. A standalone checkout needs no Codex directory.
if (-not $JavaHome) { $JavaHome = $env:JAVA_HOME }
if (-not $JavaHome -and (Test-Path -LiteralPath (Join-Path $localWork 'jdk17'))) {
    $localJdk = Get-ChildItem -LiteralPath (Join-Path $localWork 'jdk17') -Directory |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/java.exe') } |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($localJdk) { $JavaHome = $localJdk.FullName }
}
if ($JavaHome) {
    $JavaHome = Resolve-RepoPath $JavaHome
    if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
        throw "Java executable was not found under '$JavaHome'. Supply a JDK 17 with -JavaHome."
    }
} elseif (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'JDK 17 is required. Set JAVA_HOME, put java on PATH, or pass -JavaHome.'
}
if (-not $GradleHome) { $GradleHome = $env:GRADLE_USER_HOME }
if (-not $GradleHome -and (Test-Path -LiteralPath (Join-Path $localWork 'gradle-home'))) {
    $GradleHome = Join-Path $localWork 'gradle-home'
}
if ($GradleHome) { $GradleHome = Resolve-RepoPath $GradleHome }

$gradleArgs = @('--console=plain')
if ($Offline) { $gradleArgs += '--offline' }
switch ($Task) {
    'Build'      { $gradleArgs += 'build' }
    'Test'       { $gradleArgs += 'test' }
    'TestServer' { $gradleArgs += 'runGameTestServer'; if (-not $RunDir) { $RunDir = 'run/gametest' } }
    'Smoke'      { $gradleArgs += @('runClient', '-PsmokeTest'); if (-not $RunDir) { $RunDir = 'run/smoke' } }
    'Demo'       { $gradleArgs += @('runClient', '-Pdemo', "-PdemoWorld=$DemoWorld"); if (-not $RunDir) { $RunDir = 'run/demo' } }
}
if ($RunDir) { $RunDir = Resolve-RepoPath $RunDir; $gradleArgs += "-PrunDir=$RunDir" }
if ($Task -in @('Smoke', 'Demo')) {
    $worldName = if ($Task -eq 'Smoke') { 'SmokeTest' } else { $DemoWorld }
    if ([string]::IsNullOrWhiteSpace($worldName) -or $worldName -match '[\\/]' -or $worldName -in @('.', '..')) {
        throw 'DemoWorld must be one folder name inside the selected run directory''s saves folder.'
    }
    $worldFile = Join-Path (Join-Path (Join-Path $RunDir 'saves') $worldName) 'level.dat'
    if (-not (Test-Path -LiteralPath $worldFile)) {
        throw "Prepared world is missing: $worldFile. See docs/DEVELOPMENT.md before running $Task."
    }
}
if ($SmokeOutput) { $gradleArgs += "-PsmokeOutput=$(Resolve-RepoPath $SmokeOutput)" }

$compatFlags = @()
if ($AllCompat -or $Eae) { $compatFlags += '-PeaeTest' }
if ($AllCompat -or $Mek) { $compatFlags += '-PmekTest' }
if ($AllCompat -or $Omni) { $compatFlags += '-PomniTest' }
if ($AllCompat -or $Flux) { $compatFlags += '-PfluxTest' }
if ($compatFlags.Count -gt 0) {
    if (-not $CompatModsDir) {
        $CompatModsDir = if (Test-Path -LiteralPath (Join-Path $localWork 'vendor')) {
            Join-Path $localWork 'vendor'
        } else { Join-Path $repoRoot 'compat-mods' }
    }
    $CompatModsDir = Resolve-RepoPath $CompatModsDir
    if (-not (Test-Path -LiteralPath $CompatModsDir -PathType Container)) {
        throw "Optional addon directory does not exist: $CompatModsDir. See docs/DEVELOPMENT.md."
    }
    $gradleArgs += $compatFlags
    $gradleArgs += "-PcompatModsDir=$CompatModsDir"
}

Write-Host "Repository: $repoRoot"
Write-Host "Task: $Task"
if ($JavaHome) { Write-Host "JDK: $JavaHome" }
if ($RunDir) { Write-Host "Run directory: $RunDir" }
if ($DryRun) {
    [pscustomobject]@{ Executable = Join-Path $repoRoot 'gradlew.bat'; Arguments = $gradleArgs }
    return
}

$previousJavaHome = $env:JAVA_HOME
$previousGradleHome = $env:GRADLE_USER_HOME
Push-Location -LiteralPath $repoRoot
try {
    if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
    if ($GradleHome) { $env:GRADLE_USER_HOME = $GradleHome }
    & (Join-Path $repoRoot 'gradlew.bat') @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Gradle $Task failed (exit code $LASTEXITCODE)." }
} finally {
    Pop-Location
    $env:JAVA_HOME = $previousJavaHome
    $env:GRADLE_USER_HOME = $previousGradleHome
}
