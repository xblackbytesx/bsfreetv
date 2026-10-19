# F-Droid rollout (deferred)

Status: researched 2026-10-19, not started. Nothing below is implemented yet.

## Feasibility

- Technically a good fit: everything is FOSS (Hilt, Compose, DataStore, Coil,
  dadb, okio), no Play Services, Firebase or tracking, builds from source. No
  anti-feature applies; disabling the stock launcher over ADB is not one.
- The real risk is AI:
  - F-Droid has no adopted AI policy yet. An interim generative-AI policy is
    under discussion and asks for disclosure; board/reviewer sentiment is mixed
    to hostile. Reviews take weeks to months regardless (understaffed).
  - IzzyOnDroid rejects LLM-generated code: not an option.
  - The Impasto wallpapers are AI-generated, so their GPL may not hold
    (AI output may not be copyrightable). Expect a reviewer question.
- Plan: prepare the repo, disclose AI assistance and wallpaper origin up front,
  submit. Obtainium on GitHub releases stays the dependable channel.

## Repo changes

1. **Versions committed, not injected.** F-Droid's `UpdateCheckMode: Tags`
   reads them from source at each tag; `-P` overrides are invisible to it.
   - `gradle.properties`: `appVersionName=0.1.0`, `appVersionCode=1000`
     (X*1000000 + Y*1000 + Z). `app/build.gradle.kts` already reads these.
   - `release.yml`: stop passing `-P`; fail when the tag is not
     `v$appVersionName` or the code breaks the formula.
   - Release flow: bump `gradle.properties`, commit, tag.
2. **`gradle/wrapper/gradle-wrapper.properties`** pinned to 9.8.0 with
   `distributionSha256Sum`. `gradlew-fdroid` reads it; no jar or script needed.
   F-Droid only knows Gradle versions whose checksum it has added.
3. **`dependenciesInfo { includeInApk = false; includeInBundle = false }`** in
   `app/build.gradle.kts`: the scanner flags Google's dependency blob.
4. **Reproducible release builds**, so F-Droid ships our own signed APK (same
   key as GitHub, no update conflicts).
   - CI job: build `:app:assembleRelease` twice from clean checkouts, compare
     with `apksigcopier compare` (diffoscope on failure).
   - Usual suspects: baseline profile `.dm`/ArtProfile output, R8, PNG
     crunching (we only ship vectors and WebP), VCS info, JDK differences.
   - If the profile differs, disable the ArtProfile tasks for release.
   - Signing: the docs recommend build-tools 34 apksigner; verify ours with
     `apksigcopier compare` before relying on it.
5. **Fastlane metadata** in `fastlane/metadata/android/en-US/`:
   - `title.txt` (50 max), `short_description.txt` (80 max),
     `full_description.txt` (4000 max, with AI and wallpaper disclosure and the
     Shield / Android 11+ note)
   - `images/icon.png` (512 px from the vector), `images/featureGraphic.png`
     (1024x500), `images/tvBanner.png` (1280x720)
   - `images/tvScreenshots/*.png` from `docs/screenshots/` (PNG or JPEG only)
   - `changelogs/1000.txt` (500 max, exact versionCode)
6. **Disclosure** in README and CREDITS: a short "How this was made" note.
7. **The keystore**: `keystore/debug.keystore` is not flagged by the scanner,
   but reviewers may ask; it only signs `.debug` builds.

## Draft recipe (`metadata/com.bsfreetv.launcher.yml` in fdroiddata)

```yaml
Categories:
  - System
  - Theming
License: GPL-3.0-only
SourceCode: https://github.com/xblackbytesx/bsfreetv
IssueTracker: https://github.com/xblackbytesx/bsfreetv/issues

RepoType: git
Repo: https://github.com/xblackbytesx/bsfreetv.git
Binaries: https://github.com/xblackbytesx/bsfreetv/releases/download/v%v/bsfreetv-v%v.apk

Builds:
  - versionName: 0.1.0
    versionCode: 1000
    commit: v0.1.0
    subdir: app
    gradle:
      - yes

AllowedAPKSigningKeys: <release cert SHA-256, lowercase hex, from make keystore>

AutoUpdateMode: Version
UpdateCheckMode: Tags
UpdateCheckData: gradle.properties|appVersionCode=(\d+)|.|appVersionName=([\d.]+)
CurrentVersion: 0.1.0
CurrentVersionCode: 1000
```

`:baselineprofile` is only configured, never built, in this recipe.

## Submission (outward-facing, done by hand)

1. Tag `v0.1.0` with release signing set up, so the GitHub release exists.
2. Fork `gitlab.com/fdroid/fdroiddata`, branch `com.bsfreetv.launcher`, add
   the recipe.
3. Push and let the fork's CI run `fdroid lint` and `fdroid build`. This is
   the real check for Gradle 9.8.0 checksums, AGP 9.4 and platform 37 on the
   build server.
4. Open the merge request with the "New App" label and the AI disclosure in
   the description. Expect weeks to months.
5. After merge: the build log should say the signature was copied
   (reproducible).

## Sources

- https://f-droid.org/docs/Inclusion_Policy/
- https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://f-droid.org/docs/Build_Metadata_Reference/
- https://f-droid.org/docs/Reproducible_Builds/
- https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/
- https://gitlab.com/fdroid/fdroidserver/-/blob/master/fdroidserver/scanner.py
- https://gitlab.com/fdroid/admin/-/work_items/699 (interim AI policy)
- https://izzyondroid.org/docs/general/AppInclusionPolicy/
