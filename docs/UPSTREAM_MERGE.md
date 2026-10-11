# Updating Telegram X

Use a dedicated integration branch and commit application changes first.
Inspect dirty submodules individually and preserve local native outputs.

1. Fetch upstream and select a reviewed release tag or commit.
2. Run bash scripts/upstream-diff.sh with that reference.
3. Create merge/upstream-VERSION and merge with --no-ff --no-commit and
   submodule.recurse=false.
4. Review conflicts and automatic merges using the checklist below.
5. Update only submodule revisions changed in the upstream range, after
   preserving locally modified sources and build outputs.
6. Run repository hygiene checks, Java compilation, JVM tests and ARM64 dev
   assemble before committing the reviewed integration.

## Hook checklist

- Tdlib.java, TdlibAccount.java, TdlibUi.java: local Premium versus server
  permissions, account isolation and explicit user actions.
- MessagesController.java, MessageView.java, MessagesLoader.java: topics,
  partial quotes and channel bottom actions.
- ChatsController.java, TdlibChatListSlice.java, TGChat.java: proxy sponsor
  filtering, local pins and unread counts.
- SearchManager.java, TdlibFilesManager.java, FileProgressComponent.java:
  download indexing, fallback completeness and technical-file exclusion.
- DrawerController.java, DrawerHeaderView.java: configurable entries.
- Settings.java, SettingsController.java, SettingsMoexController.java:
  global and individual experiment flags and version toast.
- Lang.java and moex_strings.xml: selected app language and EN/RU parity.
- app/build.gradle.kts, JNI and buildSrc: dev package, signing, source links
  and native task dependencies.

## Integration: 1807

Target: v0.28.11.1807 (95dc291d). TDLib revision is unchanged from 1806.
Upstream moves AndroidX Media into app/thirdparty, updates FFmpeg/libvpx/Opus
and replaces native shell scripts with Gradle tasks. The Ratogram ARM64
setup wrapper must be adapted. Conflicts and validation will be recorded
here during integration.

### Resolved conflicts

- `README.md`: retained Ratogram X branding, build instructions, notices, and
  release guidance while incorporating the upstream version context.
- `scripts/private/build-ffmpeg-impl.sh` and `scripts/private/build-vpx-impl.sh`:
  retained the Ratogram-compatible legacy wrappers; upstream's new Gradle
  native tasks remain the canonical path for 1807.
- `scripts/setup-arm64-dev.sh`: changed the wrapper to invoke the upstream
  Gradle tasks for Opus, AndroidX Media, libvpx, and FFmpeg.

### Submodules

The 1807 revisions for FFmpeg, libvpx, Opus, and AndroidX Media were checked
out. Local generated native build directories and the pre-existing modified
TDLib checkout remain outside the merge commit.

### Validation

Conflict markers and staged whitespace errors were checked before the merge
commit. Java compilation and the ARM64 development assemble are run after the
merge commit because both depend on the relocated AndroidX Media worktrees.

The Gradle libvpx task now keeps generated objects under an isolated build
prefix and explicitly publishes the archive produced for the selected ABI.
This prevents stale objects or archives left in a libvpx checkout from being
resolved through Make's VPATH. The 1807 ARM64 development APK was assembled
successfully after verifying representative archive members as AArch64 ELF
objects.

## Integration: 1817

Target: `v0.29.1.1817` (`2258d6db`). The merge retains the Ratogram X feature
layer and the launcher/forum fixes committed before this integration.

### Toolchain and submodules

- JDK 25, Gradle 9.8, Android SDK 37.2, Build Tools 37.0.0, NDK r30.
- Android 5+ uses the primary NDK. Android 4.x uses r23 with
  `-PuseLegacyNdk=true`; do not apply that flag to `lollipop` variants.
- Updated TDLib, vkryl, tgcalls, WebRTC, FFmpeg, jni-utils, rlottie, and
  AndroidX Media to the tag's gitlinks and added pixman. TDLib now packages
  native binaries by NDK, Android API, and ABI. Upstream removed libtgvoip.
- Local generated native files and the obsolete libtgvoip checkout were
  preserved outside the source commit.

### Integration decisions

- Retained Ratogram branding, source URLs, the separate development package,
  and background initialization of fork settings.
- Migrated fork code from `UI.getAppContext()` to upstream's `AppContext.get()`.
- Kept localized Ratogram string fallbacks alongside upstream's resource API.
- Replaced `GitVersionSource` with upstream's `GitInformationSource`, retaining
  the remote fallback for submodules without an `origin` remote.
- Kept local Gradle configuration out of Git and added
  `gradle.properties.sample` for public defaults; CI copies both templates.
- Updated CI's toolchain and added a `LollipopArm32` compile/assemble job
  alongside `LatestArm64` to catch compatibility regressions.
- Fixed VPN detection on API 21/22 using available-network capabilities instead
  of the API 23-only active-network getter. Biometric availability checks remain
  safe below API 23; the API requirement applies to authentication itself.

### Validation

- Java/Kotlin compilation passed for `LatestArm64`, `LollipopArm32`, and
  `MarshmallowArm32` with the separate development package enabled.
- All 18 JVM tests passed without failures or skipped tests.
- Full `LatestArm64Debug` and `LollipopArm32Debug` assembly passed, including
  native compilation, resource generation, dex desugaring, and packaging.
- APK manifests report `org.ratogramx.client.debug`, version `0.29.1.1817`,
  ARM64 / minimum API 24 and ARM32 / minimum API 21 respectively.
- Repository hygiene, EN/RU string parity, shell syntax, and staged whitespace
  checks passed.
- Focused `NewApi` lint for `LollipopArm32Debug`, including dependencies and
  using an empty baseline, passed with no errors or warnings. Existing core
  library desugaring covers the collection methods mentioned in the earlier
  compatibility diagnosis.

These are development builds. Runtime checks on API 21/23 devices and a
Docker rebuild have not been performed; release APKs have not been produced
or published as part of this integration.
