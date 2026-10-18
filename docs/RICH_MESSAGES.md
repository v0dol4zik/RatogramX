# Rich messages

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

The attachment panel has a separate **Rich message** action alongside File,
Gallery and Poll for Telegram Premium users in writable, non-secret chats.
It opens a single multiline Markdown field. The eye button opens native
Instant View preview; going back keeps the source, selection and scroll.
The editor's overflow menu contains Insert photo, RTL and syntax help.
Sending uses structured TDLib input and the existing chat send path;
editing rechecks Telegram Premium and message edit permissions. User accounts
do not send the bots-only Markdown source type.

Inline syntax uses Telegram X's native TDLib `ParseMarkdown` parser:

```text
**bold** __italic__ ~~strikethrough~~ ||spoiler|| `code`
[label](https://example.com)

# Heading (one to six # characters)

- List item
  - Nested item
1. Numbered item
- [x] Checked item

> Quote
```

Blank lines separate paragraphs. Fenced code uses three or more backticks
or tildes and an optional language. Backslash escapes punctuation outside
code. Insert photo adds a standalone `![](attachment:photo-1)` reference at
the cursor; text inside the square brackets becomes its caption. Removing
that line removes the photo from the message. Photos inside lists or quotes,
multiline photo captions and remote image URLs are not supported by the editor.

Unchanged supported server blocks retain their original styles and list
metadata. Unsupported block types or text styles show an explanation instead
of opening a lossy editor. The ordinary text composer is unchanged.

The local Premium experiment enables device-only creation in Saved Messages
for accounts without Telegram Premium. These entries are explicitly marked
**Local**, stored in private account/user-specific storage, and have private
copies of their images. They appear alongside the loaded Saved Messages
history and support local edit, copy and deletion. They survive restarting
the app and disabling the experiment. Local identities are excluded from
TDLib message IDs, cloud paging, reading, selection, replies, reactions and
forwarding. They are not uploaded, synced or included in cloud search.
An actual Telegram Premium account uses the Telegram path, including in
Saved Messages; enabling the local experiment does not redirect its sends.

AI creation/proofreading, rich-message translation and multi-attachment
entries in shared-media tabs are separate follow-up work.
Bot callback, login, Web App and switch-inline button actions also remain
follow-up work for rich content.
Local Premium does not grant server Premium permissions.
Local storage retains the exact Markdown source and image bindings. Existing
messages from the previous block editor remain readable; supported layouts
can be opened in the Markdown editor.

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

On 2026-10-18, 31 targeted JVM checks passed for editable documents,
Premium/local routing, persistence encoding, rich-text previews and GitHub
release selection. ARM64 development and instrumentation APKs assembled
successfully. Android 15 emulator fixtures verified private storage,
account/user isolation, local editing/deletion, private image copies,
native editor and Instant View photo preview, and history visibility after
disabling local Premium. A separate process restart with networking disabled
verified that the local message and its image survived.

The Markdown editor revision passed 39 targeted JVM checks covering block
structure, nesting, Unicode entities, bounds, photo bindings, legacy encoding
and Premium/local routing. Android 15 fixtures verified native inline parsing,
formatted persistence, storage migration, LTR/RTL attachment routing,
creation and editing through the one-field editor, Instant View photo preview,
and source/selection/scroll preservation. A separate offline process restart
retained the Markdown source, inline formatting and private photo.

Device/account checks still required:

- Create and edit rich messages with a real Telegram Premium account in
  Saved Messages and another writable chat. Emulator fixtures do not send
  real messages or grant server-side Premium permissions.
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

Real bot streaming, playback and protection behavior with live rich messages
have not been verified on a device.
