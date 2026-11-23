#!/usr/bin/env python3
"""Turn the latest GitHub release into the website's release.json.

Usage: make_release_json.py <gh-release.json> <output.json>

The input is the JSON produced by `gh release view --json tagName,name,body,
publishedAt,url,assets`. An empty object (or a missing file) means no release
exists yet, and the site then shows the "pending" state instead of inventing a
version.
"""
from __future__ import annotations

import json
import os
import re
import sys

MIN_ANDROID = "Android 10 (API 29)"
DEFAULT_ABIS = ["arm64-v8a", "armeabi-v7a"]


def load(path: str) -> dict:
    try:
        with open(path, "r", encoding="utf-8") as handle:
            data = json.load(handle)
        return data if isinstance(data, dict) else {}
    except Exception:
        return {}


def build(data: dict) -> dict:
    tag = (data.get("tagName") or "").strip()
    body = data.get("body") or ""
    assets = data.get("assets") or []

    version = tag.lstrip("v")
    apk = next(
        (asset for asset in assets if str(asset.get("name", "")).lower().endswith(".apk")),
        None,
    )

    if not version or apk is None:
        return {
            "published": False,
            "version": "",
            "versionCode": 0,
            "apkName": "",
            "apkSize": 0,
            "sha256": "",
            "minAndroid": MIN_ANDROID,
            "abis": DEFAULT_ABIS,
            "releaseDate": "",
            "releaseUrl": data.get("url") or "https://github.com/MPV999/vcam-pro-2026/releases",
            "downloadUrl": "",
            "signed": False,
            "notes": body,
        }

    sha_match = re.search(r"SHA-256:\s*([0-9a-fA-F]{64})", body)
    code_match = re.search(r"Version code:\s*(\d+)", body)
    version_code = 0
    if code_match:
        version_code = int(code_match.group(1))
    else:
        parts = [p for p in re.split(r"[.\-+]", version) if p.isdigit()]
        if parts:
            numbers = [int(p) for p in parts[:3]] + [0, 0, 0]
            version_code = numbers[0] * 10000 + numbers[1] * 100 + numbers[2]

    notes = re.sub(r"\n?SHA-256:\s*[0-9a-fA-F]{64}\s*", "\n", body)
    notes = re.sub(r"\n?Version code:\s*\d+\s*", "\n", notes)

    return {
        "published": True,
        "version": version,
        "versionCode": version_code,
        "apkName": apk.get("name", ""),
        "apkSize": apk.get("size", 0),
        "sha256": (sha_match.group(1).lower() if sha_match else ""),
        "minAndroid": MIN_ANDROID,
        "abis": DEFAULT_ABIS,
        "releaseDate": data.get("publishedAt") or "",
        "releaseUrl": data.get("url") or "https://github.com/MPV999/vcam-pro-2026/releases",
        "downloadUrl": apk.get("url") or "",
        "signed": bool(sha_match),
        "notes": notes.strip(),
    }


def main() -> int:
    source = sys.argv[1] if len(sys.argv) > 1 else ""
    target = sys.argv[2] if len(sys.argv) > 2 else "_site/release.json"
    data = load(source) if source else {}
    payload = build(data)
    os.makedirs(os.path.dirname(target) or ".", exist_ok=True)
    with open(target, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print("wrote", target, "published =", payload["published"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
