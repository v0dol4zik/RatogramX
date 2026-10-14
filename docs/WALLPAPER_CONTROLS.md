# Wallpaper controls

The wallpaper preview uses a scrollable row of parameter buttons. Tapping a
slider parameter opens a second row; tapping it again hides that row.
The panel uses the current theme's surface and text colors. Drag the thumb or
tap the track to change a value; the value label has its own space beside the
slider. Percentage tracks are continuous, while blur has five positions
(off and four strengths). Dragging or scrolling the panel does not navigate back.
Standalone previews place the entire panel above the Apply button, including
when the system navigation bar adds a bottom inset.

- All supported backgrounds have local dimming from 0 to 80 percent.
- Photos, including local images, have four blur strengths, a tint color and
  intensity, and Normal, Multiply or Screen blending selected from a native menu.
- Patterns have intensity, inversion in dark themes, and editable fill colors.
  One color creates a solid fill, two a linear gradient, and three or four a
  multicolor gradient. Linear gradients support angles in 45-degree steps.
- Photos and patterns can move slightly as the device tilts. Motion stops
  when the controller loses focus or the activity pauses, and respects the
  system's reduced-motion setting.

**Revert changes** restores the wallpaper's parameters from when its preview
was opened, including colors, effects and motion. It is disabled until an
adjustment is made. Switching slider parameters preserves the controls' scroll
position; switching wallpapers retains a parameter only if the new type supports it.

The current wallpaper's changes are saved when leaving the wallpaper settings.
Opening a separate wallpaper object keeps changes in its preview until Apply.
Switching to another wallpaper replaces the unsaved parameter preview.

Dim, tint and blur strength use the existing per-account wallpaper storage,
separately for each theme wallpaper usage identifier. TDLib receives the
blur on/off flag and the pattern's fill, intensity, inversion and motion.
Local image wallpapers retain their parameters locally. Share links contain
the TDLib background type, so recipients do not receive dim, tint or the
exact blur strength.

The native blur decoder supports radii 1, 3, 7 and 15. Each blurred variant
has its own image cache key. Existing blurred wallpapers keep radius 3.
Inverted patterns use the fill inside the pattern mask with black elsewhere,
matching TDLib's inversion definition.

## Device checks

- Preview and apply photo, pattern, solid and gradient backgrounds. Leave
  settings, restart the app and switch accounts and light/dark themes.
- Try local photos, every blur strength, and tint modes at 0 and 80 percent.
- Check pattern intensity at 0 and 100 percent, inverted gradients and
  missing or partially loaded pattern images.
- Change parameters while a server request is pending, then select a
  different wallpaper; an earlier response must not replace the new choice.
- Check tilt motion, rotation, reduced motion, pause/resume and swipe-back.
- Check Russian labels, large font sizes and narrow or landscape screens.
- Move percentage sliders to both ends and tap intermediate values; the thumb
  and track must share their endpoints and stay clear of the value label.
- Switch parameters, collapse and reopen the slider, select each blend mode
  and revert changes. Swipe the controls in both directions without leaving
  the preview; swiping outside the controls should still navigate back.

JVM checks cover local-option bounds, native blur levels, and color/gradient
input validation. Rendering and sensor behavior still require device testing.
