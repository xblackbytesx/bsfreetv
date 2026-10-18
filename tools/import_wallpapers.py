#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Re-encode Impasto's wallpapers for the TV.

Usage: python3 tools/import_wallpapers.py <impasto-wallpapers-dir> [app/src/main/assets/wallpapers]

Impasto ships 2752x1536 JPEGs (about 1.79:1, 178 MB). The Shield composes its
UI at 1920x1080, so each image is centre-cropped to 16:9, scaled to 1920x1080
and stored as WebP, plus a 384x216 thumbnail for the picker. The output and
index.json are committed; rerun only when the source set changes. Needs Pillow.
"""
import json
import sys
from pathlib import Path

from PIL import Image

FULL = (1920, 1080)
THUMB = (384, 216)
CREDIT = "Andreu Massanet, Impasto (github.com/andreumassanet/impasto)"
LICENSE = "GPL-3.0"


def crop_16_9(img: Image.Image) -> Image.Image:
    w, h = img.size
    target = 16 / 9
    if w / h > target:
        nw = round(h * target)
        x = (w - nw) // 2
        return img.crop((x, 0, x + nw, h))
    nh = round(w / target)
    y = (h - nh) // 2
    return img.crop((0, y, w, y + nh))


def title(stem: str) -> str:
    return " ".join(part.capitalize() for part in stem.split("-"))


def main() -> None:
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    src = Path(sys.argv[1])
    out = Path(sys.argv[2] if len(sys.argv) > 2 else "app/src/main/assets/wallpapers")
    (out / "thumbs").mkdir(parents=True, exist_ok=True)
    sources = sorted(src.glob("*.jp*g"))
    if not sources:
        sys.exit(f"no JPEGs in {src}")
    entries = []
    for path in sources:
        img = crop_16_9(Image.open(path).convert("RGB"))
        img.resize(FULL, Image.LANCZOS).save(out / f"{path.stem}.webp", "WEBP", quality=86, method=6)
        img.resize(THUMB, Image.LANCZOS).save(out / "thumbs" / f"{path.stem}.webp", "WEBP", quality=80, method=6)
        entries.append({"id": path.stem, "title": title(path.stem), "credit": CREDIT, "license": LICENSE})
        print(path.stem)
    (out / "index.json").write_text(json.dumps({"wallpapers": entries}, indent=1) + "\n")
    print(f"{len(entries)} wallpapers -> {out}")


if __name__ == "__main__":
    main()
