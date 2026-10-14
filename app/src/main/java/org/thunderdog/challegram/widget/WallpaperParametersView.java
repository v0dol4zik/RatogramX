/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.mediaview.SliderView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ChatStyleChangeListener;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.TGBackground;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeChangeListener;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;
import moe.kirao.mgx.utils.WallpaperAppearance;

public class WallpaperParametersView extends LinearLayout implements Destroyable, ChatStyleChangeListener, ThemeChangeListener {
  private static final int DIM = 0, BLUR = 1, TINT = 2, INTENSITY = 3;
  private final Tdlib tdlib;
  private final LinearLayout chips;
  private final LinearLayout sliderRow;
  private final SliderView slider;
  private final ValueLabel valueLabel;
  private final int rowHeight;
  private final View[] parameterChips = new View[4];
  private View resetChip;
  private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private @Nullable WallpaperParametersListener listener;
  private @Nullable TGBackground background;
  private @Nullable TdApi.BackgroundType type;
  private WallpaperAppearance appearance = WallpaperAppearance.DEFAULT;
  private int selected = DIM;
  private int usageIdentifier;
  private boolean followWallpaperChanges;

  private final BoolAnimator shown = new BoolAnimator(0, (id, factor, fraction, callee) -> {
    setAlpha(factor);
    setVisibility(factor == 0f ? INVISIBLE : VISIBLE);
    if (listener != null) listener.onParametersViewScaleChanged(factor);
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L, true);

  public WallpaperParametersView (Context context, Tdlib tdlib) {
    super(context);
    this.tdlib = tdlib;
    this.usageIdentifier = Theme.getWallpaperIdentifier();
    setOrientation(VERTICAL);
    textPaint.setTypeface(Fonts.getRobotoRegular());
    textPaint.setTextSize(Screen.sp(14f));
    rowHeight = Math.max(Screen.dp(49f), (int) Math.ceil(textPaint.getFontSpacing()) + Screen.dp(20f));
    setBackgroundColor(Theme.getColor(ColorId.filling));

    HorizontalScrollView scroll = new HorizontalScrollView(context);
    scroll.setHorizontalScrollBarEnabled(false);
    scroll.setOverScrollMode(OVER_SCROLL_NEVER);
    chips = new LinearLayout(context);
    chips.setPadding(Screen.dp(8f), 0, Screen.dp(8f), 0);
    scroll.addView(chips, new ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
    addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));

    sliderRow = new LinearLayout(context);
    sliderRow.setPadding(Screen.dp(8f), 0, Screen.dp(8f), 0);
    slider = new SliderView(context);
    slider.setSlideEnabled(true, false);
    slider.setTapToSeekEnabled(true);
    slider.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
    slider.setForceBackgroundColorId(ColorId.sliderInactive);
    slider.setColorId(ColorId.sliderActive, false);
    sliderRow.addView(slider, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
    valueLabel = new ValueLabel(context);
    sliderRow.addView(valueLabel, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
    addView(sliderRow, new LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
    slider.setListener(new SliderView.Listener() {
      @Override public void onSetStateChanged (SliderView view, boolean setting) {
        if (!setting) view.animateValue(sliderValue() / (float) sliderMax());
      }
      @Override public boolean allowSliderChanges (SliderView view) { return background != null && selected >= 0; }
      @Override public void onValueChanged (SliderView view, float factor) {
        if (background == null || selected < 0) return;
        int value = Math.round(factor * sliderMax());
        if (value == sliderValue()) return;
        switch (selected) {
          case DIM: appearance = new WallpaperAppearance(value, appearance.blurRadius, appearance.tintColor, appearance.tintIntensity, appearance.tintBlend); break;
          case BLUR:
            appearance = new WallpaperAppearance(appearance.dim, WallpaperAppearance.blurRadiusForStep(value), appearance.tintColor, appearance.tintIntensity, appearance.tintBlend);
            type = TGBackground.makeBlurredBackgroundType(type, appearance.blurRadius > 0);
            break;
          case TINT: appearance = new WallpaperAppearance(appearance.dim, appearance.blurRadius, appearance.tintColor, value, appearance.tintBlend); break;
          case INTENSITY:
            if (!(type instanceof TdApi.BackgroundTypePattern)) return;
            TdApi.BackgroundTypePattern pattern = (TdApi.BackgroundTypePattern) type;
            type = new TdApi.BackgroundTypePattern(pattern.fill, value, pattern.isInverted, pattern.isMoving);
            break;
          default: return;
        }
        updateValueLabel();
        notifyChanged();
      }
    });
    ThemeManager.instance().addChatStyleListener(this);
    ThemeManager.instance().addThemeListener(this);
  }

  public void initWith (@Nullable TGBackground background, WallpaperParametersListener listener, boolean followWallpaperChanges) {
    this.listener = listener;
    this.followWallpaperChanges = followWallpaperChanges;
    updateBackground(background);
  }

  public void updateBackground (@Nullable TGBackground background) {
    slider.cancelTouch();
    this.background = background;
    this.type = background != null ? background.getType() : null;
    this.appearance = background != null ? background.getAppearance() : WallpaperAppearance.DEFAULT;
    if (((selected == BLUR || selected == TINT) && (background == null || !background.isWallpaper())) ||
        (selected == INTENSITY && (background == null || !background.isPattern()))) selected = DIM;
    rebuildChips();
    setParametersAvailability(background != null && !background.isEmpty() &&
      (background.isWallpaper() || background.isPattern() || background.isFill()), false);
  }

  public void setParametersAvailability (boolean available, boolean animated) {
    shown.setValue(available, animated);
  }

  public float getAvailabilityFactor () { return shown.getFloatValue(); }
  public int getPanelHeight () { return rowHeight * (selected >= 0 ? 2 : 1); }
  public int getUsageIdentifier () { return usageIdentifier; }
  public @Nullable TGBackground getOriginalBackground () { return background; }

  public @Nullable TGBackground getEditedBackground () {
    return background != null ? background.withParameters(tdlib, type, appearance) : null;
  }

  public boolean hasChanges () {
    return background != null && !TGBackground.compare(background, getEditedBackground(), false);
  }

  public void setFill (TdApi.BackgroundFill fill) {
    if (type instanceof TdApi.BackgroundTypePattern) {
      TdApi.BackgroundTypePattern pattern = (TdApi.BackgroundTypePattern) type;
      type = new TdApi.BackgroundTypePattern(fill, pattern.intensity, pattern.isInverted, pattern.isMoving);
    } else if (type instanceof TdApi.BackgroundTypeFill) {
      type = new TdApi.BackgroundTypeFill(fill);
    } else return;
    notifyChanged();
  }

  public void setTintColor (int color) {
    appearance = new WallpaperAppearance(appearance.dim, appearance.blurRadius, color,
      appearance.tintIntensity == 0 ? 25 : appearance.tintIntensity, appearance.tintBlend);
    selected = TINT;
    rebuildChips();
    notifyChanged();
  }

  public void setTintBlend (int blend) {
    if (background == null || !background.isWallpaper()) return;
    appearance = new WallpaperAppearance(appearance.dim, appearance.blurRadius, appearance.tintColor,
      appearance.tintIntensity, blend);
    rebuildChips();
    notifyChanged();
  }

  private void select (int parameter) {
    slider.cancelTouch();
    selected = selected == parameter ? -1 : parameter;
    updateSelection();
  }

  private void updateSelection () {
    for (int i = 0; i < parameterChips.length; i++) {
      if (parameterChips[i] != null) {
        parameterChips[i].setSelected(selected == i);
        parameterChips[i].invalidate();
      }
    }
    sliderRow.setVisibility(selected >= 0 ? VISIBLE : GONE);
    updateSlider();
    if (listener != null) listener.onParametersHeightChanged(getPanelHeight());
  }

  private void rebuildChips () {
    chips.removeAllViews();
    for (int i = 0; i < parameterChips.length; i++) parameterChips[i] = null;
    resetChip = null;
    if (background != null) {
      addParameterChip(R.string.WallpaperDim, DIM);
      if (background.isWallpaper()) {
        addParameterChip(R.string.ChatBackgroundBlur, BLUR);
        addParameterChip(R.string.WallpaperTint, TINT);
        addChip(R.string.WallpaperTintColor, false, () -> { if (listener != null) listener.onEditColors(true); });
        int[] labels = {R.string.WallpaperBlendNormal, R.string.WallpaperBlendMultiply, R.string.WallpaperBlendScreen};
        addChip(Lang.getString(R.string.WallpaperBlendMode, Lang.getString(labels[appearance.tintBlend])), false,
          () -> { if (listener != null) listener.onEditTintBlend(); });
      }
      if (background.isPattern()) {
        addParameterChip(R.string.WallpaperPatternIntensity, INTENSITY);
        if (Theme.isDark()) {
          addChip(R.string.WallpaperInverted, ((TdApi.BackgroundTypePattern) type).isInverted, () -> {
            TdApi.BackgroundTypePattern pattern = (TdApi.BackgroundTypePattern) type;
            type = new TdApi.BackgroundTypePattern(pattern.fill, pattern.intensity, !pattern.isInverted, pattern.isMoving);
            rebuildChips();
            notifyChanged();
          });
        }
      }
      if (background.isPattern() || background.isFill()) {
        addChip(R.string.WallpaperFillColors, false, () -> { if (listener != null) listener.onEditColors(false); });
      }
      if (background.isWallpaper() || background.isPattern()) {
        boolean moving = type instanceof TdApi.BackgroundTypeWallpaper ? ((TdApi.BackgroundTypeWallpaper) type).isMoving : ((TdApi.BackgroundTypePattern) type).isMoving;
        addChip(R.string.WallpaperMotion, moving, () -> {
          if (type instanceof TdApi.BackgroundTypeWallpaper) {
            TdApi.BackgroundTypeWallpaper wallpaper = (TdApi.BackgroundTypeWallpaper) type;
            type = new TdApi.BackgroundTypeWallpaper(wallpaper.isBlurred, !wallpaper.isMoving);
          } else {
            TdApi.BackgroundTypePattern pattern = (TdApi.BackgroundTypePattern) type;
            type = new TdApi.BackgroundTypePattern(pattern.fill, pattern.intensity, pattern.isInverted, !pattern.isMoving);
          }
          rebuildChips();
          notifyChanged();
        });
      }
      resetChip = addChip(R.string.WallpaperResetChanges, false, () -> {
        slider.cancelTouch();
        type = background.getType();
        appearance = background.getAppearance();
        rebuildChips();
        notifyChanged();
      });
    }
    updateSelection();
    updateResetChip();
  }

  private int sliderValue () {
    switch (selected) {
      case BLUR: return WallpaperAppearance.blurStep(appearance.blurRadius);
      case TINT: return appearance.tintIntensity;
      case INTENSITY: return type instanceof TdApi.BackgroundTypePattern ? ((TdApi.BackgroundTypePattern) type).intensity : 0;
      default: return appearance.dim;
    }
  }

  private int sliderMax () {
    return selected == BLUR ? 4 : selected == INTENSITY ? 100 : 80;
  }

  private void updateSlider () {
    // Percentage sliders need a continuous track, not dozens of rounded segments.
    slider.setValueCount(selected == BLUR ? 5 : 0);
    slider.animateValue(sliderValue() / (float) sliderMax());
    updateValueLabel();
  }

  private void updateValueLabel () {
    String label = selected == BLUR ? Lang.getString(R.string.WallpaperBlurLevel, sliderValue(), sliderMax()) : sliderValue() + "%";
    valueLabel.setContentDescription(label);
    int parameter = selected == BLUR ? R.string.ChatBackgroundBlur : selected == TINT ? R.string.WallpaperTint :
      selected == INTENSITY ? R.string.WallpaperPatternIntensity : R.string.WallpaperDim;
    slider.setContentDescription(Lang.getString(parameter) + ": " + label);
    valueLabel.label = label;
    valueLabel.invalidate();
  }

  private void notifyChanged () {
    updateResetChip();
    if (listener != null) listener.onParametersChanged(getEditedBackground());
  }

  private void updateResetChip () {
    if (resetChip != null) {
      boolean enabled = hasChanges();
      resetChip.setEnabled(enabled);
      resetChip.setAlpha(enabled ? 1f : .5f);
    }
  }

  private void addParameterChip (int labelId, int parameter) {
    parameterChips[parameter] = addChip(labelId, selected == parameter, () -> select(parameter));
  }

  private View addChip (int labelId, boolean checked, Runnable action) {
    return addChip(Lang.getString(labelId), checked, action);
  }

  private View addChip (String text, boolean checked, Runnable action) {
    View chip = new View(getContext()) {
      private final RectF rect = new RectF();
      @Override protected void onMeasure (int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(Screen.dp(28f) + (int) Math.ceil(textPaint.measureText(text)), widthSpec),
          resolveSize(rowHeight, heightSpec));
      }
      @Override protected void drawableStateChanged () {
        super.drawableStateChanged();
        invalidate();
      }
      @Override protected void onDraw (Canvas canvas) {
        rect.set(Screen.dp(3f), Screen.dp(8f), getWidth() - Screen.dp(3f), getHeight() - Screen.dp(8f));
        int backgroundColor = Theme.getColor(ColorId.background);
        if (isPressed()) backgroundColor = ColorUtils.fromToArgb(backgroundColor, Theme.getColor(ColorId.textLink), .15f);
        canvas.drawRoundRect(rect, Screen.dp(16f), Screen.dp(16f), Paints.fillingPaint(backgroundColor));
        if (isSelected()) canvas.drawRoundRect(rect, Screen.dp(16f), Screen.dp(16f), Paints.getProgressPaint(Theme.getColor(ColorId.textLink), Screen.dp(1.5f)));
        textPaint.setColor(Theme.getColor(ColorId.text));
        canvas.drawText(text, Screen.dp(14f), getHeight() / 2f - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint);
      }
    };
    chip.setSelected(checked);
    chip.setFocusable(true);
    chip.setContentDescription(text);
    chip.setOnClickListener(view -> action.run());
    chips.addView(chip, new LayoutParams(LayoutParams.WRAP_CONTENT, rowHeight));
    return chip;
  }

  private class ValueLabel extends View {
    private String label = "";
    ValueLabel (Context context) { super(context); }
    @Override protected void onMeasure (int widthSpec, int heightSpec) {
      int width = Math.max(Screen.dp(56f), (int) Math.ceil(Math.max(textPaint.measureText("100%"),
        textPaint.measureText(Lang.getString(R.string.WallpaperBlurLevel, 4, 4)))) + Screen.dp(16f));
      setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(rowHeight, heightSpec));
    }
    @Override protected void onDraw (Canvas canvas) {
      textPaint.setColor(Theme.getColor(ColorId.text));
      canvas.drawText(label, (getWidth() - textPaint.measureText(label)) / 2f,
        getHeight() / 2f - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint);
    }
  }

  @Override public void performDestroy () {
    ThemeManager.instance().removeChatStyleListener(this);
    ThemeManager.instance().removeThemeListener(this);
    listener = null;
    slider.setListener(null);
    slider.cancelTouch();
  }

  @Override public void onChatStyleChanged (Tdlib tdlib, int style) { }

  @Override public boolean needsTempUpdates () { return false; }
  @Override public void onThemeAutoNightModeChanged (int mode) { }
  @Override public void onThemeColorsChanged (boolean temporary, ColorState state) {
    setBackgroundColor(Theme.getColor(ColorId.filling));
    slider.invalidate();
    valueLabel.invalidate();
    for (int i = 0; i < chips.getChildCount(); i++) chips.getChildAt(i).invalidate();
  }

  @Override public void onThemeChanged (ThemeDelegate oldTheme, ThemeDelegate newTheme) {
    setBackgroundColor(Theme.getColor(ColorId.filling));
    if (followWallpaperChanges && usageIdentifier != Theme.getWallpaperIdentifier(newTheme)) {
      usageIdentifier = Theme.getWallpaperIdentifier(newTheme);
      updateBackground(tdlib.settings().getWallpaper(usageIdentifier));
    }
    rebuildChips();
  }

  @Override public void onThemePropertyChanged (int themeId, int propertyId, float value, boolean isDefault) {
    if (followWallpaperChanges && usageIdentifier != Theme.getWallpaperIdentifier(themeId)) {
      usageIdentifier = Theme.getWallpaperIdentifier(themeId);
      updateBackground(tdlib.settings().getWallpaper(usageIdentifier));
    }
  }

  @Override public void onChatWallpaperChanged (Tdlib tdlib, @Nullable TGBackground wallpaper, int usageIdentifier) {
    if (followWallpaperChanges && this.tdlib == tdlib && this.usageIdentifier == usageIdentifier) updateBackground(wallpaper);
  }

  public interface WallpaperParametersListener {
    default void onParametersChanged (@Nullable TGBackground background) { }
    default void onEditColors (boolean tint) { }
    default void onEditTintBlend () { }
    default void onParametersHeightChanged (int height) { }
    default void onParametersViewScaleChanged (float factor) { }
  }
}
