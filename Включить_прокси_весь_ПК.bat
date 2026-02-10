@echo off
chcp 65001 >nul
echo Включаю системный прокси для всего ПК...
echo Прокси: 127.0.0.1:3128 (V2Ray)
echo.
reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v ProxyEnable /t REG_DWORD /d 1 /f >nul
reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v ProxyServer /t REG_SZ /d "127.0.0.1:3128" /f >nul
echo Готово. Весь трафик Windows теперь идёт через V2Ray.
echo.
echo Важно: перед этим должен быть запущен run_v2ray.bat.
echo Некоторые программы переподхватят прокси после перезапуска.
echo.
pause
