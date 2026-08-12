from __future__ import annotations

import re


def pascal(value: str) -> str:
    parts = re.split(r"[-_\s]+", value)
    return "".join(part[:1].upper() + part[1:] for part in parts if part)


def camel(value: str) -> str:
    candidate = pascal(value)
    return candidate[:1].lower() + candidate[1:]


def java_path(package_name: str) -> str:
    return package_name.replace(".", "/")

