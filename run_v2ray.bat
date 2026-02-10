@echo off
chcp 65001 >nul
cd /d "%~dp0"

if not exist "v2ray\v2ray.exe" (
    echo V2Ray не найден. Положите v2ray.exe в папку v2ray.
    pause
    exit /b 1
)

echo Запуск V2Ray из папки v2ray...
echo Если окно сразу закроется — смотрите ошибку выше.
echo.
cd v2ray
v2ray.exe
echo.
echo V2Ray завершился. Код выхода: %errorlevel%
pause
