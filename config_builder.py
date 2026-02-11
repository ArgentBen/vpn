# -*- coding: utf-8 -*-
"""
Парсит ss:// ссылку и собирает JSON-конфиг для V2Ray/Xray на ПК.
Формат: ss://base64(method:password)@host:port#tag
"""
import base64
import json


def build_from_ss_link(ss_link: str) -> str | None:
    """Из ss:// ссылки собрать JSON-конфиг для V2Ray (с SOCKS и HTTP inbound для ПК)."""
    link = ss_link.strip().removeprefix("ss://")
    hash_idx = link.find("#")
    main = link[:hash_idx] if hash_idx >= 0 else link
    at_idx = main.find("@")
    if at_idx <= 0:
        return None
    user_info = main[:at_idx]
    host_port = main[at_idx + 1 :]
    colon = host_port.rfind(":")
    if colon <= 0:
        return None
    host = host_port[:colon]
    port_str = host_port[colon + 1 :].split("/")[0].split("?")[0]
    try:
        port = int(port_str)
    except ValueError:
        return None

    try:
        # URL-safe base64: - и _ заменяют + и /
        pad = user_info.replace("-", "+").replace("_", "/")
        pad += "=" * (4 - len(pad) % 4) if len(pad) % 4 else ""
        decoded = base64.b64decode(pad).decode("utf-8", errors="replace")
    except Exception:
        return None

    parts = decoded.split(":", 2)
    if len(parts) != 2:
        return None
    method = parts[0].strip()
    password = parts[1].strip()

    return _build_config(host, port, method, password)


def _build_config(host: str, port: int, method: str, password: str) -> str:
    """Конфиг с SOCKS 1081 и HTTP 3128 (для системного прокси ПК)."""
    config = {
        "log": {"loglevel": "warning"},
        "inbounds": [
            {
                "listen": "127.0.0.1",
                "port": 1081,
                "protocol": "socks",
                "tag": "socks-in",
                "settings": {"auth": "noauth", "udp": False},
            },
            {
                "listen": "127.0.0.1",
                "port": 3128,
                "protocol": "http",
                "tag": "http-in",
                "settings": {},
            },
        ],
        "outbounds": [
            {
                "protocol": "shadowsocks",
                "settings": {
                    "servers": [
                        {
                            "address": host,
                            "port": port,
                            "method": method,
                            "password": password,
                        }
                    ]
                },
                "tag": "proxy",
            },
            {"protocol": "freedom", "settings": {}, "tag": "direct"},
            {"protocol": "blackhole", "settings": {}, "tag": "block"},
        ],
        "routing": {
            "domainStrategy": "IPOnDemand",
            "rules": [
                {"type": "field", "ip": ["geoip:private"], "outboundTag": "block"},
                {"type": "field", "network": "tcp,udp", "outboundTag": "proxy"},
            ],
        },
    }
    return json.dumps(config, ensure_ascii=False, indent=2)
