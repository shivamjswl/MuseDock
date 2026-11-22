# MuseDock 0.9.1 validation — release pending

Checked 13 November 2026 (UTC). This source update is on `fix/music-playback-performance`. There is **no newly published installation APK**.

| Check | Current result |
| --- | --- |
| Application compilation | Passed |
| Unsigned release assembly | Passed: versionCode 10, versionName 0.9.1, min API 29 / target 37 |
| Debug instrumentation APK assembly | Passed; not executed on a device |
| Focused JVM tests | **26 passed**, zero failures |
| Debug/release application-only lint | **0 errors, 17 warnings** each; test sources excluded with an external init script |
| Full Android/Robolectric suite | **Blocked before execution**: native runtime artifact redirects to `gcp.mirrors.maven.org`, denied by current cloud policy |
| New Android UI regression tests | Compiled; not executed because of that blocker |
| Original signing key | Missing after workspace restoration |
| Wrong-key release guard | Passed: newly generated test key rejected before release signing |
| Live YouTube Music / device latency | Not verified here |

Current evidence is under `validation/0.9.1/`. All other existing framework images, signature files and test reports under `validation/` describe **0.9.0**, not this revision. Do not cite the earlier 188-test run as current validation.

## Checks executed

`python3 scripts/test-core-models.py` compiles the regular unit-test sources and executes these existing/new JUnit classes directly without the blocked Android runtime:

- AudioSourceCacheTest: four checks, including a held download extraction while a different playback track completes, cache refresh, failed-request retry and invalidated in-flight result protection.
- ArtworkTest: four checks for provider sizes, supported Music cover URLs, fallback URLs, signed URL preservation and decode sampling.
- ListeningTrackerTest: three checks for audible time, pause/buffering exclusion, unplayed skips and short/unknown-duration thresholds.
- RecommendationsTest: eleven checks for taste signals, search/mood diversification, feedback, ranking, cache scope and failing Music search without generic-video fallback.
- MusicCatalogRoutingTest: four checks against the pinned NewPipe implementation: all song/album modes select the Music host and extractor; playlists use the Music playlist filter; direct video IDs cannot bypass Music search; Music watch links reach the shared audio extractor.

These are actual compiled production classes. They do not establish full Android UI behavior or measured phone startup latency.

The added PlaybackResponsivenessTest has eight API-29/37 cases covering view reuse, progress ticks under a database lock, Play with a 100-track queue under that lock, and deferring recommendation work while the player is open. These cases **have not executed**. Existing UI assertions were updated to require Music-only discovery and size-appropriate row images; no full-suite assertions were disabled.

## Build and lint scope

The normal `:app:assembleRelease` and `:app:assembleDebugAndroidTest` tasks completed. Release signing is deliberately unconfigured without the restored production identity; the output is `app-release-unsigned.apk`, not an installable update. A new test/debug key exists only for local debug assembly. It is not the previous signing key and is not published.

Normal full lint also tried to build the blocked unit-test runtime model. Application-only lint was then run with a temporary init script setting `android.lint.ignoreTestSources = true`. It passed for both debug and release. This is narrower than full lint; it is not evidence that test-source lint or the full Android test suite passed. The repository's normal lint policy remains unchanged.

Toolchain restored: JDK 21, AGP 9.3.3, Gradle 9.5.0, Android Platform 37.0 / Build Tools 37.0.0, pinned NewPipe and nanojson source. TLS and configured proxy policy remained enabled. Official repository checksums were preserved. No private signing material was copied to source or downloads.

## Remaining acceptance

1. Review/save the pending network proposal for `gcp.mirrors.maven.org` in environment settings; publish for future sessions. The draft also adds `lh3.googleusercontent.com` for Music cover checks and corrects startup paths. Saving a draft alone has not applied these domains.
2. Run the full `:app:testDebugUnitTest :app:lintDebug :app:lintRelease` commands with the permitted dependency available. Inspect any functional failures before release.
3. Restore the original key securely from a previous environment snapshot or backup. Its certificate SHA-256 must match `38e32e997464806ba585d116b36dde477b5fc815f4059f793ede084c7114c505`. A public APK cannot restore the private key. The release build refuses a replacement certificate.
4. Measure tap-to-play and navigation on the actual phone, with/without an active download, long queues, offline playback, buffering, rapid skips, background notification controls and cold/warm artwork caches.
5. Verify actual Music-catalog relevance and network failures. Existing imported playlists/tracks remain compatible but are not retroactively certified as Music catalog entries. Playback uses YouTube/Google's shared media infrastructure.
