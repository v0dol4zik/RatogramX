# Android emulator regression checks

The development test APK runs local component fixtures inside Ratogram X. It
uses Android's built-in instrumentation API and does not need a test framework
or send Telegram messages, polls, or reactions.

Use a dedicated, authorized development installation of
`org.ratogramx.client.debug`. The runner rejects public packages. Enable
experiments and the Ghost setting that disables read receipts first; Ghost
itself can remain off. Close other popups and stop media playback before running.

The reference environment is an Android 15 emulator with a 720 × 1280 display,
320 dpi, keyboard enabled, and support for the ARM64 development APK. Android
11 or newer is required for the keyboard checks. Native dependencies and local
build credentials must already be configured as described in the main README.

If the emulator's host OpenGL driver crashes, restart the same AVD with
`-gpu swiftshader -no-snapshot-load -no-snapshot-save`. This selects software
graphics without erasing the emulator's application data.

```bash
JAVA_HOME=/path/to/jdk-25 ./gradlew \
  :app:assembleLatestArm64Debug :app:assembleLatestArm64DebugAndroidTest \
  -PratogramxDevPackage=true --max-workers=2 --no-parallel

adb devices -l
adb -s emulator-5554 install -r \
  app/build/outputs/apk/latestArm64/debug/Ratogram-X-0.29.1.1817-arm64-v8a-debug.apk
adb -s emulator-5554 install -r \
  app/build/outputs/apk/androidTest/latestArm64/debug/app-latest-arm64-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r \
  org.ratogramx.client.debug.test/moe.kirao.mgx.EmulatorSmokeInstrumentation
```

Adjust the serial and versioned application APK path to the actual build. Do
not install over a public package or clear application data. A successful run
ends with `6 passed, 0 failed` and `INSTRUMENTATION_CODE: -1`. The ADB process
exit code alone does not distinguish test failures from success.

To repeat only an affected case, pass `-e check CASE_NAME` before the runner
component, using a case name from its `PASS`/`FAIL` output.

The six checks cover:

- Pixel clipping, negative insets, and overlapping top/bottom insets.
- Local forum unread aggregation, native database reload, account isolation,
  and a server read update that must not deduct the same count twice.
- Locally read topic notifications, partially unread groups, and a delayed
  push fixture for an already viewed message.
- All application icon variants, notification bitmap cache invalidation, and
  acceptance of a tagged fixture notification by Android.
- Poll option photo loading, the native photo viewer, media removal, and
  reordered answers after a content update.
- Actual local round-video decoding, composer clipping, the Android keyboard,
  and a simulated attachment panel.

Fixtures use reserved local storage keys, restore the selected icon setting,
cancel their notification, and release their media views. Screenshots are
saved to the development app's cache under `ratogramx-smoke-*.png`; treat them
as private because the emulator's surrounding UI may be visible. No server
privacy settings are changed. Account-dependent fixtures require an existing
login but do not test switching two authenticated accounts.

This suite does not prove live FCM delivery, a real forum's complete chat-list
flow, or published polls across Telegram clients. Those still need integration
testing with suitable accounts and chats.

If a broken fixture previously caused the app's recovery screen, review the
crash and use **Launch App** before trying again. For an already diagnosed
fixture crash only, `-e resumeAfterFixtureCrash true` invokes the same recovery
action before setup. The runner otherwise fails rather than hiding a crash.

The synthetic `round-smoke.mp4` asset contains only FFmpeg's generated test
pattern. It can be reproduced with:

```bash
ffmpeg -hide_banner -loglevel error -f lavfi \
  -i testsrc2=size=320x320:rate=24 -t 5 \
  -c:v libx264 -preset ultrafast -profile:v baseline \
  -pix_fmt yuv420p -crf 28 -an -movflags +faststart \
  app/src/androidTest/assets/round-smoke.mp4
```
