[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Fail([string]$Message) {
    throw "Build failed: $Message"
}

$scriptDirectory = Split-Path -Parent $PSCommandPath
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $scriptDirectory '..'))
$gradleWrapper = Join-Path $repositoryRoot 'gradlew.bat'
$distDirectory = Join-Path $repositoryRoot 'dist'
$stagingDirectory = $null

try {
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        Fail 'Java is required. Install a local JDK compatible with this Java 8-targeted project and put it on PATH.'
    }
    if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
        Fail "Gradle wrapper not found at $gradleWrapper. Restore the repository wrapper files."
    }

    Write-Host 'Building with the checked-in Gradle wrapper...'
    Push-Location -LiteralPath $repositoryRoot
    try {
        & $gradleWrapper build
        if ($LASTEXITCODE -ne 0) {
            Fail "Gradle exited with code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }

    $artifacts = @(
        Get-ChildItem -LiteralPath $repositoryRoot -Recurse -File -Filter '*.jar' |
            Where-Object {
                $_.FullName -match '[\\/]build[\\/]libs[\\/]' -and
                $_.Name -match '^firebadnofire-storage-tweaks-.+-mc.+-forge\.jar$'
            } |
            Sort-Object FullName
    )
    if ($artifacts.Count -eq 0) {
        Fail 'Gradle completed but produced no distributable Forge JAR under */build/libs. Check the Forge module build output.'
    }

    New-Item -ItemType Directory -Path $distDirectory -Force | Out-Null
    $stagingDirectory = Join-Path $distDirectory ('.staging-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $stagingDirectory -Force | Out-Null

    foreach ($artifact in $artifacts) {
        if ($artifact.Length -eq 0) {
            Fail "Expected artifact is empty: $($artifact.FullName)"
        }
        $stagedArtifact = Join-Path $stagingDirectory $artifact.Name
        if (Test-Path -LiteralPath $stagedArtifact) {
            Fail "Two modules produced the same distribution filename: $($artifact.Name)"
        }
        Copy-Item -LiteralPath $artifact.FullName -Destination $stagedArtifact -ErrorAction Stop
    }

    # Only replace prior artifacts owned by this project; unrelated files in dist are preserved.
    Get-ChildItem -LiteralPath $distDirectory -File -Filter '*.jar' |
        Where-Object { $_.Name -match '^firebadnofire-storage-tweaks-.+-mc.+-forge\.jar$' } |
        Remove-Item -Force -ErrorAction Stop
    Get-ChildItem -LiteralPath $stagingDirectory -File -Filter '*.jar' |
        ForEach-Object { Move-Item -LiteralPath $_.FullName -Destination (Join-Path $distDirectory $_.Name) -Force -ErrorAction Stop }
    Remove-Item -LiteralPath $stagingDirectory -Force -ErrorAction Stop
    $stagingDirectory = $null

    Write-Host 'Distribution artifact(s):'
    Get-ChildItem -LiteralPath $distDirectory -File -Filter '*.jar' |
        Where-Object { $_.Name -match '^firebadnofire-storage-tweaks-.+-mc.+-forge\.jar$' } |
        Sort-Object Name |
        ForEach-Object { Write-Host $_.FullName }
}
finally {
    if ($null -ne $stagingDirectory -and (Test-Path -LiteralPath $stagingDirectory)) {
        Remove-Item -LiteralPath $stagingDirectory -Recurse -Force
    }
}
