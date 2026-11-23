[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Fail([string]$Message) {
    throw "Clean failed: $Message"
}

$scriptDirectory = Split-Path -Parent $PSCommandPath
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $scriptDirectory '..'))
$gradleWrapper = Join-Path $repositoryRoot 'gradlew.bat'
$distDirectory = Join-Path $repositoryRoot 'dist'

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Fail 'Java is required to run the Gradle clean task. Install a local compatible JDK and put it on PATH.'
}
if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
    Fail "Gradle wrapper not found at $gradleWrapper. Restore the repository wrapper files."
}

Push-Location -LiteralPath $repositoryRoot
try {
    & $gradleWrapper clean
    if ($LASTEXITCODE -ne 0) {
        Fail "Gradle clean exited with code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

if (Test-Path -LiteralPath $distDirectory -PathType Container) {
    Get-ChildItem -LiteralPath $distDirectory -File -Filter '*.jar' |
        Where-Object { $_.Name -match '^firebadnofire-storage-tweaks-.+-mc.+-forge\.jar$' } |
        Remove-Item -Force -ErrorAction Stop
    Get-ChildItem -LiteralPath $distDirectory -Directory -Filter '.staging.*' |
        Remove-Item -Recurse -Force -ErrorAction Stop
}

Write-Host 'Removed Gradle build outputs and Storage Tweaks distribution artifacts.'
