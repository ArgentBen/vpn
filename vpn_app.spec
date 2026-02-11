# -*- mode: python ; coding: utf-8 -*-
# Сборка: pyinstaller vpn_app.spec
# Итоговая папка: dist/VPN_Obhod/ — скопируйте её целиком (exe + v2ray + все файлы).

from PyInstaller.utils.hooks import collect_data_files

block_cipher = None

ctk_datas = collect_data_files("customtkinter", include_py_files=False)

a = Analysis(
    ["main.py"],
    pathex=[],
    binaries=[],
    datas=ctk_datas,
    hiddenimports=["customtkinter", "config_builder", "proxy_manager", "v2ray_runner", "app_dir"],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

# Одна папка (onedir): exe + зависимости. Имя папки с _app, чтобы не удалять старую сборку.
exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="VPN_Obhod_app",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=False,
    upx_exclude=[],
    name="VPN_Obhod_app",
)
