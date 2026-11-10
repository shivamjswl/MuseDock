# MuseDock 0.8.0 validation

Checked on 10 November 2026 (Asia/Calcutta).

| Check | Result |
| --- | --- |
| Release / instrumentation APK assembly | Passed |
| APK identity | MuseDock, versionCode 8, versionName 0.8.0; original installation ID retained |
| SDK declarations | Minimum API 29, compile/target 37, no maximum SDK |
| Release flags | Non-debuggable, no testOnly, standalone APK |
| Signature / update key | Verified for API 29–37; v3 signature; certificate matches 0.7.0 |
| Alignment / ABI | zipalign with 16 KiB checks passed; no native .so files or ABI splits |
| Java backports / lyric removal | Desugared classes present; removed app lyric classes remain absent |
| Full automated suite | 153 passed; 0 failed; 0 skipped |
| Existing custom-playlist matrix | 45 checks across API 29–37 |
| New discovery UI checks | 11 checks across API 29 and 37, including native rendering on API 37 |
| Database migration / editing checks | 8 checks across API 29 and 37 |
| Recommendation model checks | 7 passed |
| PCM stereo processing | 2 sample-level tests passed: 16-bit and float, channel attenuation, center and mono bypass |
| Equalizer settings / gain checks | 7 passed, including named snapshots, gain/headroom and master bypass |
| Debug / release lint | Zero errors; advisory warnings recorded in validation/ |
| Live catalog / images / current Spotify behavior | Not verified: cloud destination policy blocks required hosts |
| Physical installation / audible DSP / codecs | Not executed here |

## Recommendation behavior and evidence

Schema 2 adds bounded search history, play counts, hidden IDs, retrieval cache and playlist metadata while keeping existing data. Version-1 fixtures are upgraded through SQLiteOpenHelper on both framework versions; tests retain the original song, membership/order, played timestamp and offline path/file. Transactions are exercised with an invalid batch member to confirm rollback. Actual phone SQL/device acceptance is still required.

The model combines local play frequency, recency, likes and recent search terms, generates multiple retrieval queries, ranks/deduplicates candidates and caps any artist at two discovery slots. Refresh rotation changes retrieval plans/ranking and playlist order. Mood query plans differ for focus, chill, energy and discovery; On repeat uses actual local records. Duplicate IDs are filtered across distinct mood shelves. If a provider returns too little distinct content, a shelf can be empty rather than fabricating songs. Mood/genre correctness and recommendation quality are not guaranteed by these tests.

Retrieval caching is scoped to query plans/language/rotation with a six-hour expiry. New preference signals can re-rank an existing pool without fetching the same queries again. Existing artwork is retained while a changed profile refreshes; stale generation callbacks are ignored. Shared bitmap and HTTP image caches, provider artwork, and a video-thumbnail fallback supply mood cards rather than static music icons. The UI test verifies an actual BitmapDrawable from injected artwork on different mood cards and opens different song collections. All artwork fixtures are original generated graphics, not real album art or evidence of successful live image requests.

Playlist discovery merges favorite-artist and search-derived query results and removes duplicate URLs. The UI exercises a new user search followed by an updated home reason, and model tests verify multiple taste signals and varied plans. This is an original local ranking layer over public YouTube Music results, not Spotify's private recommender or an authenticated account feed. The currently selected language guides queries rather than verifying audio language.

## Playlist and sound tools

UI and data tests cover descriptions, pinning, duplication, unique batch additions/merging, sorting/reversal, multi-song removal, suggested additions and adding all suggestions. Removing playlist membership retains offline files. Covers derive from track artwork/mosaics. Playlist suggestion refresh excludes current members and hidden IDs.

Playback now records a play-start signal when a new item starts playing instead of counting every queued transition; resume of the same item does not add another count. This is not completed-listening measurement. Physical playback-service behavior remains a device check, since Robolectric deliberately does not host its MediaSession Binder.

Sound studio adds ten tone curves, custom save/load/delete, preamp attenuation, stereo balance, auto headroom and reset. Tests persist/reload curve, gain, balance and headroom settings, verify player gain calculations and unity gain on master bypass, and inspect real generated PCM sample output. Native tone effects still depend on the device's driver. The software processor attenuates stereo channels, preserves encoding/order, and bypasses mono/unsupported formats; it is not a limiter, virtualizer or loudness normalizer. No audible or device codec success is claimed.

Native framework images cover the personalized home, mood mix, playlist updates, sound curve and advanced scrolled sound controls, plus existing four-theme Library/player/equalizer renders. They show actual app views with explicit test fixtures, not screenshots from a phone.

## Research and environment limits

Spotify's Made for You support page and Android's current design site were attempted through the inherited proxy and denied with CONNECT 403. Android's official Now in Android README was readable on GitHub and identifies Material 3/adaptive UI guidance: https://github.com/android/nowinandroid#ui . No claim is made that Spotify's current UI/private algorithm was inspected. The implementation uses common personalized music shelves, artwork-backed mood cards, tonal containers and selected navigation patterns, while retaining all four themes.

Custom domain additions for Spotify/Android design research and YouTube Music/images/media, and updated MuseDock startup instructions, were saved to the cloud configuration draft. The running policy was rechecked and still had no custom allowed hosts; a saved draft has not applied those changes. Review and save the proposal in environment settings, then publish for reuse. Validate fresh access before claiming live-service success. No proxy bypass, credential extraction, disabled TLS or new account credential was used.

Toolchain remains JDK 21, AGP 9.3.3, pinned Gradle 9.5.0, SDK 37.0 / Build Tools 37.0.0, Robolectric 4.17. Corresponding extractor/nanojson/jump3r/desugar sources and notices are included in the source download. Technical installation/storage identities and the signing key remain intact. The previously reported Pixel CP3A.260905.009 installer failure still needs exact package-manager output if it persists; no physical installation is established here.

APK SHA-256: `0038a76c920cbbfe096f8b60b0b9e1e64a1dfb8e0b0c856d41e246db313eec74`

## Phone acceptance still required

1. Update over 0.7.0 and verify library, offline files and migration retention on the actual phone.
2. Search several artists, play/like songs and return home. Compare Picked for you, playlist suggestions, all mood collections and On repeat. Refresh and hide a recommendation, then restart. Check actual artwork and provider/genre quality.
3. Edit playlist details, pin, duplicate, merge, sort and bulk remove. Add from Search/downloads and suggested additions; verify no duplicate memberships and retained offline audio.
4. Compare native tone presets and custom profiles, preamp/headroom/master bypass and stereo balance with online/offline tracks, headphones and other supported outputs. Check pause/resume, mono, hardware volume, background playback and codec behavior.
5. Retain existing four-theme, language, queue, history, download/MP3/export acceptance. If installation fails on the affected Android 17 Pixel, obtain the full `adb install -r MuseDock-0.8.0.apk` result.
