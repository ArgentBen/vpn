# -*- coding: utf-8 -*-
"""
Запуск и остановка V2Ray на ПК (v2ray.exe с config.json).
"""
import subprocess
import sys
from pathlib import Path

from app_dir import BASE_DIR

# Папка v2ray рядом с exe или со скриптом
V2RAY_DIR = BASE_DIR / "v2ray"
CONFIG_PATH = V2RAY_DIR / "config.json"
V2RAY_EXE = V2RAY_DIR / "v2ray.exe"

_process = None


def start(config_json: str) -> bool:
    """Записать config_json в config.json и запустить v2ray.exe. Вернуть True при успехе."""
    global _process
    if _process is not None and _process.poll() is None:
        return True
    if not V2RAY_EXE.exists():
        return False
    try:
        CONFIG_PATH.write_text(config_json, encoding="utf-8")
    except Exception:
        return False
    try:
        _process = subprocess.Popen(
            [str(V2RAY_EXE), "-config", str(CONFIG_PATH)],
            cwd=str(V2RAY_DIR),
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
            creationflags=subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0,
        )
        return _process.poll() is None
    except Exception:
        return False


def stop() -> bool:
    """Остановить v2ray.exe."""
    global _process
    if _process is None:
        return True
    if _process.poll() is not None:
        _process = None
        return True
    try:
        _process.terminate()
        _process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        _process.kill()
    except Exception:
        pass
    _process = None
    return True


def is_running() -> bool:
    return _process is not None and _process.poll() is None
