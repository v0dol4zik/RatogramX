# Rich message viewing

Ratogram X displays TDLib's `MessageRichMessage` in a native chat bubble with
a compact text preview, the first available media thumbnail, an attachment
count and an **Open full message** button. A generating message has a native
progress indicator. Opening incomplete content requests `GetFullRichMessage`;
failed requests leave the button available to retry.

Full viewing reuses Telegram X's Instant View renderer. It supports section
headings at six sizes, paragraphs, lists, tables, quotes, details, photo/video/
animation media, audio, voice notes and documents. Mathematical expressions
are shown as their original LaTeX in monospace, including inline formulas.
An expandable quote is shown in full. Button rows use native rich-text button
links: URL, user-profile and copy-text actions work; disabled buttons do
nothing. Other actions show a localized unsupported-action notice. Unknown
blocks show a localized placeholder and log only their
constructor identifier. Unknown rich-text spans are skipped.

Both the bubble and an open viewer follow `UpdateMessageContent`. An outdated
full-content response cannot overwrite a newer update. The viewer preserves
its visible item and offset on replacement and closes after source deletion
or a change to another message type.

Chat-list, reply, notification and pinned-message previews have a bounded
text representation, retaining spoilers and supported entities. It includes
nested captions and text in lists, quotes and tables. Diff spans show the old
text struck through and the new text highlighted in the native renderer.
The plain preview represents the new text with bold formatting.

Forwarding shares the original message and its blocks through Telegram.
External export of a single rich message shares its currently available text;
individual attachments can be opened from the full viewer. The viewer honors
source content protection for copying, forwarding, screenshots, media saving
and document opening.
Copy/save actions recheck protection when a menu action or asynchronous file
check completes. Rich audio keeps its source permission in the player and
when restoring the playlist.

## Scope

This is viewing support. The ordinary text composer cannot edit a rich
message without losing its blocks, so rich-message editing remains disabled.
Block composition, AI creation/proofreading, rich-message translation and
multi-attachment entries in shared-media tabs are separate follow-up work.
Bot callback, login, Web App and switch-inline button actions also remain
follow-up work for rich content.
No server or Premium permissions are changed.

Metadata traversal limits depth to 32, visits to 1,024 blocks and output to
32,768 UTF-16 units. Truncation preserves complete surrogate pairs and keeps
entity offsets within the resulting text.

## Validation

On 2026-10-14, full ARM64 and Lollipop ARM32 development assemblies and all
90 JVM tests passed with the Telegram X 1817 toolchain.

JVM coverage checks nested containers, all six media kinds, photo selection,
duplicate file IDs, thinking replacements, formulas/references/dates, diff
and spoiler entities, labeled links, tables, Unicode truncation, unknown
blocks and traversal bounds.

Device/account checks still required:

- Receive a full and an incomplete rich message from a supporting bot.
- Open it during generation; check `Thinking`, updates, scroll position,
  retry after a failed request, navigating away and source deletion.
- Check heading sizes 1–6, formulas, tables, lists, RTL, custom emoji,
  references, button links and large font sizes.
- Open photo/video/GIF, audio, voice and document blocks; verify spoilers and
  protected content, including after changing the chat's protection setting.
- Check chat-list, reply, pinned and notification previews with content
  previews enabled and disabled. Forward through Telegram and export text.
- Reopen ordinary text messages and web Instant Views to check regressions.

Real bot streaming, visual layout, playback and protection behavior have not
been verified on a device.
