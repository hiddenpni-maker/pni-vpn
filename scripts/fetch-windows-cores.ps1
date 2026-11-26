$ErrorActionPreference = 'Stop'

$RootDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$OutDir = Join-Path $RootDir "desktop\cores\windows-x64"
$AssetsOut = Join-Path $OutDir "assets"
$TmpDir = Join-Path $env:TEMP "pnifox_win_cores"

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
New-Item -ItemType Directory -Force -Path $AssetsOut | Out-Null
New-Item -ItemType Directory -Force -Path $TmpDir | Out-Null

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Download-IfMissing([string]$Url, [string]$Dest) {
    if (-not (Test-Path $Dest)) {
        Write-Host "Downloading $Url..."
        Invoke-WebRequest -Uri $Url -OutFile $Dest -UseBasicParsing
    }
}

Write-Host "[1/6] Fetching patterniha/Aether Windows x86_64 (aether.exe, psiphon-tunnel-core.exe, lyrebird.exe)..."
$aetherTag = "v2.4.0"
try {
    $rel = Invoke-RestMethod -Uri 'https://api.github.com/repos/patterniha/Aether/releases/latest' -Headers @{ 'User-Agent' = 'pnifox-build' }
    if ($rel.tag_name) { $aetherTag = $rel.tag_name }
} catch {}
$aetherZip = Join-Path $TmpDir "aether-windows-$aetherTag.zip"
Download-IfMissing "https://github.com/patterniha/Aether/releases/download/$aetherTag/aether-windows-x86_64.zip" $aetherZip
$aetherExt = Join-Path $TmpDir "aether_ext"
Expand-Archive -Path $aetherZip -DestinationPath $aetherExt -Force
Copy-Item (Join-Path $aetherExt "aether.exe") (Join-Path $OutDir "aether.exe") -Force
if (Test-Path (Join-Path $aetherExt "pt\psiphon-tunnel-core.exe")) {
    Copy-Item (Join-Path $aetherExt "pt\psiphon-tunnel-core.exe") (Join-Path $OutDir "psiphon-tunnel-core.exe") -Force
}
if (Test-Path (Join-Path $aetherExt "pt\lyrebird.exe")) {
    Copy-Item (Join-Path $aetherExt "pt\lyrebird.exe") (Join-Path $OutDir "lyrebird.exe") -Force
}

Write-Host "[2/6] Fetching Tor Project Windows Expert Bundle (tor.exe)..."
$torVer = "16.0.3"
try {
    $idx = (Invoke-WebRequest -Uri "https://dist.torproject.org/torbrowser/" -UseBasicParsing -TimeoutSec 15).Content
    $vers = [regex]::Matches($idx, 'href="([0-9]+\.[0-9]+\.[0-9]+)/"') | ForEach-Object { $_.Groups[1].Value }
    if ($vers.Count -gt 0) { $torVer = ($vers | Sort-Object { [version]$_ } | Select-Object -Last 1) }
} catch {}
$torTar = Join-Path $TmpDir "tor-windows-$torVer.tar.gz"
Download-IfMissing "https://dist.torproject.org/torbrowser/$torVer/tor-expert-bundle-windows-x86_64-$torVer.tar.gz" $torTar
$torExt = Join-Path $TmpDir "tor_ext"
New-Item -ItemType Directory -Force -Path $torExt | Out-Null
& tar -xzf $torTar -C $torExt
Copy-Item (Join-Path $torExt "tor\tor.exe") (Join-Path $OutDir "tor.exe") -Force
if (Test-Path (Join-Path $torExt "tor\pluggable_transports\lyrebird.exe")) {
    Copy-Item (Join-Path $torExt "tor\pluggable_transports\lyrebird.exe") (Join-Path $OutDir "lyrebird.exe") -Force
}

Write-Host "[3/6] Fetching XTLS/Xray-core Windows 64-bit (xray.exe)..."
$xrayUrl = "https://github.com/XTLS/Xray-core/releases/download/v26.11.9/Xray-windows-64.zip"
try {
    $xRel = Invoke-RestMethod -Uri 'https://api.github.com/repos/XTLS/Xray-core/releases/latest' -Headers @{ 'User-Agent' = 'pnifox-build' }
    $xAsset = $xRel.assets | Where-Object { $_.name -eq "Xray-windows-64.zip" } | Select-Object -First 1
    if ($xAsset) { $xrayUrl = $xAsset.browser_download_url }
} catch {}
$xrayZip = Join-Path $TmpDir "xray-windows.zip"
Download-IfMissing $xrayUrl $xrayZip
$xrayExt = Join-Path $TmpDir "xray_ext"
Expand-Archive -Path $xrayZip -DestinationPath $xrayExt -Force
Copy-Item (Join-Path $xrayExt "xray.exe") (Join-Path $OutDir "xray.exe") -Force

Write-Host "[4/6] Fetching SagerNet/sing-box Windows amd64 + Wintun (sing-box.exe, wintun.dll)..."
$sbUrl = "https://github.com/SagerNet/sing-box/releases/download/v1.14.4/sing-box-1.14.4-windows-amd64.zip"
try {
    $sbRel = Invoke-RestMethod -Uri 'https://api.github.com/repos/SagerNet/sing-box/releases/latest' -Headers @{ 'User-Agent' = 'pnifox-build' }
    $sbAsset = $sbRel.assets | Where-Object { $_.name -match "windows-amd64\.zip$" } | Select-Object -First 1
    if ($sbAsset) { $sbUrl = $sbAsset.browser_download_url }
} catch {}
$sbZip = Join-Path $TmpDir "singbox-windows.zip"
Download-IfMissing $sbUrl $sbZip
$sbExt = Join-Path $TmpDir "sb_ext"
Expand-Archive -Path $sbZip -DestinationPath $sbExt -Force
$sbExe = Get-ChildItem $sbExt -Recurse -Filter "sing-box.exe" | Select-Object -First 1
Copy-Item $sbExe.FullName (Join-Path $OutDir "sing-box.exe") -Force

$wintunZip = Join-Path $TmpDir "wintun.zip"
Download-IfMissing "https://www.wintun.net/builds/wintun-0.14.1.zip" $wintunZip
$wintunExt = Join-Path $TmpDir "wintun_ext"
Expand-Archive -Path $wintunZip -DestinationPath $wintunExt -Force
Copy-Item (Join-Path $wintunExt "wintun\bin\amd64\wintun.dll") (Join-Path $OutDir "wintun.dll") -Force

Write-Host "[5/6] Fetching MasterHttpRelayVPN-RUST (mhrv-rs.exe)..."
$mhrTag = "v1.9.38"
try {
    $mRel = Invoke-RestMethod -Uri 'https://api.github.com/repos/therealaleph/MasterHttpRelayVPN-RUST/releases/latest' -Headers @{ 'User-Agent' = 'pnifox-build' }
    if ($mRel.tag_name) { $mhrTag = $mRel.tag_name }
} catch {}
$mhrZip = Join-Path $TmpDir "mhrv-windows-$mhrTag.zip"
Download-IfMissing "https://github.com/therealaleph/MasterHttpRelayVPN-RUST/releases/download/$mhrTag/mhrv-rs-windows-amd64.zip" $mhrZip
$mhrExt = Join-Path $TmpDir "mhrv_ext"
Expand-Archive -Path $mhrZip -DestinationPath $mhrExt -Force
Copy-Item (Join-Path $mhrExt "mhrv-rs.exe") (Join-Path $OutDir "mhrv-rs.exe") -Force

Write-Host "[6/6] Building dnstt-client.exe from Mygod/dnstt..."
$goBin = (Get-Command go -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue)
if (-not $goBin) {
    $goRoot = Join-Path $TmpDir "go"
    $goExe = Join-Path $goRoot "bin\go.exe"
    if (-not (Test-Path $goExe)) {
        $goRel = Invoke-RestMethod -Uri 'https://go.dev/dl/?mode=json' -Headers @{ 'User-Agent' = 'pnifox-build' }
        $goVer = $goRel[0].version
        $goZip = Join-Path $TmpDir "$goVer.windows-amd64.zip"
        Download-IfMissing "https://go.dev/dl/$goVer.windows-amd64.zip" $goZip
        Expand-Archive -Path $goZip -DestinationPath $TmpDir -Force
    }
    $goBin = $goExe
}
$dnsttDir = Join-Path $TmpDir "dnstt"
if (-not (Test-Path $dnsttDir)) {
    & git clone --depth=1 -b plugin https://github.com/Mygod/dnstt.git $dnsttDir
}
Push-Location (Join-Path $dnsttDir "dnstt-client")
$env:CGO_ENABLED = "0"
$env:GOOS = "windows"
$env:GOARCH = "amd64"
& $goBin build -trimpath -buildvcs=false -ldflags "-s -w -buildid=" -o (Join-Path $OutDir "dnstt-client.exe") .
Pop-Location

Write-Host "Copying shared geo, scanner, and MHR assets..."
$AppAssets = Join-Path $RootDir "app\src\main\assets"
Copy-Item (Join-Path $AppAssets "geo\*") $AssetsOut -Force -ErrorAction SilentlyContinue
Copy-Item (Join-Path $AppAssets "scanner\*") $AssetsOut -Force -ErrorAction SilentlyContinue
Copy-Item (Join-Path $AppAssets "mhr\*") $AssetsOut -Force -ErrorAction SilentlyContinue
Copy-Item (Join-Path $AppAssets "dns\*") $AssetsOut -Force -ErrorAction SilentlyContinue

# Ensure Iran GeoIP/GeoSite exist on clean CI checkouts where app/src/main/assets/geo/*.dat is gitignored
Download-IfMissing "https://github.com/Chocolate4U/Iran-v2ray-rules/releases/latest/download/geoip.dat" (Join-Path $AssetsOut "geoip.dat")
Download-IfMissing "https://github.com/Chocolate4U/Iran-v2ray-rules/releases/latest/download/geosite.dat" (Join-Path $AssetsOut "geosite.dat")

Write-Host "`nAll Windows x64 cores ready in $OutDir :"
Get-ChildItem $OutDir -File | Select-Object Name, Length | Format-Table -AutoSize
