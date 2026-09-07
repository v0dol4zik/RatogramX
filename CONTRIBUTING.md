# Contributing to Ratogram X

Thank you for helping improve Ratogram X.

## Before starting

- Search existing issues and pull requests.
- For a large feature or behavior change, open a discussion issue first.
- Keep the Telegram X interaction model and visual language unless the change
  has an approved design.
- Use TDLib as the source of Telegram data and state. Do not add a parallel
  Telegram networking implementation.
- Clearly identify code ported from another open-source project and preserve
  its license and attribution.

## Development setup

Follow the build instructions in [README.md](README.md). Use a personal
Telegram API ID and hash in the ignored `local.properties` file.

Never commit:

- `local.properties`;
- signing keystores or passwords;
- private API credentials;
- production service-account credentials;
- deploy keys;
- generated APKs or native build directories.

## Change guidelines

- Keep commits focused and explain why the change is needed.
- Avoid unrelated formatting or generated-file churn.
- Preserve existing user changes when working in a dirty tree.
- Add or update English strings first, then update maintained translations
  when possible.
- For UI work, include screenshots or a short recording in the pull request.
- For performance work, provide before/after measurements and device details.
- For imported upstream changes, name the source project and commit when
  practical.

## Verification

At minimum, run:

```bash
git diff --check
JAVA_HOME=/path/to/jdk-21 ./gradlew :app:assembleLatestArm64Debug
```

For JVM unit tests, run:

```bash
JAVA_HOME=/path/to/jdk-21 ./gradlew :app:testLatestArm64DebugUnitTest
```

Test the affected flow on a real device or emulator. Mention anything that
could not be tested in the pull request.

## Pull requests

A good pull request contains:

- a concise title;
- the problem and the chosen solution;
- testing steps and results;
- screenshots for visible UI changes;
- upstream attribution where relevant;
- no private data or binary build artifacts.

By contributing, you agree that your contribution is distributed under the
project's GPL-3.0 license and any applicable per-file notices.
