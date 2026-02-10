@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist "v2ray" mkdir v2ray
echo Downloading V2Ray Windows 64 from v2fly/v2ray-core ...
python -c "
import urllib.request, zipfile
from pathlib import Path
url = 'https://github.com/v2fly/v2ray-core/releases/download/v5.44.1/v2ray-windows-64.zip'
path = Path('v2ray/v2ray-windows-64.zip')
Path('v2ray').mkdir(exist_ok=True)
urllib.request.urlretrieve(url, path)
with zipfile.ZipFile(path, 'r') as z:
    z.extractall('v2ray')
print('Done. V2Ray in folder: v2ray')
"
pause
