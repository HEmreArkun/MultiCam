$ErrorActionPreference = 'Stop'
$exe = Join-Path $PSScriptRoot 'mediamtx.exe'
$config = Join-Path $PSScriptRoot 'mediamtx.yml'

if (-not (Test-Path $exe)) {
    Write-Host 'mediamtx.exe bulunamadi.' -ForegroundColor Red
    Write-Host 'MediaMTX Windows paketini indirip mediamtx.exe dosyasini bu klasore koy.'
    Write-Host 'Indirme: https://github.com/bluenviron/mediamtx/releases'
    Read-Host 'Cikmak icin Enter'
    exit 1
}

if (-not (Test-Path $config)) {
    throw "Ayar dosyasi bulunamadi: $config"
}

Write-Host 'MultiCam alicisi baslatiliyor. RTMP portu: TCP 1935' -ForegroundColor Cyan
Write-Host 'Durdurmak icin Ctrl+C.'
& $exe $config
