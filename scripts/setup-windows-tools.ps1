<#
.SYNOPSIS
Installs local Windows build dependencies for PNI (Go, Android SDK, NDK, Gradle)
and generates local.properties + Gradle Wrapper.
#>
$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot

Write-Host "=== PNI Windows Build Environment Setup ===" -ForegroundColor Cyan

# 1. Check winget availability
if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
    Write-Warning "winget not found. Please install Go and Android Studio / SDK manually."
} else {
    if (-not (Get-Command go -ErrorAction SilentlyContinue)) {
        Write-Host "Installing Go..."
        winget install --id GoLang.Go --accept-package-agreements --accept-source-agreements
    }
    if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
        Write-Host "Installing Gradle..."
        winget install --id Gradle.Gradle --accept-package-agreements --accept-source-agreements
    }
    if (-not (Test-Path "$env:LOCALAPPDATA\Android\Sdk")) {
        Write-Host "Installing Android Studio (or Command Line Tools)..."
        winget install --id Google.AndroidStudio --accept-package-agreements --accept-source-agreements
    }
}

$SdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$SdkManager = "$SdkPath\cmdline-tools\latest\bin\sdkmanager.bat"
if (Test-Path $SdkManager) {
    Write-Host "Installing Android SDK 35, Build-Tools 35.0.0, and NDK 27..."
    & $SdkManager "platforms;android-35" "build-tools;35.0.0" "ndk;27.2.12479018" "platform-tools"
} else {
    Write-Warning "sdkmanager.bat not found under $SdkPath. Open Android Studio SDK Manager to install API 35 + NDK."
}

$EscapedSdk = $SdkPath.Replace("\", "\\")
Set-Content -Path "$RootDir\local.properties" -Value "sdk.dir=$EscapedSdk" -Encoding ASCII
Write-Host "Wrote $RootDir\local.properties" -ForegroundColor Green

if (Get-Command gradle -ErrorAction SilentlyContinue) {
    Push-Location $RootDir
    gradle wrapper --gradle-version 8.11.1
    Pop-Location
}

Write-Host "`nSetup complete! Next steps (Git Bash / WSL):" -ForegroundColor Cyan
Write-Host "  1. bash scripts/fetch-assets.sh"
Write-Host "  2. bash scripts/build-cores.sh"
Write-Host "  3. bash scripts/build-binaries.sh"
Write-Host "  4. .\gradlew.bat assembleDebug"
