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

- Build with JDK 21 and the SDK, NDK, and CMake versions from
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

- Tag the exact source commit used for the binaries.
- Create a GitHub Release from that tag.
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
