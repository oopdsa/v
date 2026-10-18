# Temporary Media3 demo

This source set is included only in Debug builds. Release builds and product navigation are unchanged.

## Record

1. Edit the five URL constants at the top of `java/com/example/pgck2/demo/MediaDemoActivity.java`.
2. Use a direct HLS master URL with multiple variants to demonstrate HD/Auto. Do not use a YouTube page URL. Existing project dependencies include HLS, not DASH.
3. Supply two matching VTT/SRT subtitle URLs and two audio URLs matching the video's timeline. Empty URLs are deliberately rejected when selected.
4. Run the Debug app. Open the separate **Media3 Demo** launcher icon. Alternatively choose **Specified Activity** `com.example.pgck2.demo.MediaDemoActivity` in Android Studio's run configuration, or run:

   `adb shell am start -n com.example.pgck2/.demo.MediaDemoActivity`

5. Small buttons 1/2/3 switch levels, keeping the current timestamp and resetting options.
   - 1: basic playback (play/pause and timeline), displayed with the existing product controller.
   - 2: configured buffer, 10-second seek, speed, volume/mute.
   - 3: level 2 plus quality, two CC choices, original/two dub choices, PiP with rewind 10s / play-pause / forward 10s.

## PiP

At level 3, tap the PiP icon and choose source aspect ratio, landscape 16:9, or portrait 9:16. These options change the window's shape, not a percentage of the display. Video keeps its own aspect ratio, so portrait windows can have black bars around landscape content.

On Android 12+, single-tap the PiP window to reveal controls, double-tap to switch small/large, or pinch with two fingers to resize. Android chooses the permitted size range. The public Activity PiP API cannot set exact bounds or a fixed 25% of the screen. Use the size-help menu for these instructions. Return to the Activity and choose a different aspect mode to demonstrate landscape/portrait.

Three distinct PendingIntents send rewind, play/pause, and forward commands to the Activity's non-exported receiver. Seek uses the configured 10-second increments and keeps the existing play/pause state. Seek actions are disabled for unseekable content. Android controls how many actions it displays; on devices with fewer than three slots, the demo keeps Play/Pause.

Official references: https://developer.android.com/develop/ui/views/picture-in-picture and https://developer.android.com/reference/android/app/PictureInPictureParams.Builder

Changing dub rebuilds the source and may rebuffer. This merges sources during playback, not an exported file. A fixed quality needs a matching track. Audio and subtitle URLs must match the video content.

On Android emulators, this screen prefers a software video decoder when available to avoid corrupted/stale Goldfish H.264 output during adaptive quality changes. Real devices keep the normal decoder selection. Software decoding can use more CPU, especially at 2x or high resolutions.

## Remove

Delete this README, `src/debug/AndroidManifest.xml`, `src/debug/java/com/example/pgck2/demo/MediaDemoActivity.java`, `src/debug/res/layout/demo_player.xml`, and the two `src/debug/res/drawable/demo_*_10.xml` icons. No production file needs reverting. Do not delete future unrelated debug files.

## Commit only the demo

Stage only the demo source set, from the repository root:

```sh
git add app/src/debug
git diff --cached --name-only
```

Expected files:

- `app/src/debug/AndroidManifest.xml`
- `app/src/debug/README.md`
- `app/src/debug/PRESENTATION.md`
- `app/src/debug/java/com/example/pgck2/demo/MediaDemoActivity.java`
- `app/src/debug/res/layout/demo_player.xml`
- `app/src/debug/res/drawable/demo_replay_10.xml`
- `app/src/debug/res/drawable/demo_forward_10.xml`

Leave unrelated `.idea`, `app/src/main`, `app/src/test`, `app/src/androidTest`, and `presentation-output` changes unstaged for this demo-only commit. Keep those files on disk; do not delete or reset them. If unrelated files were already staged, `git add app/src/debug` does not unstage them, so review the list before committing.

The demo Activity does not call any product Java class. It uses Media3/AppCompat/Core libraries already declared in the project, and the existing product controller XML/icons through `R`. These original resources are already in HEAD, so no product-file change is needed for this commit. This remains a source set of PGCK2, not a standalone Android project.

Speaker notes and a recording sequence are in `PRESENTATION.md`. Remove that demo-only guide too when removing this screen.
