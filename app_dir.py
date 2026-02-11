# -*- coding: utf-8 -*-
"""Базовая папка приложения: при запуске из exe — папка с exe, иначе — папка со скриптом."""
import sys
from pathlib import Path

if getattr(sys, "frozen", False):
    BASE_DIR = Path(sys.executable).resolve().parent
else:
    BASE_DIR = Path(__file__).resolve().parent
