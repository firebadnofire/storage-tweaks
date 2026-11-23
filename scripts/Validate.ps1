param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$GradleHome = $env:GRADLE_USER_HOME,
    [switch]$RuntimeTest
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $GradleHome) { $GradleHome = Join-Path ([IO.Path]::GetPathRoot($repoRoot)) 'storage-tweaks-validation/gradle' }
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
    throw 'Pass -JavaHome pointing to a Java 8 JDK. No system Java installation will be changed.'
}
$env:JAVA_HOME = $JavaHome
$env:GRADLE_USER_HOME = $GradleHome
Push-Location $repoRoot
try {
    & .\gradlew.bat --no-daemon :common:test :forge-1.12.2:build
    if ($LASTEXITCODE -ne 0) { throw "Build/tests failed with exit $LASTEXITCODE" }
    if ($RuntimeTest) {
        & "$PSScriptRoot/Validate-IsolatedGui.ps1" -JavaHome $JavaHome -GradleHome $GradleHome
    }
    Get-ChildItem -LiteralPath 'forge-1.12.2/build/libs' -Filter '*-mc1.12.2-forge*.jar' | ForEach-Object {
        $entries = & (Join-Path $JavaHome 'bin/jar.exe') tf $_.FullName
        if ($LASTEXITCODE -ne 0) { throw "Cannot inspect artifact $($_.FullName)" }
        if ($entries -match 'RuntimeTestMod|BaselineTestMod|^net/minecraftforge/') {
            throw "Distribution artifact contains test or Forge implementation classes: $($_.FullName)"
        }
        $digest = Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
        ($digest.Hash.ToLowerInvariant() + '  ' + $_.Name) | Set-Content -LiteralPath ($_.FullName + '.sha256') -Encoding ASCII
    }
} finally { Pop-Location }
