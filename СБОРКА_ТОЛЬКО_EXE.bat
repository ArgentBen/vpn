@echo off
chcp 65001 >nul
cd /d "%~dp0"
set HTTP_PROXY=
set HTTPS_PROXY=

echo Запуск PyInstaller (окно не закроется — смотрите вывод)...
echo.
python -m PyInstaller --noconfirm --clean vpn_app.spec
echo.
echo Код выхода: %errorlevel%
pause
