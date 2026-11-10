# MuseDock development

Use the existing checkout at `/workspace/Cadence`; the application is MuseDock. This cloud task is already isolated, so do not create an extra worktree unless requested. Preserve user changes. The separate REA checkout is not an app dependency.

## Build

```bash
cd /workspace/Cadence
export JAVA_HOME=/workspace/toolchains/jdk/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/workspace/toolchains/android-sdk
export ANDROID_USER_HOME=/workspace/toolchains/android-user
export GRADLE_USER_HOME=/workspace/toolchains/gradle-home
./scripts/fetch-deps.sh
./gradlew :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest
```

The installed toolchain is JDK 21, AGP 9.3.3, checksum-pinned Gradle 9.5.0, SDK Platform 37.0 and Build Tools 37.0.0. Minimum SDK is 29 (Android 10), compile/target 37 (Android 17). Core library desugaring supplies Java library backports. The ignored local.properties SDK path and writable caches are retained under /workspace/toolchains. No app server or Docker container is needed. Release output is app/build/outputs/apk/release/app-release.apk; distribute it as MuseDock-0.8.0.apk.

Release builds are non-debuggable and signed with the original locally retained development key. Do not publish or replace that key. Technical compatibility identifiers `app.cadence`, `cadence.db`, and `soundzy.account` stay unchanged to preserve installation identity, playlists/downloads and account preferences. They are not visible app branding. The Application class is MuseDockApp. Current project and UI labels use MuseDock.

## Feature boundaries

Recommendations.Profile snapshots local searches, play counts, recency, likes, hidden IDs, language and refresh rotation. Recommendations supplies mood-specific retrieval plans, artist-diverse ranking, deduplication, novelty filtering and multi-query playlist discovery. It does not call a private Spotify recommendation API. The six-hour retrieval cache is scoped to query plans/language/rotation and can be re-ranked without re-fetching identical requests. MainActivity ignores stale generations, keeps current artwork while updating and has separate workers for discovery versus interactive searches. Search/playlist extraction no longer holds the audio resolver's cache lock. Artwork uses a shared bitmap cache and a bounded private HTTP disk cache.

Library schema 2 migrates schema 1 with additive columns/tables. It preserves tracks, playlists, member order and offline paths, seeds old play counts, and adds bounded search history, hidden IDs, recommendation cache, descriptions, pinning and update times. Batch additions/removals, sorting, duplicate and merge operations use transactions. Playback history records a new item when it starts playing, rather than counting every queued media transition; pausing/resuming the same item does not add another count. This is a play-start signal, not a completed-listening metric.

Playlist suggested additions use the collection's artists and exclude existing/hidden song IDs. Editing can add directly from Search or downloads, sort/reverse, pin, duplicate, merge or remove multiple songs. Removing membership does not delete offline files. Catalogs and playlists still come from the existing NewPipe integration; availability and genre/mood matching are not guaranteed.

AudioEqualizer retains the native per-session tone effect and adds ten curves, named snapshots, attenuation and headroom. Output gain is applied through the service-owned ExoPlayer; master bypass restores unity gain. StereoBalanceProcessor attenuates the selected channel of decoded stereo 16-bit/float PCM, preserves channel order and bypasses mono/unsupported formats. DefaultAudioSink owns the processor. Do not describe it as a limiter, spatializer, loudness normalization or verified audible output in a cloud test.

ThemePalette and all four appearances remain. The new home uses song shelves and artwork-backed mood cards; playlist covers use track artwork. UI reference: Android's official Now in Android sample describes Material 3/adaptive design at https://github.com/android/nowinandroid#ui . Spotify's current Made for You support page and live Android design documentation could not be read because the cloud proxy denied those hosts. An additive network-domain proposal was saved for review; it has not by itself changed current runtime access. Never claim the private Spotify algorithm was inspected or copied.

## Verification limits

Robolectric tests exercise SQLite, actual widgets, native framework rendering and ExoPlayer timelines. The API29–37 compatibility matrix verifies custom playlists. On API29–32 the test harness grants the app's own signature receiver permission, matching installation state. It deliberately treats the playback-service Binder as unavailable; tests can attach a real local ExoPlayer for player UI checks. JVM settings already export the file-descriptor bridge needed by Android 16/17 shadows, and test output uses musedock.validation.dir.

Run ./gradlew :app:connectedDebugAndroidTest with a usable Android10+ device for instrumentation. Instrumentation APKs compile here, but no physical-device installation, hearing test or MediaCodec acceptance is claimed. The unaccelerated emulator previously did not finish booting, so framework renders are not phone screenshots. Live YouTube/Music requests may be denied by the restricted cloud; retain proxy and TLS verification. Use native HTTPS Git authentication and never extract proxy credentials.

ResumableDownload uses real local HTTP tests for range recovery and expired links. PcmMp3Encoder uses real PCM, with the generated fixture independently decoded. MediaCodec conversion and live streaming still need device acceptance. Google sign-in remains optional, requires OAuth registration and cannot expose YouTube history; see GOOGLE_SIGN_IN_SETUP.md. The previously reported Pixel Android17 build CP3A.260905.009 installation issue still needs exact package-manager output if it persists; see INSTALLATION.md.
