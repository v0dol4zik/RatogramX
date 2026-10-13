# Basic theme editor

Editing a custom theme opens a compact editor with twelve groups: accent,
header, filling, background, separators, text, secondary text and icons,
chat background, outgoing and incoming bubbles, bubble text, and badges.
The existing Telegram X color picker, color history and theme storage are
used. Changes preview immediately and are saved when color editing finishes.

The accent group updates tinted and main colors together, using darker,
muted and pale tones for their different roles. Header, bubble, badge and
surface changes also adjust their foregrounds for readability. Text colors
can still be edited manually. A translucent accent keeps the parent colors'
role-specific alpha.

Long-press a group to restore its colors to the parent theme. Resetting a
group also removes the foreground overrides generated for that group; it
does not erase color history. Groups overlap, so changing an accent or
surface can replace a previous manual change to an affected color.

The menu opens the full Telegram X editor for theme names, properties and
individual colors. Both editors modify the same theme and exported `.tgx-theme`
files continue to work without a separate Ratogram format.

## Device checks

- Create a theme, edit each group, leave and reopen it, and export/import it.
- Change dark surfaces to light ones and verify header, settings, bubbles and
  badges remain readable in both chat styles.
- Drag the color picker, use history and reset a group after a manual edit.
- Open the advanced editor, rename the theme, and return to the basic editor.
- Check the interface in English and Russian and with a large font scale.

JVM tests cover color composition, contrast, grouped resets and batch cache
invalidation. Controller navigation and the live color picker still need a
device check.
