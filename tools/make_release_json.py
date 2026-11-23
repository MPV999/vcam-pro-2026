#!/usr/bin/env python3
"""Turn the latest GitHub release into the website's release.json fallback.

Usage: make_release_json.py <gh-release.json> <output.json>

The site reads the releases live from the GitHub API; this file is only a
fallback for the case where that request cannot be made. An empty object (or a
missing file) means no release exists yet, and the site then shows the pending
state instead of inventing a version.
"""
from __future__ import annotations

import json
import os
import re
import sys

MIN_ANDROID = "Android 10 (API 29)"
DEFAULT_ABIS = ["arm64-v8a", "armeabi-v7a"]
DEBUG_RE = re.compile(r"khoá debug|khoa debug|debug-signed|debug signing", re.IGNORECASE)


def load(path: str) -> dict:
    try:
        with open(path, "r", encoding="utf-8") as handle:
            data = json.load(handle)
        return data if isinstance(data, dict) else {}
    except Exception:
        return {}


def version_code(tag: str, body: str) -> int:
    match = re.search(r"Version\s*code\s*\|?\s*(\d+)", body, re.IGNORECASE)
    if match:
        value = int(match.group(1))
        if value > 0:
            return value
    parts = [p for p in re.split(r"[.\-+_]", tag.lstrip("v")) if p.isdigit()]
    numbers = [int(p) for p in parts[:3]] + [0, 0, 0]
    return numbers[0] * 10000 + numbers[1] * 100 + numbers[2]


def build(data: dict) -> dict:
    tag = (data.get("tagName") or "").strip()
    body = data.get("body") or ""
    assets = data.get("assets") or []

    version = tag.lstrip("v")
    apk = next(
        (asset for asset in assets if str(asset.get("name", "")).lower().endswith(".apk")),
        None,
    )
    sums = next(
        (asset for asset in assets if "sha256" in str(asset.get("name", "")).lower()),
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

    sha_match = re.search(r"SHA-?256:?\s*([0-9a-fA-F]{64})", body)

    return {
        "published": True,
        "version": version,
        "versionCode": version_code(version, body),
        "apkName": apk.get("name", ""),
        "apkSize": apk.get("size", 0),
        "sha256": (sha_match.group(1).lower() if sha_match else ""),
        "minAndroid": MIN_ANDROID,
        "abis": DEFAULT_ABIS,
        "releaseDate": data.get("publishedAt") or "",
        "releaseUrl": data.get("url") or "https://github.com/MPV999/vcam-pro-2026/releases",
        "downloadUrl": apk.get("url") or "",
        "sumsUrl": (sums.get("url") if sums else ""),
        "signed": not DEBUG_RE.search(body),
        "notes": body.strip(),
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
