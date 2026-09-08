# Changelog

All notable Ratogram X-specific changes will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project intends to use semantic version tags for public Ratogram X
releases. The embedded Telegram X application version may continue to appear
in development build metadata.

## [Unreleased]

- Restored Telegram X's native chat-folder pager independently of Ratogram
  experiments, moved the folder-tab style override to Interface settings, and
  fixed a counter update race during folder changes.
- Removed the obsolete experimental chat swipe and double-tap action handlers.
- Optional screenshot and Recents-preview protection, globally or only while
  a secret chat is open.
- Optional confirmation before calls and voice-message sends.
- JSON export and import for Ratogram X settings without account data.
- Local access to the latest on-device crash report from Ratogram X settings.
- Documented native dependency scope, ignored local source trees, and
  baseline-profile verification.
- Low-end first-run profile now avoids drawer blur and warms Ratogram settings
  off the first UI frame.
- Experimental seven-day, size-limited local history for edited and deleted
  text messages in private chats and groups.
- Added JVM coverage for low-end thresholds, Ratogram bit masks, ghost flags,
  and the downloaded-file search predicate.

### Added

- Experimental Ghost mode with independent controls for read receipts,
  typing activity, and online presence.
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
