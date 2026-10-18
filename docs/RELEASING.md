# Ratogram X release checklist

This checklist applies to public Ratogram X APK releases.

## Before building

- Start from a clean, reviewed commit on `main`.
- Confirm that `README.md`, `CHANGELOG.md`, and the source URL shown in the app
  match the release repository.
- Use release-specific Telegram API credentials where appropriate.
- Store the signing keystore and passwords outside the repository.
- Confirm that no deploy key, `local.properties`, keystore, or service-account
  file is tracked.
- Update submodules deliberately and record their commits.

## Build and test

- Build with JDK 25 and the SDK, NDK, and CMake versions from
  `version.properties`.
- Build every ABI that will be published.
- Install the APKs on representative devices.
- Test login, messaging, calls, notifications, media, search, profile flows,
  app icon switching, and upgrade from the previous release.
- Run `git diff --check` and the repository hygiene script.
- For profile-enabled variants, pass `-PgenerateBaselineProfile=true`, run
  the baseline-profile producer on a representative device, and verify the
  APK contains `baseline-prof.txt` with
  `unzip -l app.apk | grep baseline`.
- Native dependency scope and local source-tree handling are documented in
  `docs/NATIVE_DEPENDENCIES.md`.

## Publish

Choose APK variants deliberately; the `latest` flavor starts at Android 7.0
and cannot be installed on Android 5 or 6. ABI names alone do not indicate
the minimum Android version.

| Android version | SDK flavor | NDK | Example ARM32 task |
| --- | --- | --- | --- |
| 4.1–4.4 (API 16–20) | `legacy` | r23 | `:app:assembleLegacyArm32Release -PuseLegacyNdk=true` |
| 5.0–5.1 (API 21–22) | `lollipop` | r30 | `:app:assembleLollipopArm32Release` |
| 6.0 (API 23) | `marshmallow` | r30 | `:app:assembleMarshmallowArm32Release` |
| 7.0+ (API 24+) | `latest` | r30 | `:app:assembleLatestArm32Release` |

Use the matching `Arm64` or `Universal` task where supported. Native TDLib
and OpenSSL artifacts must match the SDK flavor, ABI, and NDK revision.

For a payload reproducibility check, compare a clean rebuild with the
published APK. The helper excludes only `META-INF/` signature entries:

```bash
reproducible-builds/verify.sh published.apk rebuilt.apk
```

Set `REPRO_BUILD=1` to perform the rebuild in the Ubuntu/JDK 25 Docker
image. Publish the resulting SHA-256 checksum beside each APK; signatures
remain verified separately with `apksigner verify --print-certs`.

- Tag the exact source commit used for the binaries.
- Create a GitHub Release from that tag.
- Use a stable release for the in-app updater. Drafts and prereleases are
  excluded. Keep the generated `Ratogram-X-<version>[-<sdk>][-<abi>].apk`
  names so the updater can choose the correct Android version and ABI;
  a universal APK is the fallback. Publish compatible legacy variants when
  supporting older Android releases.
- Increase the four-part application version and Android version code for
  every public update, including Ratogram-only fixes. Changing just the
  GitHub tag suffix does not change the installed application version and
  will not make that APK an update. The updater refuses downgrades,
  mismatched package names and APKs signed with a different key.
- Attach APKs to the release instead of committing them to Git.
- Publish SHA-256 checksums and signing-certificate fingerprints.
- Include user-visible changes, supported Android/ABI information, and known
  issues.
- Make the complete corresponding GPL-3.0 source available for every
  distributed binary.

## After publishing

- Install one APK downloaded from GitHub Releases and verify its checksum.
- Keep the signing key backed up securely and offline.
- Record urgent fixes in `CHANGELOG.md` before preparing a follow-up release.
