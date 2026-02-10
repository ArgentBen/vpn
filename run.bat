@echo off
chcp 65001 >nul
cd /d "%~dp0"
python -c "import customtkinter, pproxy" 2>nul || (echo Устанавливаю зависимости... && pip install -r requirements.txt)
python main.py
pause
