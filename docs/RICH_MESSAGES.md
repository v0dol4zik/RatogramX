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

The local Premium experiment enables Markdown rich messages in Saved Messages
for accounts without Telegram Premium. The source is sent as an ordinary
Telegram message with a single full-span `PreCode` entity whose language is
`ratogramx-rich-v1` (or `ratogramx-rich-v1-rtl`). Other Telegram clients show
the literal source, including `#` headings and `**` formatting. Ratogram X
recognizes this explicit marker only in the current account's Saved Messages
and renders a rich bubble and Instant View. Ordinary Markdown and unrelated
code blocks keep their normal appearance. TDLib's stored message is unchanged.

Source beyond Telegram's text limit is uploaded as a UTF-8
`RatogramX-rich-*.md` file, with a marked source excerpt in its caption.
Other clients can download and open the complete Markdown file. Existing
files are edited in place. If a text message grows beyond the limit, the
editor asks before uploading a new file and removing the original text
message; this changes its position and identity. The original is kept until
the replacement is confirmed. Source is limited to 65,536 UTF-16 units.

Photos are uploaded as ordinary, separately visible Saved Messages photos.
Standalone `![](attachment:tg_MESSAGE_ID)` references bind the source to those
messages. Ratogram resolves them within the same Saved Messages chat; no
local paths or external image URLs are taken from the source. Deleting the
main message keeps these separately saved photos. Removing a referenced photo
can prevent rich rendering; the cloud Markdown source remains available.

These messages use normal Telegram IDs, history paging, search and deletion.
Editing and deleting the source sync through Telegram. Existing messages
remain viewable and editable after local Premium is disabled. Parsing runs
on the background queue and coalesces requests for unchanged source. Each
account caches at most 16 parsed documents and 262,144 source UTF-16 units;
at most four source/attachment loads run concurrently. No periodic scan or
full-history rewrite is used for new messages. Markdown files download only
when their loaded bubble needs rendering, with a 256 KiB download bound.

Older device-only entries are still marked **Local** and retain their private
account/user-specific storage and images. Their menu offers **Sync to Saved
Messages**; saving an edit also transfers the entry. The local copy is removed
only after Telegram confirms sending and a private synchronization record is
written. Failed transfers retain local data. Existing records let a retry
reuse the confirmed cloud message. Legacy local IDs remain excluded from
TDLib paging, reading, selection, replies, reactions and forwarding.
An actual Telegram Premium account uses the Telegram path, including in
Saved Messages; enabling the local experiment does not redirect its sends.

AI creation/proofreading, rich-message translation and multi-attachment
entries in shared-media tabs are separate follow-up work.
Bot callback, login, Web App and switch-inline button actions also remain
follow-up work for rich content.
Local Premium does not grant server Premium permissions.
Legacy storage retains the exact Markdown source and image bindings. Existing
messages from the previous block editor remain readable; supported layouts
can be opened in the Markdown editor and transferred.

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

Saved Messages synchronization passed 48 targeted JVM checks, including nine
new protocol checks for literal source, marker scope/version, Unicode, file
bounds and photo references. Four Android 15 emulator cases passed for storage,
Markdown, native editor/preview/history and cloud-source rendering. The latter
also parsed 64 synthetic messages through the bounded queue and verified
in-place content updates and marker removal.

The separate opt-in cloud case passed with an authorized development account:
Telegram retained the literal source and marker; text and Markdown files
could be edited; a long text was replaced only after a confirmed file upload;
RTL and a migrated photo survived loading with a fresh renderer. The native
editor saved with local Premium disabled, and an open viewer followed an
external source edit and closed after deletion. All generated cloud fixtures
were removed. Send listeners are retained until success, failure or deletion,
since TDLib's listener registry uses weak references. The native preview was
visually inspected. Telegram Desktop UI and Android 13 runtime were not tested.

Device/account checks still required:

- Create and edit rich messages with a real Telegram Premium account in
  Saved Messages and another writable chat. The opt-in synchronization case
  sends ordinary Markdown source messages and does not grant or exercise
  server-side Premium permissions.
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
