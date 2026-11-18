@echo off
chcp 65001 >nul
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Build-APK.ps1"
if errorlevel 1 (
  echo.
  echo APK derlemesi basarisiz. Yukaridaki hata mesajini kontrol edin.
) else (
  echo.
  echo APK dosyasi app\build\outputs\apk\debug\app-debug.apk konumunda.
)
pause
