# BSfreeTV

An ad-free launcher for Android TV, made for the NVIDIA SHIELD TV Pro.

- Your media apps up front as large banners; tools in a small tray (or hidden).
- 40 built-in paintings from [Impasto](https://github.com/andreumassanet/impasto);
  the accent colour follows the wallpaper.
- OLED care: true black, wallpaper-only after a few idle minutes, then black,
  slow pixel shift on everything static.
- Optional Next Up row from apps that publish Watch Next (Jellyfin does).
- Makes itself the Home app from its own settings (self-ADB), with one-click undo.

## Install

```
make build
adb connect <shield-ip>:5555
adb install -r build-output/bsfreetv-debug.apk
```

Open BSfreeTV from the app list, then **Settings > Default launcher**.

## Make it the Home app

1. Shield: Settings > Device Preferences > About, press Build 7 times.
2. Developer options: turn on **Network debugging**.
3. BSfreeTV: Settings > Default launcher > **Test connection**, choose Allow
   (tick Always allow) on the prompt, then **Make BSfreeTV Home**.
4. Turn Network debugging off again.

From a PC instead:

```
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell pm grant com.bsfreetv.launcher android.permission.READ_TV_LISTINGS
```

Undo (also the recovery if Home ever stops working):

```
adb shell pm enable com.google.android.tvlauncher
```

Restore the stock launcher before uninstalling BSfreeTV.

## Using it

- Hold OK (or Menu) on any app: Add to Media, Move to Tools, Reorder, Hide.
- Reorder: Left/Right moves the app, OK finishes.
- Your own wallpapers: put images in
  `/sdcard/Android/data/com.bsfreetv.launcher/files/wallpapers/`
  (`adb push`), or use Add image when the device has a file picker.

## License

GPL-3.0. Wallpapers by Andreu Massanet (Impasto), GPL-3.0; see `CREDITS.md`.
