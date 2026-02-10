# -*- coding: utf-8 -*-
"""
Клиент для обхода блокировок — подключение к прокси/VPN (SOCKS5/HTTP).
"""
import json
import threading
import tkinter as tk
from pathlib import Path

import customtkinter as ctk
from proxy_manager import (
    LOCAL_PROXY_PORT,
    ProxyProcess,
    build_remote_url,
    clear_system_proxy,
    get_system_proxy_status,
    set_system_proxy,
)

PROFILES_FILE = Path(__file__).parent / "profiles.json"
ctk.set_appearance_mode("dark")
ctk.set_default_color_theme("blue")


class VPNClientApp(ctk.CTk):
    def __init__(self):
        super().__init__()
        self.title("Обход блокировок — Прокси-клиент")
        self.geometry("480x520")
        self.minsize(400, 420)

        self.proxy_process = ProxyProcess(local_port=LOCAL_PROXY_PORT)
        self.connected = False
        self.profiles = self._load_profiles()

        self._build_ui()

    def _load_profiles(self):
        if PROFILES_FILE.exists():
            try:
                with open(PROFILES_FILE, "r", encoding="utf-8") as f:
                    return json.load(f)
            except Exception:
                pass
        return []

    def _save_profiles(self):
        try:
            with open(PROFILES_FILE, "w", encoding="utf-8") as f:
                json.dump(self.profiles, f, ensure_ascii=False, indent=2)
        except Exception:
            pass

    def _build_ui(self):
        # Заголовок
        title = ctk.CTkLabel(
            self,
            text="Подключение к прокси",
            font=ctk.CTkFont(size=22, weight="bold"),
        )
        title.pack(pady=(20, 8))

        subtitle = ctk.CTkLabel(
            self,
            text="SOCKS5 или HTTP — трафик пойдёт через выбранный сервер",
            font=ctk.CTkFont(size=12),
            text_color="gray",
        )
        subtitle.pack(pady=(0, 20))

        frame = ctk.CTkFrame(self, fg_color="transparent")
        frame.pack(fill="x", padx=24, pady=0)

        # Тип прокси
        ctk.CTkLabel(frame, text="Тип", font=ctk.CTkFont(weight="bold")).pack(anchor="w")
        self.protocol_var = ctk.StringVar(value="socks5")
        proto_frame = ctk.CTkFrame(frame, fg_color="transparent")
        proto_frame.pack(fill="x", pady=(4, 12))
        ctk.CTkRadioButton(
            proto_frame, text="SOCKS5", variable=self.protocol_var, value="socks5"
        ).pack(side="left", padx=(0, 16))
        ctk.CTkRadioButton(
            proto_frame, text="HTTP", variable=self.protocol_var, value="http"
        ).pack(side="left")

        # Хост
        ctk.CTkLabel(frame, text="Сервер (хост)", font=ctk.CTkFont(weight="bold")).pack(anchor="w")
        self.host_entry = ctk.CTkEntry(frame, placeholder_text="например proxy.example.com или 10.0.0.1")
        self.host_entry.pack(fill="x", pady=(4, 12))

        # Порт
        ctk.CTkLabel(frame, text="Порт", font=ctk.CTkFont(weight="bold")).pack(anchor="w")
        self.port_entry = ctk.CTkEntry(frame, placeholder_text="1080")
        self.port_entry.pack(fill="x", pady=(4, 12))

        # Логин (опционально)
        ctk.CTkLabel(frame, text="Логин (необязательно)", font=ctk.CTkFont(weight="bold")).pack(anchor="w")
        self.user_entry = ctk.CTkEntry(frame, placeholder_text="")
        self.user_entry.pack(fill="x", pady=(4, 12))

        # Пароль (опционально)
        ctk.CTkLabel(frame, text="Пароль (необязательно)", font=ctk.CTkFont(weight="bold")).pack(anchor="w")
        self.password_entry = ctk.CTkEntry(frame, placeholder_text="", show="•")
        self.password_entry.pack(fill="x", pady=(4, 16))

        # Профили
        prof_frame = ctk.CTkFrame(frame, fg_color="transparent")
        prof_frame.pack(fill="x", pady=(0, 8))
        ctk.CTkLabel(prof_frame, text="Профиль", font=ctk.CTkFont(weight="bold")).pack(anchor="w")
        self.profile_var = ctk.StringVar(value="")
        self.profile_combo = ctk.CTkComboBox(
            prof_frame,
            values=[""] + [p.get("name", "Без имени") for p in self.profiles],
            variable=self.profile_var,
            command=self._on_profile_select,
        )
        self.profile_combo.pack(fill="x", pady=(4, 4))
        btn_row = ctk.CTkFrame(prof_frame, fg_color="transparent")
        btn_row.pack(fill="x", pady=(0, 16))
        ctk.CTkButton(btn_row, text="Сохранить профиль", width=140, command=self._save_profile).pack(side="left", padx=(0, 8))
        ctk.CTkButton(btn_row, text="Удалить", width=80, fg_color="gray", command=self._delete_profile).pack(side="left")

        # Кнопки подключения
        btn_frame = ctk.CTkFrame(self, fg_color="transparent")
        btn_frame.pack(fill="x", padx=24, pady=12)
        self.connect_btn = ctk.CTkButton(
            btn_frame,
            text="Подключиться",
            height=44,
            font=ctk.CTkFont(size=16, weight="bold"),
            command=self._toggle_connection,
        )
        self.connect_btn.pack(fill="x", pady=4)

        # Статус
        self.status_label = ctk.CTkLabel(
            self,
            text="Отключено",
            font=ctk.CTkFont(size=13),
            text_color="gray",
        )
        self.status_label.pack(pady=12)

        self._update_status_from_system()

    def _on_profile_select(self, choice):
        if not choice:
            return
        for p in self.profiles:
            if p.get("name") == choice:
                self.protocol_var.set(p.get("protocol", "socks5"))
                self.host_entry.delete(0, tk.END)
                self.host_entry.insert(0, p.get("host", ""))
                self.port_entry.delete(0, tk.END)
                self.port_entry.insert(0, str(p.get("port", "")))
                self.user_entry.delete(0, tk.END)
                self.user_entry.insert(0, p.get("user", ""))
                self.password_entry.delete(0, tk.END)
                self.password_entry.insert(0, p.get("password", ""))
                break

    def _save_profile(self):
        name = ctk.CTkInputDialog(text="Название профиля:", title="Сохранить профиль").get_input()
        if not name:
            return
        host = self.host_entry.get().strip()
        port_str = self.port_entry.get().strip()
        if not host or not port_str:
            self.status_label.configure(text="Укажите хост и порт для сохранения")
            return
        try:
            port = int(port_str)
        except ValueError:
            self.status_label.configure(text="Порт должен быть числом")
            return
        profile = {
            "name": name,
            "protocol": self.protocol_var.get(),
            "host": host,
            "port": port,
            "user": self.user_entry.get().strip(),
            "password": self.password_entry.get().strip(),
        }
        self.profiles = [p for p in self.profiles if p.get("name") != name]
        self.profiles.append(profile)
        self._save_profiles()
        self.profile_combo.configure(values=[""] + [p.get("name", "") for p in self.profiles])
        self.profile_var.set(name)
        self.status_label.configure(text=f"Профиль «{name}» сохранён")

    def _delete_profile(self):
        name = self.profile_var.get()
        if not name:
            return
        self.profiles = [p for p in self.profiles if p.get("name") != name]
        self._save_profiles()
        self.profile_combo.configure(values=[""] + [p.get("name", "") for p in self.profiles])
        self.profile_var.set("")
        self.status_label.configure(text="Профиль удалён")

    def _get_remote_url(self):
        host = self.host_entry.get().strip()
        port_str = self.port_entry.get().strip()
        try:
            port = int(port_str) if port_str else 1080
        except ValueError:
            port = 1080
        user = self.user_entry.get().strip()
        password = self.password_entry.get().strip()
        protocol = self.protocol_var.get()
        return build_remote_url(protocol, host, port, user, password)

    def _toggle_connection(self):
        if self.connected:
            self._disconnect()
        else:
            self._connect()

    def _connect(self):
        host = self.host_entry.get().strip()
        port_str = self.port_entry.get().strip()
        if not host:
            self.status_label.configure(text="Введите адрес сервера")
            return
        try:
            port = int(port_str) if port_str else (1080 if self.protocol_var.get() == "socks5" else 8080)
        except ValueError:
            self.status_label.configure(text="Укажите корректный порт")
            return

        self.connect_btn.configure(state="disabled", text="Подключение...")
        self.status_label.configure(text="Запуск прокси...")

        def do_connect():
            remote_url = self._get_remote_url()
            ok = self.proxy_process.start(remote_url)
            self.after(0, lambda: self._on_connect_done(ok))

        threading.Thread(target=do_connect, daemon=True).start()

    def _on_connect_done(self, process_ok: bool):
        if not process_ok:
            self.connect_btn.configure(state="normal", text="Подключиться")
            self.status_label.configure(text="Ошибка запуска прокси. Проверьте pproxy: pip install pproxy")
            return
        set_ok = set_system_proxy(LOCAL_PROXY_HOST, LOCAL_PROXY_PORT)
        if set_ok:
            self.connected = True
            self.connect_btn.configure(state="normal", text="Отключиться", fg_color="#c0392b")
            self.status_label.configure(text="Подключено — трафик идёт через прокси")
        else:
            self.proxy_process.stop()
            self.connect_btn.configure(state="normal", text="Подключиться")
            self.status_label.configure(text="Ошибка настройки системного прокси")

    def _disconnect(self):
        clear_system_proxy()
        self.proxy_process.stop()
        self.connected = False
        self.connect_btn.configure(text="Подключиться", fg_color=["#3B8ED0", "#1F6AA5"])
        self.status_label.configure(text="Отключено")

    def _update_status_from_system(self):
        enabled, server = get_system_proxy_status()
        if enabled and server and server.startswith(f"{LOCAL_PROXY_HOST}:{LOCAL_PROXY_PORT}"):
            if self.proxy_process.is_running():
                self.connected = True
                self.connect_btn.configure(text="Отключиться", fg_color="#c0392b")
                self.status_label.configure(text="Подключено — трафик идёт через прокси")
            else:
                clear_system_proxy()

    def on_closing(self):
        if self.connected:
            self._disconnect()
        self.destroy()


def main():
    app = VPNClientApp()
    app.protocol("WM_DELETE_WINDOW", app.on_closing)
    app.mainloop()


if __name__ == "__main__":
    main()
