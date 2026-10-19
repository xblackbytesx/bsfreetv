# Credits

## Wallpapers

`app/src/main/assets/wallpapers/` holds the 40 paintings from
[Impasto](https://github.com/andreumassanet/impasto), copyright 2026 Andreu
Massanet, released under the GNU GPL-3.0. They were re-encoded for the TV
(centre-cropped to 16:9, 2752x1548 WebP) by `tools/import_wallpapers.py`; the
content is unchanged. The source images carry C2PA credentials marking them
as generated with Google tools.

## Colour extraction

The wallpaper accent picker and contrast lifting follow Impasto's
`theme_manager.py` (`pick_accent`, `build_palette`, `lift`), reimplemented in
Kotlin in `app/src/main/java/com/bsfreetv/launcher/domain/palette/`.
