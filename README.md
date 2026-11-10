# MuseDock downloads

Developed by Shivam Jaiswal. Version **0.8.0**, for Android 10+ (API 29), targeting Android 17 (API 37).

**[Download MuseDock 0.8.0 APK](https://raw.githubusercontent.com/shivamjswl/MuseDock/downloads/MuseDock-0.8.0.apk)**

[Complete source archive](https://raw.githubusercontent.com/shivamjswl/MuseDock/downloads/MuseDock-0.8.0-source.zip) · [APK SHA-256](https://raw.githubusercontent.com/shivamjswl/MuseDock/downloads/MuseDock-0.8.0.apk.sha256)

## What's improved

- **Personalized discovery:** local searches, play counts, recency, likes and hidden-song feedback guide a varied Picked for you shelf and multi-query playlist discovery. Refresh rotates selections. Caches can be re-ranked without fetching identical queries again.
- **Mood collections with artwork:** Deep focus, Slow it down, Turn it up and Fresh finds retrieve distinct song pools and filter overlapping IDs. On repeat uses actual local records. Open, play/shuffle, refresh or save a mix to Library. Artwork comes from the provider, with thumbnail fallback and private caches.
- **Playlist tools:** edit descriptions, pin, duplicate, merge unique songs, sort/reverse, bulk remove, add directly from Search/downloads and update with suggested additions. Covers use track artwork/mosaics. Membership removal preserves offline files.
- **Sound studio:** ten tone presets, curve preview, named custom save/load/delete, preamp attenuation, software stereo balance, auto headroom and reset. The master switch bypasses adjustments; native tone-band support varies by device.
- **Updated home:** content-first artwork shelves, tonal mood cards and selected navigation indicators, with Material, Light, Dark and Liquid appearances retained.

All **153 automated tests passed**. Debug/release lint has zero errors. Tests cover schema-1-to-2 data retention, batch-edit rollback, recommendation signals/diversity, actual widget flows and artwork, custom preset persistence, gain math and PCM stereo samples. The standalone non-debuggable APK passes signature/alignment checks and uses the previous release's certificate so compatible updates retain data. It remains development-signed, not a Play Store release.

Personalization is an original on-device ranking layer over public YouTube Music results; it is not Spotify's private algorithm or genre verification. Provider relevance/availability, real image requests, audible sound effects and physical-phone acceptance still need device checks. The current cloud denied Spotify support/design hosts and live-provider destinations; proposed access settings are pending review. Android's official Now in Android Material3 reference was readable through GitHub. [Validation details](https://github.com/shivamjswl/MuseDock/blob/main/VALIDATION.md) distinguish fixture checks and phone acceptance.

Search/taste signals, hidden choices and sound profiles stay in private app storage. Artist/search-derived queries go to YouTube Music to fetch candidates. No full listening database or developer-hosted profile is uploaded. Lyrics remains removed; no lyric provider is contacted. Optional Google login still needs OAuth registration and cannot expose YouTube history.

Application code is GPL-3.0-or-later; third-party notices and corresponding source are included in the source archive. Earlier downloads remain available by filename.
