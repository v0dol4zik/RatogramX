# Changelog

All notable Ratogram X-specific changes will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project intends to use semantic version tags for public Ratogram X
releases. The embedded Telegram X application version may continue to appear
in development build metadata.

## [Unreleased]

### Added

- Ratogram X branding, package identity, notification artwork, and launcher
  resources.
- Runtime launcher icon selector with nine icon variants.
- Search category tabs with swipe navigation and a client setting to disable
  them.
- Configurable navigation drawer entries.
- Unified profile editor for name, birthday, and personal channel management.
- Profile music actions in the player menu.
- Local visibility controls for profile music, birthdays, and personal
  channels.
- Option to hide Telegram-sponsored and recommended channel messages.
- VPN-aware proxy bypass option.
- System media pause and optional resume behavior.
- First-launch performance defaults for constrained devices.
- Dark Red Telegram X theme.
- ARM64-focused native dependency setup script.

### Changed

- Integrated and adapted selected GPL-3.0 functionality from moeGramX on top
  of Telegram X `0.28.11.1806`.
- Updated app and session icon presentation for Ratogram X.
- Updated the Telegram X base to `0.28.11.1807` and hardened the ARM64
  libvpx build against stale cross-ABI objects in native checkouts.

### Documentation

- Added public build, contribution, security, lineage, and attribution
  documentation.

## Upstream history

Telegram X release history predating Ratogram X is maintained by the upstream
project: <https://github.com/TGX-Android/Telegram-X/releases>.
