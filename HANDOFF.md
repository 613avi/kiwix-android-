# Kiwix+ — Handoff / continuation notes

This document lets a **local** Claude Code session (running in your terminal,
with USB/ADB access to your phone) pick up exactly where the cloud session left
off. The cloud session could not reach your device because it runs in an
isolated remote container; your phone is attached to your own machine.

To continue locally:

```bash
git clone https://github.com/613avi/kiwix-android-.git
cd kiwix-android-
claude            # then say: "read HANDOFF.md and continue"
```

---

## What this fork is

`Kiwix+` — a fork of Kiwix Android with extra reading/search features and, most
importantly for the current problem, **two ways to read ZIM content without the
Android System WebView**, for a device whose WebView is broken/blocked and which
cannot install a browser.

Branding: app name is `Kiwix+` (regular) / `Kiwix+ Gecko` (Gecko build), set in
`app/build.gradle.kts`.

## Features added (all on `main` and branch
`claude/device-support-content-search-142dhu`)

1. **Full-text (page content) search** — Titles / Page content / All books chips
   in the search screen, backed by libzim `Searcher` (Xapian). Snippets shown
   under results. Files: `core/.../search/**`.
2. **Quick book switcher** — left-drawer "Switch book" lists on-disk ZIMs, tap to
   switch without leaving the reader. Files:
   `core/.../main/reader/BookSwitcherDialog.kt`, `CoreReaderFragment.showBookSwitcher()`,
   `CoreMainActivity.openBookSwitcher()`.
3. **Search across all books** — global scope over every on-disk ZIM. Files:
   `core/.../search/viewmodel/GlobalSearchResultGenerator*.kt`.
4. **dpad focus** + **Kiwix+ branding**.
5. **Gecko build** (`-PwithGecko`) — bundles GeckoView (Firefox engine) and renders
   ZIM content **without any WebView**. Gecko is the default renderer in Gecko
   builds. Files: `app/src/gecko/java/.../EmbeddedGeckoReader.kt`,
   `app/src/main/java/.../gecko/GeckoSupport.kt`,
   `KiwixReaderFragment` (gecko overrides).
6. **Native WebView-free reader mode** (regular build, lightweight) — parses ZIM
   article HTML and renders it natively in Compose (text, headings, links, inline
   images). No WebView / browser / Gecko. File:
   `core/.../main/reader/NativeArticleReader.kt`, wired as the default
   "alternative renderer" in `CoreReaderFragment`.

## The alternative-renderer mechanism (key to the WebView problem)

`CoreReaderFragment` can show an alternative view in place of the WebView tabs:

- `shouldOpenInAlternativeRenderer(): Boolean` — decides whether to use the
  alternative renderer for the opening book.
  - Base (regular build): `isNativeReaderPreferred() || isWebViewNotAvailable()`.
  - `KiwixReaderFragment` override (Gecko build): `refreshGeckoPreference() ||
    isWebViewNotAvailable()`; in non-Gecko builds it calls `super` (native).
- `openBookInAlternativeRenderer()` — base opens the native reader; Gecko override
  opens Gecko.
- `loadUrlInAlternativeReader(url)`, `onAlternativeReaderBackPressed()`,
  `closeAlternativeReader()` — same base/override split.
- `setAlternativeReaderView(view)` puts the view into `ReaderScreenState.alternativeReaderView`,
  which `ReaderScreen.kt` renders instead of `selectedWebView`.
- Hardening: `getCurrentWebView()` returns **null** (never creates a WebView) when
  `isAlternativeReaderActive() || isWebViewNotAvailable()`.

Preferences (DataStore, `core/.../utils/datastore/KiwixDataStore.kt`):
- `preferGeckoRenderer` (default = `preferGeckoRendererDefault`, set true in Gecko
  builds by `KiwixApp.onCreate`). Settings switch "Use Gecko engine".
- `nativeReaderMode` (default false). Settings switch "Native reader mode".

WebView availability check: `core/.../utils/WebViewAvailability.kt` uses
`WebView.getCurrentWebViewPackage()` (API 26+).

## The open bug to debug on-device

**Symptom:** on a device with the Gecko build installed and a broken/blocked
WebView, the app still shows the (blocked) system WebView instead of Gecko.

**Leading hypotheses (verify with logcat):**

1. **Old APK installed.** Early Gecko builds defaulted `preferGeckoRenderer=false`.
   Fix: uninstall + install the latest build fresh:
   `adb uninstall org.kiwix.kiwixmobile` (and `...standalone` if present), then
   install `kiwix-plus-gecko-*.apk` from the latest release.
2. **WebView is *disabled/blocked* but still "present".** If
   `WebView.getCurrentWebViewPackage()` returns non-null,
   `isWebViewNotAvailable()` is **false**, so the app does not auto-switch — it
   relies on the Gecko-default preference. If the stored preference is somehow
   false, it uses WebView. Confirm the effective renderer from logcat and the
   Settings → Extras switch state.
3. **A WebView is created/shown before Gecko attaches.** Check whether a
   `KiwixWebView` is instantiated during restore/tab paths. The restore path
   (`KiwixReaderFragment.restoreViewStateOnValidWebViewHistory`) already routes to
   `restoreBookInGecko` when `shouldOpenInAlternativeRenderer()` is true —
   verify that predicate returns true at runtime.

**Immediate user workaround:** Settings → Extras → enable "Use Gecko engine"
(Gecko build) or "Native reader mode" (regular build) to force the alternative
renderer regardless of auto-detection.

### Data to capture on-device

```bash
adb logcat -c
# open a book in the app, then:
adb logcat -d | grep -iE "gecko|webview|kiwix|ZimFileReader|Could not|alternative"
adb shell dumpsys webviewupdate | grep -iE "current|package|valid|fallback"
adb shell pm list packages -d | grep -iE "webview|chrome"   # -d lists DISABLED pkgs
adb shell dumpsys package org.kiwix.kiwixmobile | grep -iE "versionName|versionCode"
```

`versionName` ending in `-gecko` confirms a Gecko build. Compare `versionCode`
against the installed APK to confirm it is the latest.

### Likely fix directions if it is hypothesis 2/3

- Detect a *blocked* WebView, not just a *missing* one: attempt to construct a
  throwaway `WebView` at startup inside try/catch and, on failure
  (`AndroidRuntimeException`/`Resources.NotFoundException`), call
  `WebViewAvailability.markWebViewUnavailable()` so `isWebViewNotAvailable()`
  becomes true and the alternative renderer engages automatically.
  Hook point: `CoreReaderFragment.onViewCreated` already guards the
  `WebView(it).destroy()` workaround with `WebViewAvailability.isWebViewAvailable`;
  widen that catch to flip the flag.
- Ensure `setAlternativeReaderView` is applied before any `getCurrentWebView()`
  call in the open/restore flow.

## Build & release

- Local verify (Android SDK required; project uses Gradle 8.14.3):
  - Regular: `./gradlew :app:assembleDebug`
  - Gecko: `./gradlew :app:assembleDebug -PwithGecko -PgeckoAbi=arm64-v8a`
    (single ABI; `armeabi-v7a` for 32-bit). Gecko needs `minSdk 26` (Android 8+).
  - Minified/signed (dev key): `APK_BUILD=true ./gradlew :app:assembleStandalone`
    → `app/build/outputs/apk/standalone/app-*-standalone.apk`.
- CI release: `.github/workflows/kiwix-apk-release.yml`. Triggered by editing
  `.github/kiwix-release-trigger` on a `claude/**` branch (or a `kiwix-build-*` /
  `gecko-build-*` tag, or manual dispatch). It publishes:
  `kiwix-plus-universal.apk` (all ABIs, WebView + native reader),
  `kiwix-plus-armeabi-v7a/arm64-v8a.apk`, `kiwix-plus-debug.apk`, and the two
  `kiwix-plus-gecko-*.apk`.
- Latest release at time of writing: tag `kiwix-build-5`
  (https://github.com/613avi/kiwix-android-/releases).

## Archived work

The pre-removal full Gecko implementation is on branch
`archive/gecko-embedded-reader` (it was removed and then restored; the archive
branch is a safety copy).

## Recommended next step for the local session

1. Confirm the installed `versionName`/`versionCode` vs the latest APK.
2. Capture the logcat block above while opening a book.
3. Decide between hypotheses and, if it is a blocked-but-present WebView
   (hypothesis 2), implement the "detect blocked WebView at startup" fix so the
   alternative renderer engages automatically without the user toggling settings.
