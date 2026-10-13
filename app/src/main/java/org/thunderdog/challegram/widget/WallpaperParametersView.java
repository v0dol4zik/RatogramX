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
import android.view.Gravity;
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
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;
import moe.kirao.mgx.utils.WallpaperAppearance;

public class WallpaperParametersView extends LinearLayout implements Destroyable, ChatStyleChangeListener, ThemeChangeListener {
  private static final int DIM = 0, BLUR = 1, TINT = 2, INTENSITY = 3;
  private final Tdlib tdlib;
  private final LinearLayout chips;
  private final FrameLayoutFix sliderRow;
  private final SliderView slider;
  private final ValueLabel valueLabel;
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

    HorizontalScrollView scroll = new HorizontalScrollView(context);
    scroll.setHorizontalScrollBarEnabled(false);
    chips = new LinearLayout(context);
    chips.setPadding(Screen.dp(8f), 0, Screen.dp(8f), 0);
    scroll.addView(chips, new ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
    addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, Screen.dp(49f)));

    sliderRow = new FrameLayoutFix(context);
    slider = new SliderView(context);
    slider.setSlideEnabled(true, false);
    slider.setPadding(Screen.dp(20f), 0, Screen.dp(72f), 0);
    slider.setForceBackgroundColorId(ColorId.sliderInactive);
    slider.setColorId(ColorId.sliderActive, false);
    sliderRow.addView(slider, FrameLayoutFix.newParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    valueLabel = new ValueLabel(context);
    sliderRow.addView(valueLabel, FrameLayoutFix.newParams(Screen.dp(64f), LayoutParams.MATCH_PARENT, Gravity.RIGHT));
    addView(sliderRow, new LayoutParams(LayoutParams.MATCH_PARENT, Screen.dp(49f)));
    slider.setListener(new SliderView.Listener() {
      @Override public void onSetStateChanged (SliderView view, boolean setting) { }
      @Override public boolean allowSliderChanges (SliderView view) { return background != null; }
      @Override public void onValueChanged (SliderView view, float factor) {
        int value = Math.round(factor * (selected == BLUR ? 4 : selected == INTENSITY ? 100 : 80));
        switch (selected) {
          case DIM: appearance = new WallpaperAppearance(value, appearance.blurRadius, appearance.tintColor, appearance.tintIntensity, appearance.tintBlend); break;
          case BLUR:
            appearance = new WallpaperAppearance(appearance.dim, WallpaperAppearance.blurRadiusForStep(value), appearance.tintColor, appearance.tintIntensity, appearance.tintBlend);
            type = TGBackground.makeBlurredBackgroundType(type, appearance.blurRadius > 0);
            break;
          case TINT: appearance = new WallpaperAppearance(appearance.dim, appearance.blurRadius, appearance.tintColor, value, appearance.tintBlend); break;
          case INTENSITY:
            TdApi.BackgroundTypePattern pattern = (TdApi.BackgroundTypePattern) type;
            type = new TdApi.BackgroundTypePattern(pattern.fill, value, pattern.isInverted, pattern.isMoving);
            break;
          default: return;
        }
        updateSlider();
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
    this.background = background;
    this.type = background != null ? background.getType() : null;
    this.appearance = background != null ? background.getAppearance() : WallpaperAppearance.DEFAULT;
    if (selected == BLUR || selected == TINT || selected == INTENSITY) selected = DIM;
    rebuildChips();
    setParametersAvailability(background != null && !background.isEmpty() &&
      (background.isWallpaper() || background.isPattern() || background.isFill()), false);
  }

  public void setParametersAvailability (boolean available, boolean animated) {
    shown.setValue(available, animated);
  }

  public float getAvailabilityFactor () { return shown.getFloatValue(); }
  public int getPanelHeight () { return Screen.dp(selected >= 0 ? 98f : 49f); }
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

  private void select (int parameter) {
    selected = selected == parameter ? -1 : parameter;
    rebuildChips();
  }

  private void rebuildChips () {
    chips.removeAllViews();
    if (background != null) {
      addChip(R.string.WallpaperDim, selected == DIM, () -> select(DIM));
      if (background.isWallpaper()) {
        addChip(R.string.ChatBackgroundBlur, selected == BLUR, () -> select(BLUR));
        addChip(R.string.WallpaperTint, selected == TINT, () -> select(TINT));
        addChip(R.string.WallpaperTintColor, false, () -> { if (listener != null) listener.onEditColors(true); });
        int[] labels = {R.string.WallpaperBlendNormal, R.string.WallpaperBlendMultiply, R.string.WallpaperBlendScreen};
        addChip(labels[appearance.tintBlend], false, () -> {
          appearance = new WallpaperAppearance(appearance.dim, appearance.blurRadius, appearance.tintColor,
            appearance.tintIntensity, (appearance.tintBlend + 1) % 3);
          rebuildChips();
          notifyChanged();
        });
      }
      if (background.isPattern()) {
        addChip(R.string.WallpaperPatternIntensity, selected == INTENSITY, () -> select(INTENSITY));
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
    }
    sliderRow.setVisibility(selected >= 0 ? VISIBLE : GONE);
    updateSlider();
    if (listener != null) listener.onParametersHeightChanged(getPanelHeight());
  }

  private void updateSlider () {
    int value, max;
    switch (selected) {
      case BLUR: value = WallpaperAppearance.blurStep(appearance.blurRadius); max = 4; break;
      case TINT: value = appearance.tintIntensity; max = 80; break;
      case INTENSITY: value = type instanceof TdApi.BackgroundTypePattern ? ((TdApi.BackgroundTypePattern) type).intensity : 0; max = 100; break;
      default: value = appearance.dim; max = 80; break;
    }
    slider.setValueCount(max + 1);
    slider.setValue(value / (float) max);
    String label = selected == BLUR ? Lang.getString(R.string.WallpaperBlurLevel, value, max) : value + "%";
    valueLabel.setContentDescription(label);
    slider.setContentDescription(label);
    valueLabel.label = label;
    valueLabel.invalidate();
  }

  private void notifyChanged () {
    if (listener != null) listener.onParametersChanged(getEditedBackground());
  }

  private void addChip (int labelId, boolean checked, Runnable action) {
    String text = Lang.getString(labelId);
    View chip = new View(getContext()) {
      private final RectF rect = new RectF();
      @Override protected void onMeasure (int widthSpec, int heightSpec) {
        setMeasuredDimension(Screen.dp(28f) + (int) textPaint.measureText(text), Screen.dp(49f));
      }
      @Override protected void onDraw (Canvas canvas) {
        rect.set(Screen.dp(3f), Screen.dp(8f), getWidth() - Screen.dp(3f), getHeight() - Screen.dp(8f));
        canvas.drawRoundRect(rect, Screen.dp(16f), Screen.dp(16f), Paints.fillingPaint(Theme.getColor(ColorId.previewBackground)));
        if (checked) canvas.drawRoundRect(rect, Screen.dp(16f), Screen.dp(16f), Paints.getProgressPaint(Theme.getColor(ColorId.textLink), Screen.dp(1.5f)));
        textPaint.setColor(Theme.getColor(ColorId.text));
        canvas.drawText(text, Screen.dp(14f), getHeight() / 2f - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint);
      }
    };
    chip.setSelected(checked);
    chip.setFocusable(true);
    chip.setContentDescription(text);
    chip.setOnClickListener(view -> action.run());
    chips.addView(chip, new LayoutParams(LayoutParams.WRAP_CONTENT, Screen.dp(49f)));
  }

  private class ValueLabel extends View {
    private String label = "";
    ValueLabel (Context context) { super(context); }
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
  }

  @Override public void onChatStyleChanged (Tdlib tdlib, int style) { }

  @Override public boolean needsTempUpdates () { return false; }
  @Override public void onThemeAutoNightModeChanged (int mode) { }
  @Override public void onThemeColorsChanged (boolean temporary, ColorState state) { invalidate(); }

  @Override public void onThemeChanged (ThemeDelegate oldTheme, ThemeDelegate newTheme) {
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
    default void onParametersHeightChanged (int height) { }
    default void onParametersViewScaleChanged (float factor) { }
  }
}
