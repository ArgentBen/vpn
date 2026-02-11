@echo off
chcp 65001 >nul
cd /d "%~dp0"

set HTTP_PROXY=
set HTTPS_PROXY=
set NO_PROXY=*

echo Установка зависимостей...
pip install --proxy="" -r requirements.txt
pip install --proxy="" pyinstaller
if errorlevel 1 (
    echo Ошибка установки. Отключите прокси в Windows и повторите.
    pause
    exit /b 1
)

echo.
echo Сборка приложения (PyInstaller)...
python -m PyInstaller --noconfirm --clean vpn_app.spec
if errorlevel 1 (
    echo.
    echo PyInstaller завершился с ошибкой. Скопируйте текст ошибки выше и проверьте.
    pause
    exit /b 1
)

if not exist "dist\VPN_Obhod_app\VPN_Obhod_app.exe" (
    echo.
    echo ОШИБКА: exe не создан.
    pause
    exit /b 1
)

echo Копирование v2ray и README...
xcopy /E /I /Y "v2ray" "dist\VPN_Obhod_app\v2ray"
copy /Y "ПРИЛОЖЕНИЕ_ПК_README.txt" "dist\VPN_Obhod_app\README.txt"

echo.
echo Готово. Приложение: dist\VPN_Obhod_app\VPN_Obhod_app.exe
echo.
start "" "dist\VPN_Obhod_app"
pause
