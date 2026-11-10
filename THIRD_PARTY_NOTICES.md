# Third-party notices

MuseDock uses:

| Component | Version/revision | License | Source |
| --- | --- | --- | --- |
| NewPipe Extractor | `711f13b15be314286a0e0a019302ad540c8f0ed7` | GPL-3.0-or-later | https://github.com/TeamNewPipe/NewPipeExtractor |
| TeamNewPipe nanojson | `e9d656ddb49a412a5a0a5d5ef20ca7ef09549996` | Apache-2.0 | https://github.com/TeamNewPipe/nanojson |
| AndroidX Media3 | 1.7.1 | Apache-2.0 | https://github.com/androidx/media |
| AndroidX Concurrent Futures | 1.2.0 | Apache-2.0 | https://android.googlesource.com/platform/frameworks/support |
| OkHttp | 4.12.0 | Apache-2.0 | https://github.com/square/okhttp |
| Okio (transitive) | Resolved by OkHttp | Apache-2.0 | https://github.com/square/okio |
| jsoup | 1.23.2 | MIT | https://github.com/jhy/jsoup |
| Protocol Buffers Lite | 4.36.2 | BSD-3-Clause | https://github.com/protocolbuffers/protobuf |
| Rhino and Rhino engine | 1.8.1 | MPL-2.0 | https://github.com/mozilla/rhino |
| JSR-305 annotations | 3.0.2 | Apache-2.0 | https://github.com/findbugsproject/findbugs |
| Kotlin stdlib (transitive) | Resolved by AndroidX/OkHttp | Apache-2.0 | https://github.com/JetBrains/kotlin |
| Guava (transitive) | Resolved by AndroidX | Apache-2.0 | https://github.com/google/guava |

The Android UI and application code are original to MuseDock. Spotify is a product reference only; no Spotify branding, assets, or APIs are included. REA code is not linked into the application.

Upstream extractor and nanojson source headers are retained. The complete-source archive contains their source trees. Dependency archives also include their bundled `META-INF` license notices; the Android package retains merged Java-resource notices. See `licenses/` for the principal license texts. This inventory does not replace upstream copyright notices.

## MuseDock 0.3.0 additions

- **jump3r 1.0.5** (Java LAME port), LGPL-2.1-or-later. Source: https://github.com/Sciss/Jump3r and the Maven Central `de.sciss:jump3r:1.0.5:sources` artifact. Exact corresponding source is included as `third_party/jump3r-1.0.5-sources.jar`; LGPL text is in `licenses/LGPL-2.1.txt`. MuseDock uses its MP3 core through an original adapter and does not modify the library. LAME and jump3r's upstream copyright/license headers are retained in the source archive.
- The default distributed MuseDock APK does **not** bundle the optional Google sign-in SDKs. A configured Google-enabled build adds AndroidX Credentials 1.5.0 (Apache-2.0), its Google Play services adapter 1.5.0 (Apache-2.0), Google ID 1.1.1 and transitive Google Play services libraries (Android Software Development Kit License). Google's SDK terms: https://developer.android.com/studio/terms.html . Review the configured build's licensing compatibility before redistributing it; these components are not relicensed by MuseDock's GPL notice.
- MockWebServer 4.12.0, JUnit, Robolectric, and generated test audio are test-only; MockWebServer is Apache-2.0 and is not a runtime download server.

## MuseDock 0.5.0 additions

- AndroidX Activity 1.10.1 and Core 1.15.0, Apache-2.0, https://android.googlesource.com/platform/frameworks/support . Their upstream resource notices are retained in the package.
- desugar_jdk_libs 2.1.5, GPL-2.0 with the Classpath Exception, https://github.com/google/desugar_jdk_libs . Its corresponding source archive and upstream license text are supplied in `third_party/` and `licenses/` respectively. It provides Java library backports for older Android releases; upstream notices remain applicable.


## Current provider integration

YouTube Music catalog support uses the already-pinned NewPipe Extractor. MuseDock 0.7.0 removes the lyrics provider integration and does not bundle a lyric corpus. Appearance and music-language preferences add no third-party runtime SDKs.

## MuseDock 0.8.0

Recommendation ranking, playlist tools, curve UI and stereo balance processing are original application code. The stereo pipeline uses the existing AndroidX Media3 dependency and native Android audio effects; no Spotify SDK, recommendation model, artwork corpus, or additional runtime library is bundled. Native-render artwork fixtures are original generated test graphics, not real album covers.
