# Translations

BSfreeTV uses standard Android string resources. English is the source
(`app/src/main/res/values/strings.xml`); every other language lives in
`app/src/main/res/values-<code>/strings.xml` and is managed on Hosted Weblate.

Shipped in the first release (all flagged for native review on Weblate):
Dutch, German, Spanish, French, Italian, Portuguese (`values-pt`, Brazilian
wording; a `values-pt-rPT` can be added on Weblate), Polish, Ukrainian, Arabic, Japanese, Korean,
Simplified Chinese (`values-zh-rCN`). Arabic is the right-to-left reference: layouts mirror and
headings drop their letter-spacing, which would break its joined letters.

## One-time Weblate setup

1. Make the GitHub repo public, then request a Libre (gratis) project at
   https://hosted.weblate.org/hosting/ .
2. Create the project `bsfreetv` and a component `app`:
   - Source code repository: `https://github.com/xblackbytesx/bsfreetv.git`, branch `main`
   - File format: Android String Resource
   - File mask: `app/src/main/res/values-*/strings.xml`
   - Monolingual base language file: `app/src/main/res/values/strings.xml`
   - Template for new translations: same file
   - Language regular expression: leave the default
3. Version control integration: "GitHub pull request". Add the Weblate
   GitHub user as a collaborator, or install the Weblate GitHub app, so it can
   open pull requests. Add the repository webhook Weblate shows you so it
   picks up new English strings on every push.
4. Import the existing translations, then mark the twelve shipped languages as
   "Needs editing" (Tools > Bulk edit, state "Needs editing") so native
   speakers review them.
5. Add the widget to the README (Weblate > Share > Engage), replacing the
   placeholder badge.

## Adding a language

Weblate creates `values-<code>/strings.xml` on its own. When its pull request
arrives, add the language to `SupportedLanguages` in
`app/src/main/java/com/bsfreetv/launcher/i18n/Languages.kt` (tag plus its own
name). `TranslationsTest` fails until you do, and also rejects translations
whose placeholders (`%1$s`, `%d`) differ from English: a broken placeholder
would crash the launcher.

Region and script codes map like this: Android `values-zh-rCN` is tag
`zh-CN`; Traditional Chinese would be `values-zh-rTW` / `zh-TW`.

## Rules for strings

- No user-facing text in Kotlin. Use `stringResource`; domain values get
  their names in `ui/Labels.kt`. `NoHardcodedTextTest` enforces this.
- Counts use `<plurals>`, never "%d item(s)".
- Never rename a key: Weblate treats it as a new string and every
  translation of the old one is lost.
- Shell commands, package names and wallpaper titles stay untranslated.
- Check long and right-to-left text with the screenshot tests
  (`make screenshots`): they render German, Japanese, the en-XA pseudo-locale
  (about 30% longer) and ar-XB (right-to-left).
