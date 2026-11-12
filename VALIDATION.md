# MuseDock 0.9.0 validation

Checked 12 November 2026 (UTC).

- Full automated suite: **188 passed**, zero failures, errors or skipped tests.
- Release and instrumentation APK assembly passed. Debug/release lint: zero errors (advisories retained in validation/).
- APK: MuseDock 0.9.0 / versionCode 9, minimum API 29, compile/target API 37, no maximum SDK or ABI split. Non-debuggable, no testOnly.
- Original certificate retained; signature verification API 29–37 and 16 KiB zip alignment passed. No native .so files. Desugaring present.
- APK SHA-256: `743dbb3373ab6ac7edfd4a8202445aadd0237a3c832159cebbf4db74c0420856`.

## Artwork

The extractor now chooses the largest reported image. Known Google cover URLs request 320/640/1280 sizes as appropriate; large video art requests try maxres, sd and hq variants. Supplied URLs remain fallbacks and unknown/signed URLs are preserved. Decode retains high resolution instead of scaling every image down for list rows. Concurrent identical requests share work. The byte/dimension limits, cache budget and HTTPS checks remain bounded.

Four pure artwork-policy checks and six framework image-loader checks verify provider-size selection, resized URLs, fallback ordering, preserving signed/unknown URLs, decode sizing, actual 1280-pixel player images after a smaller list fetch, HTTP-error/undersized-placeholder fallback and request coalescing. Network responses in those loader tests are injected PNG fixtures; they do not establish live CDN access. Missing high-resolution originals cannot be restored by upscaling. Native four-theme player renders use original 1280-pixel test artwork; home fixtures remain explicitly labeled test mixes.

## Listening and recommendations

Schema 3 adds a listening-feedback table. Twelve migration/library checks across API 29 and 37 include upgrades from schema 1 and 2, retention of prior searches/tracks/memberships/download paths, feedback persistence and playlist editing/transactions. Technical installation/database identities remain unchanged.

Three clock-based tracker tests plus six Media3 listener tests across API 29 and 37 verify early skips, audible-time thresholds, buffering/pause exclusion, real ExoPlayer next-item callbacks, same-item seeking, single positive credit, errors and unplayed transitions. Meaningful listening is 30 seconds or half the song (minimum five seconds for short songs); early skips are before 30 seconds and a quarter of known duration. Unknown duration uses the 30-second threshold. A recent skip suppresses that song for seven days; subsequent qualified listening clears that cooldown. Historical play-start counts are retained but cannot be retroactively corrected.

Eleven recommendation tests verify current-song/singer weighting, explicit style clues and word boundaries, distinct mood ranking, search retrieval, multiple queries, duplicate/hidden/familiar filtering, diversity, feedback suppression/recovery, cached re-ranking and provider-related fallback. Queries are heuristics over public YouTube Music search results. Optional public YouTube related items broaden the candidate pool; this is not authenticated account recommendations or Spotify's private model. Artist metadata may be uploader names, and textual genre/mood hints are not verified audio analysis. Provider content relevance still requires real listening.

Eight adaptive-queue checks across API 29 and 37 verify that generated-mix refreshes retain prior/current songs, filter feedback/duplicates, reject stale queue snapshots and keep ordinary/shuffled queues or empty-result queues intact. Refreshes apply only to generated mixes with shuffle off, not On repeat or saved playlist contents. User edits, changing tracks, unavailable providers and activity/service teardown cannot allow a late result to overwrite a new queue. Physical background/headset behavior remains a phone check.

## UI and regression checks

Native framework renders cover the colorful home and mood collections, updated player with cover-derived tint and rounded transport controls, translucent Liquid surfaces, Library, playlist editing and sound tools. Original generated artwork is used only for tests. Existing 45-case API 29–37 custom-playlist matrix, download/resume/export, queue, history, account, four-theme/language, equalizer/gain/PCM and discovery UI suites remain green.

The first validation pass caught an original-artwork metadata roundtrip regression; Track.from now preserves the source artwork field from MediaItem extras, while notification metadata still gets the fallback. The unchanged queue equality test passed after correction.

## Limits and phone acceptance

The current enforced cloud network policy does not include YouTube/Music/image destinations; live provider and CDN behavior was not verified. Previously saved access proposals have not been applied. No network-policy bypass was used. No physical phone installation or audible playback claim is made by these tests.

1. Update over the existing app and confirm playlists, downloads and searches remain.
2. Search/play different singers and styles, compare recommendations and mood collections, and check large covers against rows on the actual phone.
3. Skip a generated-mix track within a few audible seconds (shuffle off), wait for the network refresh and confirm only upcoming songs change. Try pause/buffering, same-track seeks, a manual queue edit during refresh, a saved playlist and offline failures.
4. Play a cooled-down song intentionally for 30 seconds to restore its eligibility; restart and check feedback persistence.
5. Compare player/layout readability under Material, Light, Dark and Liquid, including large font sizes, system insets and accessibility controls.

Toolchain: JDK 21, AGP 9.3.3, Gradle 9.5.0, Android SDK/Build Tools 37.0, Robolectric 4.17. Corresponding dependency sources and notices accompany the source archive; private signing material is excluded.
