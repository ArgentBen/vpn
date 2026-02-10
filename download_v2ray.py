# -*- coding: utf-8 -*-
"""Скачивание v2ray-core для Windows с GitHub (v2fly)."""
import os
import urllib.request
import zipfile
from pathlib import Path

RELEASE = "v5.44.1"
URL = f"https://github.com/v2fly/v2ray-core/releases/download/{RELEASE}/v2ray-windows-64.zip"
BASE = Path(__file__).resolve().parent
OUT_DIR = BASE / "v2ray"
ZIP_PATH = OUT_DIR / "v2ray-windows-64.zip"

def main():
    OUT_DIR.mkdir(exist_ok=True)
    print("Downloading v2ray-windows-64.zip ...")
    urllib.request.urlretrieve(URL, ZIP_PATH)
    print("Extracting ...")
    with zipfile.ZipFile(ZIP_PATH, "r") as z:
        z.extractall(OUT_DIR)
    print("Done. V2Ray is in:", OUT_DIR.resolve())

if __name__ == "__main__":
    main()
