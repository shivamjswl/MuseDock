# Install MuseDock 0.8.0

Use the complete, standalone APK from the **downloads** branch:

[Download MuseDock 0.8.0](https://raw.githubusercontent.com/shivamjswl/Soundzy/downloads/MuseDock-0.8.0.apk)

The APK declares minimum SDK 29 (Android 10) and target SDK 37 (Android 17), with no maximum SDK and no device-ABI restriction. It has no packaged native `.so` libraries. Its signing certificate matches the previously supplied APK, and its versionCode is 8, so Android can update those versions while preserving data. This is a release-mode, non-debuggable APK signed with the existing development key; it is not a Play Store release.

## Recommendations and controls

Install as an update to retain your existing library. The version-2 database upgrade adds preference/history counters and playlist metadata without resetting saved tracks or downloads.

- Search for a few favorites, play songs and use Likes. Return to Listen for Picked for you, actual mood collections and On repeat. Use the refresh icon for another selection. A song's menu offers Hide from suggestions; this does not remove it from your library.
- Tap a mood card to open its song list, play/shuffle it, refresh, or save it to Library. Different mood shelves filter overlapping song IDs; an empty mood asks for refresh instead of reusing the same songs.
- Open a personal playlist's **⋯** menu for Edit details, Add songs from search, Add downloaded songs, Refresh suggestions, Sort songs, Select songs, Duplicate, Merge, Pin and Delete. Suggested additions have + and Add all controls. Select songs removes membership, preserving audio files.
- **⋯ → Equalizer** opens the sound studio. Try the ten presets, five sliders, preamp attenuation, stereo balance, auto headroom, custom presets and Reset. Preamp/headroom can reduce player loudness; use the master switch to compare the original sound. Tone bands depend on device support.
- **⋯ → Appearance** retains Material, Light, Dark and Liquid. Music language still guides discovery through its chip/menu.

Recommendation quality depends on local signals and provider availability; source artwork needs a successful image request or cache entry. This release has automated and native-render checks but still needs live-provider and audible device acceptance.

## Create a playlist from downloaded songs

1. Open **Offline**.
2. Tap **Create playlist from downloads**.
3. Name it and tap **Save** or the keyboard **Done** action.
4. Choose songs (or **Select all**) and tap **Create playlist**.
5. The saved playlist opens in **Library** and can play its downloaded songs without a connection.

To create an empty playlist, use **Library → Create playlist**. In a playlist, use **Add downloaded songs**, or use the visible **+** beside a song to add it to a playlist.

## If Android says “Something went wrong. App not installed”

The affected phone is a Pixel running **Android 17 build CP3A.260905.009**, confirmed by the supplied Settings screenshot. The exact Pixel model and package-manager failure are still unknown. The generic message does not identify the cause.

Version 0.8.0 lowers the minimum SDK to 29 for Android 10 and adds compatible APIs/Java backports. It also switches the distributed APK to a non-debuggable release build while retaining its package name and signing key. Lowering the minimum SDK does not explain or fix an Android 17 rejection by itself. Installation on this specific Pixel is still unverified; framework tests and signature checks do not reproduce a device installation.

Download the new APK completely from the direct link, then open it in Files and permit installation from that specific file manager if Android requests it. Keep the original app installed for an in-place update. Confirm adequate free storage. The expected APK size and SHA-256 checksum are published alongside the download.

If it still fails and you have Android Platform Tools on a computer, connect the phone with USB debugging authorized and run:

```bash
adb install -r MuseDock-0.8.0.apk
```

This keeps app data on a compatible update and reports a concrete reason. Useful examples:

- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: an installed copy is signed with a different key. That must be resolved without assuming the data can be discarded.
- `INSTALL_FAILED_VERSION_DOWNGRADE`: the installed version has a higher versionCode than this APK.
- `INSTALL_FAILED_INSUFFICIENT_STORAGE`: free device storage is too low.
- `INSTALL_FAILED_OLDER_SDK`: the device is below the APK's declared minimum (Android 10).
- An invalid/corrupt APK error: compare the downloaded file's size/checksum to the published values.
- A device-policy, developer-verification, or installer block: follow the phone's own diagnostic guidance; rebuilding the playlist UI does not establish that such a block has been resolved.

Report the exact `adb install` result if available. No account password or token is needed. MuseDock cannot bypass device-management or system install policy.
