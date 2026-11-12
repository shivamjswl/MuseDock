# MuseDock downloads

Developed by Shivam Jaiswal. Version **0.9.0**, for Android 10+ (API 29), targeting Android 17 (API 37).

**[Download MuseDock 0.9.0 APK](https://raw.githubusercontent.com/shivamjswl/MuseDock/downloads/MuseDock-0.9.0.apk)**

[Complete corresponding source](https://raw.githubusercontent.com/shivamjswl/MuseDock/downloads/MuseDock-0.9.0-source.zip) · [APK SHA-256](https://raw.githubusercontent.com/shivamjswl/MuseDock/downloads/MuseDock-0.9.0.apk.sha256)

- Larger, clearer artwork requests with fallback images, placeholder rejection, concurrent-request sharing and independent full-player image sizes.
- Recommendations combine the current song, singers, searches, likes, meaningful listening, textual genre/style clues and public provider-related tracks.
- Early skips lower a song's priority and give it a temporary recommendation cooldown. Listening again for 30 seconds, or half of a short song (minimum five seconds), restores eligibility. Pauses, buffering, seeks within a song and playback failures do not count as early skips.
- Generated mixes refresh their upcoming songs after early skips when shuffle is off. Current/history items, saved playlist contents and later manual queue edits are preserved. Unavailable providers leave the queue intact.
- A colorful home, translucent Liquid surfaces and an artwork-tinted player with refreshed controls. Select **⋯ → Appearance → Liquid**.

**188 automated tests passed**, with zero debug/release lint errors. The standalone, non-debuggable APK retains the previous signing certificate and installation identity for compatible updates. Keep the existing app installed to retain its library. It remains development-signed, not a Play Store release.

Live provider relevance, real CDN image availability and phone playback remain device checks. This is an original local recommendation system, not Spotify's private algorithm or verified genre classification. High-resolution originals are used when the provider supplies them; missing image detail cannot be recovered by upscaling. [Validation details and phone checks](https://github.com/shivamjswl/MuseDock/blob/main/VALIDATION.md).

Playlist editing, downloads/MP3 export, language choices, the four themes and expanded equalizer remain available. Private history and feedback stay on the device; derived searches and current video IDs are sent to the provider for retrieval. No developer-hosted listening profile or analytics is added.

GPL-3.0-or-later application source, corresponding dependency sources and notices accompany the source download. Older builds remain available by filename.
