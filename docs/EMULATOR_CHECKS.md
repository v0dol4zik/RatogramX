# Android emulator regression checks

The development test APK runs local component fixtures inside Ratogram X. It
uses Android's built-in instrumentation API and does not need a test framework.
The default cases do not send Telegram messages, polls, or reactions. The
separately requested cloud rich-message case writes and removes its own
fixtures only in the current development account's Saved Messages.

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
ends with `17 passed, 0 failed` and `INSTRUMENTATION_CODE: -1`. The ADB process
exit code alone does not distinguish test failures from success.

To repeat only affected cases, pass `-e check CASE_NAME` or a comma-separated
list before the runner component, using names from its `PASS`/`FAIL` output.
For the profile, keyboard, and push-configuration regressions only:

```bash
adb -s emulator-5554 shell am instrument -w -r \
  -e check profile_tab_mutations,emoji_keyboard_navigation_and_reopen,push_configuration_error \
  org.ratogramx.client.debug.test/moe.kirao.mgx.EmulatorSmokeInstrumentation
```

These three checks do not require Ghost mode or experiments. A successful
targeted run ends with `3 passed, 0 failed`.

For Ghost content reads and the related local inbox/notification checks:

```bash
adb -s emulator-5554 shell am instrument -w -r \
  -e check ghost_reactions_pins_and_service_reads,forum_counters_storage_and_account_isolation,local_notifications_and_delayed_push \
  org.ratogramx.client.debug.test/moe.kirao.mgx.EmulatorSmokeInstrumentation
```

For local rich messages and their native editor:

```bash
adb -s emulator-5554 shell am instrument -w -r \
  -e check rich_messages_private_storage,rich_messages_markdown_syntax,rich_messages_editor_preview_and_history \
  org.ratogramx.client.debug.test/moe.kirao.mgx.EmulatorSmokeInstrumentation
```

Run `rich_messages_markdown_syntax` on Android 13 (API 33) as well as the
reference emulator to catch Java API compatibility regressions. This case
also exercises empty editor initialization and list conversion; it can run
alone without a logged-in account. The editor/history fixture still needs
an authorized development account.

Open Saved Messages once before the editor fixture so its chat is cached.
The fixture restores experiment settings and deletes only its own entries.
To check a real process restart, run `rich_messages_restart_write`, force-stop
only the development package, then run `rich_messages_restart_read_cleanup`.
The second case verifies the persisted message and private image and removes
that fixture. It can run offline; restore the emulator's previous network
settings afterwards.

`saved_rich_messages_cache_and_updates` checks native rendering of marked
Telegram source messages, account/chat isolation, source edits, reverting to
ordinary text after marker removal, the parse queue, and cache bounds using
64 synthetic messages. It does not send those messages.

For the separate, live cloud test:

```bash
adb -s emulator-5554 shell am instrument -w -r \
  -e check saved_rich_messages_cloud_roundtrip \
  org.ratogramx.client.debug.test/moe.kirao.mgx.EmulatorSmokeInstrumentation
```

This opt-in case sends generated Markdown and a synthetic image only to the
development account's Saved Messages. It verifies server acceptance of the
code-block marker, literal source, text/file editing, long-source replacement,
RTL, photo resolution using a fresh renderer, legacy migration, native editor
save, external source updates in an open viewer, and source deletion. Its
`finally` block removes only the IDs it created and matching unique fixture
markers; no other chat is targeted. It needs an online, authorized account.
This verifies the Telegram wire representation; it does not automate the
Telegram Desktop UI or authenticate a second client.

`github_updater_release_check` makes a read-only request to the public
Ratogram X GitHub Releases API. It checks completion and the GitHub update
source without downloading or installing an APK. Unlike the local fixtures,
it requires internet access.

The checks cover:

- Pixel clipping, negative insets, and overlapping top/bottom insets.
- Local forum unread aggregation, native database reload, account isolation,
  and a server read update that must not deduct the same count twice.
- Local pin-mention and reaction reads in native message/chat models, topic
  counters, database reload, new reactions on old messages, and unchanged raw
  TDLib state. A pin service message is rendered with the native message view;
  Ghost view requests use a source that does not send read receipts.
- Local rich-message storage, account/user isolation, editing/deletion,
  independent image copies, migration from the previous storage format,
  native Markdown parsing, escaping and formatted persistence, attachment
  action routing in LTR/RTL, one-field editing and Instant View rendering,
  source/selection/scroll preservation, visibility with local Premium disabled,
  and persistence across restart.
- Live Ratogram GitHub release checking without Telegram X update downloads.
- Locally read topic notifications, partially unread groups, and a delayed
  push fixture for an already viewed message.
- All application icon variants, notification bitmap cache invalidation, and
  acceptance of a tagged fixture notification by Android.
- Poll option photo loading, the native photo viewer, media removal, and
  reordered answers after a content update.
- Actual local round-video decoding, composer clipping, the Android keyboard,
  and a simulated attachment panel.
- Profile tab insertion, removal, replacement, empty lists, cached widths,
  selection bounds, and both wrapping and full-width layouts.
- Pointer taps on emoji, sticker, and GIF headers, their alignment with content,
  and reopening a keyboard after interrupting a page transition.
- Reporting an unavailable push configuration through the listener, preserving
  its reason, and avoiding delayed retries for a missing development config.

Fixtures use reserved local storage keys, restore the icon and keyboard settings,
cancel their notification, and release their media views. Screenshots are
saved to the development app's cache, including `ratogramx-smoke-*.png` and
`rich-message-preview-smoke.png`; treat them
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
