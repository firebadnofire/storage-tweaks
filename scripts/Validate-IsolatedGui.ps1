param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$GradleHome = $env:GRADLE_USER_HOME,
    [string]$ValidationRoot,
    [string]$Distribution = 'Ubuntu'
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $ValidationRoot) { $ValidationRoot = Join-Path ([IO.Path]::GetPathRoot($repoRoot)) 'storage-tweaks-validation' }
if (-not $GradleHome) { $GradleHome = Join-Path $ValidationRoot 'gradle' }
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
    throw 'Pass -JavaHome pointing to the pinned Java 8 JDK.'
}
if (-not (Test-Path -LiteralPath (Join-Path $ValidationRoot 'linux-tools/root/usr/bin/Xvfb'))) {
    throw 'Prepare the isolated Linux tools described in docs/testing.md. No visible-desktop fallback will be launched.'
}
$env:JAVA_HOME = $JavaHome
$env:GRADLE_USER_HOME = $GradleHome
Push-Location $repoRoot
try {
    & .\gradlew.bat --no-daemon :forge-1.12.2:exportProductionRuntime
    if ($LASTEXITCODE -ne 0) { throw "Runtime export failed with exit $LASTEXITCODE" }
    $linuxRepoOutput = & wsl.exe -d $Distribution -- wslpath -a -u $repoRoot.Replace('\','/')
    if ($LASTEXITCODE -ne 0 -or -not $linuxRepoOutput) { throw 'Cannot resolve the repository path inside WSL' }
    $linuxRepo = $linuxRepoOutput.Trim()
    $linuxValidationOutput = & wsl.exe -d $Distribution -- wslpath -a -u $ValidationRoot.Replace('\','/')
    if ($LASTEXITCODE -ne 0 -or -not $linuxValidationOutput) { throw 'Cannot resolve the validation directory inside WSL' }
    $linuxValidation = $linuxValidationOutput.Trim()
    $log = Join-Path $repoRoot 'validation-isolated-gui.log'
    & wsl.exe -d $Distribution -u root -- env "ST_GUI_TOOLS=$linuxValidation/linux-tools" `
        sh "$linuxRepo/test-harness/linux-virtual-display.sh" `
        python3 "$linuxRepo/test-harness/linux-client.py" `
        --manifest "$linuxRepo/forge-1.12.2/build/production-runtime.json" `
        --instance "$linuxValidation/linux-integrated" --native-cache "$linuxValidation/linux-natives" *> $log
    if ($LASTEXITCODE -ne 0) { throw "Isolated client failed with exit $LASTEXITCODE; inspect $log" }
    if ((Select-String -LiteralPath $log -SimpleMatch 'STORAGE_TWEAKS_RUNTIME_TEST_FAILED' -Quiet) -or
        -not (Select-String -LiteralPath $log -SimpleMatch 'ALL INTEGRATED RUNTIME TESTS PASSED' -Quiet)) {
        throw "Runtime assertions did not pass; inspect $log"
    }
    Write-Host "Private-display integrated assertions passed; log: $log"
} finally { Pop-Location }
