param([string]$UpperVersion = '26.3')
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$output = Join-Path $repoRoot 'docs/targets.json'
$manifestUrl = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
$manifest = Invoke-RestMethod $manifestUrl
$upper = $manifest.versions | Where-Object { $_.id -eq $UpperVersion -and $_.type -eq 'release' }
$lower = $manifest.versions | Where-Object { $_.id -eq '1.8' -and $_.type -eq 'release' }
if (-not $upper -or -not $lower) { throw 'Stable range endpoints are absent from the official manifest' }
$forgeMetadata = [xml](Invoke-WebRequest 'https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml').Content
$forgeVersions = @($forgeMetadata.metadata.versioning.versions.version)
$fabricGames = @(Invoke-RestMethod 'https://meta.fabricmc.net/v2/versions/game')
$prior = @{}
if (Test-Path -LiteralPath $output) {
    $previous = Get-Content -LiteralPath $output -Raw | ConvertFrom-Json
    foreach ($target in $previous.targets) { $prior[($target.minecraft + '|' + $target.loader)] = $target }
}
$targets = @()
$releases = @($manifest.versions | Where-Object {
    $_.type -eq 'release' -and [datetime]$_.releaseTime -ge [datetime]$lower.releaseTime -and
    [datetime]$_.releaseTime -le [datetime]$upper.releaseTime
} | Sort-Object { [datetime]$_.releaseTime })
foreach ($release in $releases) {
    foreach ($loader in @('forge','fabric','neoforge','quilt')) {
        $key = $release.id + '|' + $loader
        $availability = 'not checked'
        if ($loader -eq 'forge') {
            $availability = if (@($forgeVersions | Where-Object { $_ -like ($release.id + '-*') }).Count) { 'listed by loader' } else { 'not listed by loader' }
        }
        if ($loader -eq 'fabric') {
            $availability = if (@($fabricGames | Where-Object { $_.version -eq $release.id }).Count) { 'listed by loader' } else { 'not listed by loader' }
        }
        if ($prior.ContainsKey($key)) {
            $target = $prior[$key]
            $target.availability = $availability
        } else {
            $target = [ordered]@{
                minecraft = $release.id; loader = $loader; availability = $availability
                loaderVersion = $null; java = $null; gradle = $null; mappings = $null
                build = 'not started'; client = 'not started'; dedicatedServer = 'not started'
                gameplay = 'not started'; caveats = 'No Storage Tweaks adapter implemented'
            }
        }
        $targets += $target
    }
}
$record = [ordered]@{
    manifest = $manifestUrl; upper = $UpperVersion; observedUtc = [datetime]::UtcNow.ToString('o')
    stableReleaseCount = $releases.Count; targets = $targets
}
$record | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $output -Encoding UTF8
Write-Host ('Recorded {0} exact stable releases and {1} version/loader targets in {2}' -f $releases.Count,$targets.Count,$output)
