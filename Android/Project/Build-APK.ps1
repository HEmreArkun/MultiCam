$ErrorActionPreference = 'Stop'
$GradleVersion = '8.13'
$InstallRoot = Join-Path $env:LOCALAPPDATA 'MultiCamBuildTools'
$GradleHome = Join-Path $InstallRoot "gradle-$GradleVersion"
$GradleExe = Join-Path $GradleHome 'bin\gradle.bat'

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'Java bulunamadi. Android Studio kurup onun JDK 17+ ortaminda bu betigi calistir.'
}

if (-not (Test-Path $GradleExe)) {
    New-Item -ItemType Directory -Force -Path $InstallRoot | Out-Null
    $zip = Join-Path $InstallRoot "gradle-$GradleVersion-bin.zip"
    $url = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"
    Write-Host "Gradle $GradleVersion indiriliyor..." -ForegroundColor Cyan
    Invoke-WebRequest -Uri $url -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $InstallRoot -Force
}

$sdk = $env:ANDROID_HOME
if ([string]::IsNullOrWhiteSpace($sdk)) { $sdk = $env:ANDROID_SDK_ROOT }
if ([string]::IsNullOrWhiteSpace($sdk)) {
    $commonSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path $commonSdk) { $sdk = $commonSdk }
}
if ([string]::IsNullOrWhiteSpace($sdk) -or -not (Test-Path $sdk)) {
    throw 'Android SDK bulunamadi. Android Studio > SDK Manager uzerinden Android SDK Platform 36 ve Android SDK Build-Tools paketlerini kur; ANDROID_HOME ortam degiskenini ayarla.'
}

$platformJar = Join-Path $sdk 'platforms\android-36\android.jar'
if (-not (Test-Path $platformJar)) {
    throw "Android SDK Platform 36 bulunamadi: $platformJar. Android Studio > Tools > SDK Manager > SDK Platforms bolumunden Android 16 / API 36 paketini kur."
}
$buildToolsRoot = Join-Path $sdk 'build-tools'
$buildTools = Get-ChildItem -Path $buildToolsRoot -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^35\.' -or $_.Name -match '^36\.' } | Select-Object -First 1
if (-not $buildTools) {
    throw "Android SDK Build-Tools 35.0.0 veya uyumlu bir 36.x surumu bulunamadi: $buildToolsRoot"
}
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk

Set-Location $PSScriptRoot
Write-Host 'APK derleniyor...' -ForegroundColor Cyan
& $GradleExe --no-daemon assembleDebug
if ($LASTEXITCODE -ne 0) { throw "Gradle derlemesi basarisiz oldu (kod $LASTEXITCODE)." }

$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
Write-Host "APK hazir: $apk" -ForegroundColor Green
