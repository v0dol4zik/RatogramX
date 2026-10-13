# Ratogram X

<p align="center">
  <img src="artwork/ratogram-logo-round.svg" width="160" alt="Ratogram X logo">
</p>

<p align="center">
  A community-driven Telegram X fork focused on customization, practical
  privacy controls, profile features, and good performance on constrained
  Android devices.
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue.svg" alt="GPL-3.0 license"></a>
  <img src="https://img.shields.io/badge/platform-Android-3DDC84.svg" alt="Android">
  <img src="https://img.shields.io/badge/base-Telegram%20X-229ED9.svg" alt="Telegram X base">
</p>

> [!IMPORTANT]
> Ratogram X is an independent, unofficial client. It is not affiliated with,
> endorsed by, or maintained by Telegram Messenger Inc. or the Telegram X
> maintainers.

Ratogram X keeps the native Telegram X interface and TDLib architecture while
adding selected functionality inspired by and integrated from
[moeGramX](https://github.com/moeCrafters/moeGramX), along with original
Ratogram X features and branding.

[![AI Slop Inside](https://sladge.net/badge.svg)](https://sladge.net)

## Highlights

- **Search tabs** for chats, media, links, files, music, voice messages, and
  downloads, including horizontal swipe navigation and an option to disable
  the tabs.
- **Profile management** for birthdays, personal channels, and profile music.
  Music can be added or removed from the player menu.
- **Bot Mini Apps** with initial support for buttons and Telegram links,
  native main/back controls and the current theme. See
  [supported features and requirements](docs/MINI_APPS.md).
- **Profile visibility controls** to locally hide phone numbers, music,
  birthdays, or personal channels from profile screens.
- **Nine launcher icons** with matching legacy, adaptive, round, and themed
  icon resources.
- **Customizable navigation drawer** with individually selectable entries.
- **Sponsored-message visibility option** for Telegram-sponsored and
  recommended messages in channels. Regular channel posts are unaffected.
- **System media coordination** that can pause and optionally resume audio
  from other apps while supported Ratogram X media is playing.
- **VPN-aware proxy behavior** that can temporarily bypass the Telegram proxy
  while a system VPN is active.
- **Low-end device defaults** that reduce animation and autoplay load on the
  first launch when Android reports constrained hardware. Every affected
  setting remains user-configurable.
- **Dark Red theme** included as `Dark Red.tgx-theme`.

The full project lineage and attribution notes are documented in
[FORK.md](FORK.md) and [NOTICE.md](NOTICE.md).

## Downloads

Verified Ratogram X APKs should be downloaded only from this repository's
GitHub Releases page. Release signing details and checksums will be published
with each release.

Do not treat APKs from unrelated websites or channels as official Ratogram X
builds.

## Building from source

### Requirements

- Linux or macOS. Windows is supported through an MSYS2-compatible shell, as
  in upstream Telegram X.
- Git and Git LFS.
- JDK 25.
- Android SDK package `android-37.2` and Build Tools `37.0.0`.
- Android NDK `30.0.16248370`, with `23.2.8568313` for legacy builds.
- CMake `3.22.1`.
- A personal Telegram `api_id` and `api_hash` from
  [my.telegram.org](https://my.telegram.org/apps).

The repository uses submodules and must be cloned recursively:

```bash
git clone --recurse-submodules https://github.com/v0dol4zik/RatogramX.git RatogramX
cd RatogramX
git lfs install
```

If the repository was cloned without submodules:

```bash
git submodule update --init --recursive
```

### Configure the local build

Copy the public template:

```bash
cp local.properties.sample local.properties
cp gradle.properties.sample gradle.properties
```

Edit `local.properties` and provide at least:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
app.disable_signing=true
app.id=org.ratogramx.client
app.name=Ratogram X
app.download_url=https://github.com/v0dol4zik/RatogramX/releases
app.sources_url=https://github.com/v0dol4zik/RatogramX
telegram.api_id=YOUR_API_ID
telegram.api_hash=YOUR_API_HASH
tgx.extension=none
```

Never commit `local.properties`, a keystore, signing passwords, or private API
credentials. The repository's `.gitignore` excludes the expected local files.

### Prepare native dependencies

For an ARM64 development device, the narrowed setup path avoids building
unused ABIs:

```bash
ANDROID_SDK_ROOT=/absolute/path/to/Android/Sdk \
  bash scripts/setup-arm64-dev.sh
```

For the complete upstream-compatible setup:

```bash
bash scripts/setup.sh
```

The full setup is interactive and may download Android SDK components and
build native dependencies.

### Build an APK

ARM64 debug build used for current Ratogram X development:

```bash
JAVA_HOME=/path/to/jdk-25 ./gradlew :app:assembleLatestArm64Debug
```

Output:

```text
app/build/outputs/apk/latestArm64/debug/
```

The `latest` SDK flavor requires Android 7.0 (API 24). Other inherited SDK
flavors include `lollipop` (API 21), `marshmallow` (API 23), and `legacy`
(API 16). ABI flavors include `arm64`, `arm32`, `x64`, `x86`, and
`universal`; not every SDK/ABI combination is valid.

Release builds require your own signing configuration. Never reuse or share a
development keystore for public releases.

## Development

Before opening a pull request:

```bash
git diff --check
JAVA_HOME=/path/to/jdk-25 ./gradlew :app:assembleLatestArm64Debug
```

Keep changes focused, preserve the Telegram X visual language, and avoid
introducing parallel networking or storage layers where TDLib already provides
the required behavior. See [CONTRIBUTING.md](CONTRIBUTING.md) for the complete
contribution workflow.

## Security

Do not report vulnerabilities in a public issue. Follow
[SECURITY.md](SECURITY.md) and use GitHub's private vulnerability reporting
for this repository. Security issues affecting Telegram or TDLib directly
should also be reported through the relevant upstream security channels.

## License

Ratogram X is distributed under the
[GNU General Public License v3.0](LICENSE). Source files and bundled
dependencies may carry additional compatible notices or their own licenses;
see [NOTICE.md](NOTICE.md) and [docs/THIRDPARTY.md](docs/THIRDPARTY.md).

If you distribute a modified Ratogram X APK, you must comply with GPL-3.0,
including providing the corresponding source code and preserving applicable
license and copyright notices.

## Acknowledgements

- [Telegram X](https://github.com/TGX-Android/Telegram-X) for the Android
  client architecture and interface foundation.
- [moeGramX](https://github.com/moeCrafters/moeGramX) for the extended feature
  layer incorporated into Ratogram X.
- [TDLib](https://github.com/tdlib/td) and the Telegram team for the Telegram
  client library.
- All upstream and Ratogram X contributors.

Telegram, the Telegram logo, and related marks belong to their respective
owners. Ratogram X branding is used only to identify this independent fork.
