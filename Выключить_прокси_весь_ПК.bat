@echo off
chcp 65001 >nul
echo Выключаю системный прокси...
reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v ProxyEnable /t REG_DWORD /d 0 /f >nul
echo Готово. Трафик снова идёт напрямую.
pause
