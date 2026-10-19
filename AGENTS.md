# BSfreeTV: agent guide and invariant register

An ad-free Android TV launcher for the NVIDIA SHIELD TV Pro. Media apps are the
hero row, tools sit in a quiet tray, Impasto's wallpapers ship built in and the
UI takes its accent from the wallpaper. OLED-aware: true black base, idle fade
to black, pixel shift. GPL-3.0.

## Build and verify

Docker only (`make`), no host toolchain:

```
make test          # JVM unit tests + Robolectric screenshots
make build         # debug APK -> build-output/bsfreetv-debug.apk
make release       # R8-shrunk release APK (signed when keystore.properties / RELEASE_* exist)
make lint
make screenshots   # every screen at 1920x1080 (+ one 4K home) -> build-output/screenshots/
make install       # adb install the debug APK on SHIELD=host:5555
make keystore      # once: release key + GitHub secrets
```

Baseline Profile: `make baseline-profile SHIELD=<ip>:5555` (Network debugging
on, accept the prompt). It runs `:app:generateBaselineProfile`, which drives
`:baselineprofile` `HomeProfileGenerator` on the Shield; commit
`app/src/main/generated/baselineProfiles/`. `profileinstaller` applies it.

CI: `.github/workflows/ci.yml` on every branch push and PR (compose check,
tests incl. screenshots as an artifact, lint, debug APK). Releases:
`.github/workflows/release.yml` on a `vX.Y.Z` tag: tests gate, then a signed
`assembleRelease` published as a GitHub release (Obtainium). Tag runs refuse
to publish unsigned.
versionCode = X*1000000 + Y*1000 + Z: never reuse it, never change the key.

Agent sandbox: scratchpad toolchain mirroring the image (Temurin 21 at
`/opt/java`, cmdline-tools 16111833, android-37.0, build-tools 37.0.0, Gradle
9.8.0) with `--project-cache-dir` and `GRADLE_USER_HOME` outside the repo.
Afterwards remove `.gradle app/.gradle build app/build baselineprofile/build
.kotlin build-output local.properties`: the container bind-mounts the repo.

## Architecture

Single app module `com.bsfreetv.launcher` (+ `:baselineprofile`). Hilt,
DataStore, Compose foundation + Material 3 (no tv-material), Coil (Next Up
posters only), dadb (self-ADB).

| Path | Role |
|---|---|
| `domain/palette/` | Pure Kotlin Impasto port: median-cut quantise, `pickAccent`, `fromAccent`, WCAG `lift` |
| `domain/layout/` | `LauncherLayout` (media/tools/hidden lists, known, fresh), `reconcile`, `SeedRules` |
| `domain/idle/` | `IdlePolicy` (active, ambient, black), `PixelShift` orbit |
| `domain/home/` | Stock launcher ids, `HomeStatus`, `HomeCommands` (exact adb strings, tested) |
| `domain/LauncherSettings.kt` | Every setting, one JSON document |
| `data/AppCatalog.kt` | Leanback + launcher activities, banners/icons rasterised once off the main thread |
| `data/Wallpapers.kt` | Built-in assets + `Android/data/<pkg>/files/wallpapers/`, decode, palette cache, import |
| `data/WatchNext.kt` | TvProvider Watch Next reader (READ_TV_LISTINGS) |
| `data/LauncherStore.kt` | DataStore: layout + settings JSON, decode failure = defaults |
| `system/HomeControl.kt` | Home status and self-ADB (dadb to 127.0.0.1:5555) |
| `feature/home/` | `HomeViewModel` (implements `HomeActions`), stateless `HomeContent`, `LauncherRoot` |
| `feature/apps/`, `feature/settings/`, `feature/wallpaper/` | Overlays: All apps + app menu, settings panel, wallpaper strip |
| `ui/` | `BsfTheme` (animated palette), type scale, `tvClickable`, focus halo, panels |
| `ui/Labels.kt` | The only place domain values get user-facing names (string resources) |
| `i18n/Languages.kt` | `SupportedLanguages` (picker order) and `LanguageStore` (in-app override, wraps the activity context) |
| `tools/import_wallpapers.py` | Impasto JPEG -> 2752x1548 WebP (q82, full source width) + 384x216 thumbs + `index.json` |

## Invariants

- **Target is the Shield: Android 11, API 30.** minSdk 30; nothing may need
  API 31+ (no `RenderEffect` blur).
- **The launcher must always start.** It may be the only Home on the device.
  Bad stored JSON decodes to defaults, a missing wallpaper falls back to the
  default then black, app scan failures keep the last list. Never crash-loop.
- **True black base.** Window, scrim ends, idle curtain and panels are black or
  near-black; the wallpaper only tints accent and surfaces. Text is off-white
  (`#EEF2F7`), never pure white.
- **Animate draw-phase only.** Focus scale, ring, halo, chrome fade, pixel
  shift and drift read animated values inside `graphicsLayer`/draw lambdas.
  Never animate size, padding or anything that re-measures a row.
- **Artwork is prerasterised.** Rows draw `ImageBitmap`s from `AppCatalog`;
  never load a Drawable or touch PackageManager while composing.
- **Wallpaper decode is screen-sized and HARDWARE.** At most two bitmaps alive
  (during the cross-fade). Screen size comes from the display, capped at 4K and
  at the image's own size.
  `WallpapersTest` runs the real asset decode path; screenshot tests bypass it.
- **Seeds apply once per package** (`known`). A user's move always sticks.
  Uninstalled apps keep their slot so reinstalls return to place.
- **Focus.** Overlays trap focus (`trapFocus`); closing one restores the last
  focused home item; Home press returns to the first media app. A key release
  only clicks if the same element saw the press (long-press opens menus).
  An overlay that opens a menu over itself releases its trap meanwhile
  (`trapFocus(enabled = !menuOpen)`): the trap also cancels `requestFocus()`
  from outside (`AppMenuFocusTest`). `tvClickable` also takes pointer taps and
  holds (air mice).
- **No unreachable information.** TV panels scroll only by moving focus, so
  every block of text on a settings page is focusable: rows, buttons or
  `InfoBlock`. Long material goes on a sub-page (`SettingsPage.parent` makes
  Back walk up one level). `DefaultHomePageTest` walks each page end to end.
- **First key after idle only wakes.** Root `onPreviewKeyEvent` in `LauncherRoot`.
- **The Home switch never bricks a device.** It only acts on
  `DeviceKind.CLASSIC_ANDROID_TV` (tvlauncher present, no Google TV launcher).
  `com.google.android.apps.tv.launcherx` is never disabled: Google TV's Home
  key starts it explicitly, so disabling it boot-loops the device (v0.2.0 did
  this to a Google TV Streamer). Order is set Home, then grant, then disable
  tvlauncher last; `HomeCommands.runSteps` stops at the first required failure.
  Every restore path re-enables all stock launchers (launcherx too) and sets
  Home back. `HomeCommandsTest` pins all of this.
- **Self-ADB never runs unasked** and only the commands in `HomeCommands`.
  `READ_TV_LISTINGS` is not in `Manifest.permission`: use the string.
- `@Serializable` classes never get a private companion (ART-only decode failure).
- **No user-facing text in Kotlin.** UI strings come from `res/values*/strings.xml`
  via `stringResource`/`pluralStringResource`; domain enums carry no labels
  (names live in `ui/Labels.kt`). `NoHardcodedTextTest` and `TranslationsTest`
  (keys, placeholders, picker list) enforce it. Never rename a string key.
  Workflow: `docs/translations.md` (Hosted Weblate).
- **Language override** is a SharedPreferences value read synchronously in
  `MainActivity.attachBaseContext`; changing it recreates the activity. It is
  picked from a list (Settings > Language), never cycled: options are shown in
  their own names and script so nobody gets stranded in an unfamiliar
  language or RTL (`LanguagePickerTest`). Do not
  move it into the DataStore settings (async).

## Shield facts (2026-10)

- SHIELD Experience 9.2.4 = Android 11. Stock Home is `com.google.android.tvlauncher`.
  No Home picker exists; disabling tvlauncher makes ours the only HOME handler.
  Settings and notifications are separate packages and keep working. OTAs or a
  factory reset can re-enable it: the Default launcher page shows the state.
- Google TV (launcherx) is not a Shield: its Home key bypasses HOME
  resolution and starts launcherx directly. Replacing Home there needs an
  accessibility override (roadmap), never disabling.
- `READ_TV_LISTINGS` (dangerous) lets TvProvider return other apps' searchable
  Watch Next rows. Custom selections throw; filter in code. Jellyfin publishes.
- The UI renders at the output resolution: 960x540 dp at 2x on a 1080p TV,
  4x (3840x2160 px) on a 4K TV (the user's setup). Size artwork from
  `displayMetrics`, never assume 1080p: `AppCatalog.bannerWidthFor`/`iconSizeFor`,
  `Wallpapers.screenSize` (decodes never upscale past the asset).

## Unverified on hardware (check before building on it)

- Self-ADB to `127.0.0.1:5555` with Network debugging on (use **Test connection**).
- `cmd package set-home-activity` on the Shield build (marked optional).
- Watch Next contents from Netflix / NPO Start (Jellyfin is known to publish).

## Roadmap

- Accessibility-service Home watcher (Projectivy style): the only acceptable
  route to a Home switch on Google TV, and a fallback when an OTA re-enables
  the stock launcher on the Shield.
- DreamService screensaver using the built-in wallpapers.
- Next Up straight from the Jellyfin API (`/Shows/NextUp`, `/UserItems/Resume`).
- Layout/settings export and import.
- F-Droid submission: researched, deferred. Steps, recipe draft and the AI
  disclosure risk are in `docs/fdroid.md`.
