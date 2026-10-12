# Changelog

All notable Ratogram X-specific changes will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project intends to use semantic version tags for public Ratogram X
releases. The embedded Telegram X application version may continue to appear
in development build metadata.

## [Unreleased]

- Updated the Telegram X base to 0.29.1.1817, including the matching TDLib,
  call libraries, native toolchain, and background-notification fixes.
- Restored VPN detection on Android 5 and verified the Android 5 ARM32 build;
  documented SDK/ABI variants and added older-device build coverage to CI.
- Poll messages now display formatted descriptions and photo/video/GIF
  attachments. Layout, edits, translation, and media viewing include the
  description and attachment; other attachment types show an icon and title.
- Forum topics use their own read markers, unread counters, and saved scroll
  positions; marking a forum read also clears its topics, mentions, and reactions.
- Centered default topic icons and kept unread badges clear of the discussion button.
- Ghost mode switches now reflect the master switch and restore the last selected
  submode when enabled again.
- Ghost mode remembers local read positions and unread progress per account,
  chat, and topic, including after restarting the client. Explicit Mark as read
  actions retain their server behavior.
- Unread badges in ordinary chats decrease while scrolling through loaded
  incoming messages, including while TDLib repairs a stale unread count.
- Long videos resume from the last playback position when reopened. Bookmarks
  are kept locally per account, cleared on completion or logout, and excluded
  for secret, self-destructing, and view-once media.
- Added four video-message quality presets, including 384 px at 1 Mbps, and
  bitrate choices for compressed videos. Trimming in original quality keeps
  source frame rate and bitrate; encoded dimensions match the quality preview.
- Automatic unread reaction effects wait until an overlapping popup closes,
  including effects whose stickers finish loading while the popup is open.
- With partial quotes enabled, tapping a reply centers and highlights its quoted
  text or caption, including messages loaded from history and other chats.
- Launcher icons now use separate adaptive background, foreground, and
  monochrome layers, with matching square and round legacy variants.
- Changing the launcher icon keeps the current app task open and updates its
  Recents icon; tasks opened before updating switch safely in the background.
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
