# -*- coding: utf-8 -*-
"""
Управление системным прокси в Windows и локальный прокси-сервер.
"""
import subprocess
import sys
import winreg
import urllib.parse

# Адрес локального прокси (Windows использует только HTTP-прокси)
LOCAL_PROXY_HOST = "127.0.0.1"
LOCAL_PROXY_PORT = 3128
REG_PATH = r"Software\Microsoft\Windows\CurrentVersion\Internet Settings"


def set_system_proxy(host: str, port: int) -> bool:
    """Включить системный прокси в Windows."""
    try:
        key = winreg.OpenKey(
            winreg.HKEY_CURRENT_USER,
            REG_PATH,
            0,
            winreg.KEY_SET_VALUE
        )
        winreg.SetValueEx(key, "ProxyEnable", 0, winreg.REG_DWORD, 1)
        winreg.SetValueEx(key, "ProxyServer", 0, winreg.REG_SZ, f"{host}:{port}")
        winreg.CloseKey(key)
        return True
    except OSError:
        return False


def clear_system_proxy() -> bool:
    """Отключить системный прокси."""
    try:
        key = winreg.OpenKey(
            winreg.HKEY_CURRENT_USER,
            REG_PATH,
            0,
            winreg.KEY_SET_VALUE
        )
        winreg.SetValueEx(key, "ProxyEnable", 0, winreg.REG_DWORD, 0)
        winreg.CloseKey(key)
        return True
    except OSError:
        return False


def get_system_proxy_status() -> tuple[bool, str]:
    """Вернуть (включен ли прокси, строка ProxyServer)."""
    try:
        key = winreg.OpenKey(
            winreg.HKEY_CURRENT_USER,
            REG_PATH,
            0,
            winreg.KEY_READ
        )
        enabled = winreg.QueryValueEx(key, "ProxyEnable")[0]
        server = ""
        try:
            server = winreg.QueryValueEx(key, "ProxyServer")[0] or ""
        except FileNotFoundError:
            pass
        winreg.CloseKey(key)
        return bool(enabled), server
    except OSError:
        return False, ""


def build_remote_url(protocol: str, host: str, port: int, user: str = "", password: str = "") -> str:
    """Собрать URL для pproxy: socks5://[user:pass@]host:port или http://..."""
    netloc = host
    if port:
        netloc = f"{host}:{port}"
    if user or password:
        auth = urllib.parse.quote(user, safe="") + ":" + urllib.parse.quote(password, safe="")
        netloc = f"{auth}@{netloc}"
    return f"{protocol}://{netloc}"


class ProxyProcess:
    """Запуск и остановка процесса pproxy (локальный HTTP -> удалённый SOCKS5/HTTP)."""
    def __init__(self, local_port: int = LOCAL_PROXY_PORT):
        self.local_port = local_port
        self._process = None

    def start(self, remote_url: str) -> bool:
        """remote_url: socks5://host:port или http://host:port, с опцией user:pass@."""
        if self._process and self._process.poll() is None:
            return True
        listen_arg = f"http://{LOCAL_PROXY_HOST}:{self.local_port}"
        cmd = [sys.executable, "-m", "pproxy", "-l", listen_arg, "-r", remote_url]
        try:
            self._process = subprocess.Popen(
                cmd,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.PIPE,
                creationflags=subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0,
            )
            return self._process.poll() is None
        except Exception:
            return False

    def stop(self) -> bool:
        if self._process and self._process.poll() is None:
            self._process.terminate()
            try:
                self._process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self._process.kill()
            self._process = None
            return True
        return False

    def is_running(self) -> bool:
        return self._process is not None and self._process.poll() is None
