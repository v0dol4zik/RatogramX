/*
 * This file is a part of Ratogram X, a Telegram X fork.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.AppIconManager;

final class AppIconSelectorAdapter extends RecyclerView.Adapter<AppIconSelectorAdapter.Holder> {
  interface Listener {
    void onIconSelected (@NonNull AppIconManager.Icon icon);
  }

  private final Context context;
  private final ViewController<?> themeProvider;
  private final Listener listener;
  private final AppIconManager.Icon[] icons = AppIconManager.getIcons();
  private String selectedIconId = AppIconManager.getSelectedIconId();

  AppIconSelectorAdapter (Context context, ViewController<?> themeProvider, Listener listener) {
    this.context = context;
    this.themeProvider = themeProvider;
    this.listener = listener;
    setHasStableIds(true);
  }

  @Override
  public long getItemId (int position) {
    return icons[position].id.hashCode();
  }

  @Override
  public int getItemCount () {
    return icons.length;
  }

  @NonNull
  @Override
  public Holder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
    IconView view = new IconView(context);
    view.setLayoutParams(new RecyclerView.LayoutParams(Screen.dp(88f), Screen.dp(95f)));
    themeProvider.addThemeInvalidateListener(view);
    return new Holder(view);
  }

  @Override
  public void onBindViewHolder (@NonNull Holder holder, int position) {
    AppIconManager.Icon icon = icons[position];
    holder.view.setIcon(icon, icon.id.equals(selectedIconId));
    holder.view.setOnClickListener(v -> listener.onIconSelected(icon));
  }

  void setSelectedIcon (@NonNull String iconId) {
    if (selectedIconId.equals(iconId)) {
      return;
    }
    int previousPosition = indexOf(selectedIconId);
    selectedIconId = iconId;
    int newPosition = indexOf(iconId);
    if (previousPosition >= 0) {
      notifyItemChanged(previousPosition);
    }
    if (newPosition >= 0) {
      notifyItemChanged(newPosition);
    }
  }

  private int indexOf (String iconId) {
    for (int i = 0; i < icons.length; i++) {
      if (icons[i].id.equals(iconId)) {
        return i;
      }
    }
    return -1;
  }

  static final class Holder extends RecyclerView.ViewHolder {
    final IconView view;

    Holder (IconView view) {
      super(view);
      this.view = view;
    }
  }

  private static final class IconView extends View {
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Path iconClipPath = new Path();
    private AppIconManager.Icon icon;
    private Drawable drawable;
    private boolean selected;

    IconView (Context context) {
      super(context);
      Views.setClickable(this);
      RippleSupport.setTransparentSelector(this);
    }

    void setIcon (AppIconManager.Icon icon, boolean selected) {
      this.icon = icon;
      this.selected = selected;
      this.drawable = Drawables.load(getResources(), icon.drawableRes).mutate();
      setSelected(selected);
      setContentDescription(icon.displayName);
      invalidate();
    }

    @Override
    protected void onDraw (Canvas canvas) {
      super.onDraw(canvas);
      if (icon == null || drawable == null) {
        return;
      }

      int iconSize = Screen.dp(56f);
      int iconTop = Screen.dp(7f);
      int iconLeft = (getWidth() - iconSize) / 2;
      int iconRight = iconLeft + iconSize;
      int iconBottom = iconTop + iconSize;
      float iconCenterX = (iconLeft + iconRight) / 2f;
      float iconCenterY = (iconTop + iconBottom) / 2f;
      float iconRadius = iconSize / 2f;

      if (selected) {
        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setStrokeWidth(Screen.dp(2f));
        selectionPaint.setColor(Theme.getColor(ColorId.iconActive));
        canvas.drawCircle(iconCenterX, iconCenterY, iconRadius + Screen.dp(4f), selectionPaint);
      }

      int saveCount = canvas.save();
      iconClipPath.rewind();
      iconClipPath.addCircle(iconCenterX, iconCenterY, iconRadius, Path.Direction.CW);
      canvas.clipPath(iconClipPath);
      int artworkZoom = Screen.dp(2f);
      drawable.setBounds(
        iconLeft - artworkZoom,
        iconTop - artworkZoom,
        iconRight + artworkZoom,
        iconBottom + artworkZoom
      );
      drawable.draw(canvas);
      canvas.restoreToCount(saveCount);

      int textColor = selected ? Theme.getColor(ColorId.iconActive) : Theme.textDecentColor();
      TextPaint textPaint = Paints.getRegularTextPaint(12f, textColor);
      float maxTextWidth = getWidth() - Screen.dp(8f);
      CharSequence label = TextUtils.ellipsize(icon.displayName, textPaint, maxTextWidth, TextUtils.TruncateAt.END);
      float textWidth = textPaint.measureText(label, 0, label.length());
      canvas.drawText(label, 0, label.length(), (getWidth() - textWidth) / 2f, Screen.dp(84f), textPaint);
    }
  }
}
