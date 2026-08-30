/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeInvalidateListener;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;

/** Compact, theme-aware, horizontally scrollable search categories. */
public class SearchTabsView extends HorizontalScrollView implements ThemeInvalidateListener {
  public interface Listener {
    void onSearchTabSelected (int index);
  }

  public static final int CONTENT_HEIGHT_DP = 46;
  public static final int DIVIDER_HEIGHT_DP = 4;
  public static final int HEIGHT_DP = CONTENT_HEIGHT_DP + DIVIDER_HEIGHT_DP;

  private final LinearLayout contentView;
  private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint separatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private TabView[] tabs = new TabView[0];
  private Listener listener;
  private int selectedIndex;

  public SearchTabsView (@NonNull Context context) {
    super(context);
    setHorizontalScrollBarEnabled(false);
    setVerticalScrollBarEnabled(false);
    setOverScrollMode(OVER_SCROLL_NEVER);
    setFillViewport(false);
    setWillNotDraw(false);

    contentView = new LinearLayout(context);
    contentView.setOrientation(LinearLayout.HORIZONTAL);
    contentView.setGravity(Gravity.CENTER_VERTICAL);
    contentView.setPadding(Screen.dp(8f), 0, Screen.dp(8f), 0);
    addView(contentView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(CONTENT_HEIGHT_DP), Gravity.TOP));
    checkRtl();
    updateColors();
  }

  public void setListener (Listener listener) {
    this.listener = listener;
  }

  public void setTitles (@NonNull CharSequence[] titles) {
    contentView.removeAllViews();
    tabs = new TabView[titles.length];
    for (int i = 0; i < titles.length; i++) {
      final int index = i;
      TabView tab = new TabView(getContext());
      tab.setText(titles[i]);
      tab.setContentDescription(titles[i]);
      tab.setOnClickListener(v -> {
        if (listener != null) {
          listener.onSearchTabSelected(index);
        }
      });
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
      if (i + 1 < titles.length) {
        params.setMarginEnd(Screen.dp(4f));
      }
      contentView.addView(tab, params);
      tabs[i] = tab;
    }
    selectedIndex = Math.max(0, Math.min(selectedIndex, tabs.length - 1));
    updateSelection(false);
  }

  public void setSelectedIndex (int index, boolean animated) {
    if (tabs.length == 0) {
      selectedIndex = 0;
      return;
    }
    index = Math.max(0, Math.min(index, tabs.length - 1));
    if (selectedIndex != index) {
      selectedIndex = index;
      updateSelection(animated);
    } else {
      scrollSelectionIntoView(animated);
    }
  }

  public int getSelectedIndex () {
    return selectedIndex;
  }

  public void checkRtl () {
    int direction = Lang.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR;
    setLayoutDirection(direction);
    contentView.setLayoutDirection(direction);
  }

  private void updateSelection (boolean animated) {
    for (int i = 0; i < tabs.length; i++) {
      tabs[i].setTabSelected(i == selectedIndex);
    }
    scrollSelectionIntoView(animated);
  }

  private void scrollSelectionIntoView (boolean animated) {
    if (selectedIndex < 0 || selectedIndex >= tabs.length) {
      return;
    }
    post(() -> {
      TabView tab = tabs[selectedIndex];
      int target = tab.getLeft() + tab.getWidth() / 2 - getWidth() / 2;
      int maxScroll = Math.max(0, contentView.getWidth() - getWidth());
      target = Math.max(0, Math.min(target, maxScroll));
      if (animated) {
        smoothScrollTo(target, 0);
      } else {
        scrollTo(target, 0);
      }
    });
  }

  private void updateColors () {
    setBackgroundColor(Theme.backgroundColor());
    dividerPaint.setColor(Theme.fillingColor());
    separatorPaint.setColor(Theme.separatorColor());
    for (TabView tab : tabs) {
      tab.updateColors();
    }
    invalidate();
  }

  @Override
  protected void dispatchDraw (Canvas canvas) {
    super.dispatchDraw(canvas);
    int viewportLeft = getScrollX();
    int dividerTop = Screen.dp(CONTENT_HEIGHT_DP);
    canvas.drawRect(viewportLeft, dividerTop, viewportLeft + getWidth(), getHeight(), dividerPaint);
    canvas.drawRect(viewportLeft, dividerTop, viewportLeft + getWidth(), dividerTop + Screen.dp(1f), separatorPaint);
  }

  @Override
  public void onThemeInvalidate (boolean isTempUpdate) {
    updateColors();
  }

  private static class TabView extends TextView {
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean tabSelected;

    TabView (Context context) {
      super(context);
      setGravity(Gravity.CENTER);
      setSingleLine(true);
      setTypeface(Fonts.getRobotoMedium());
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
      setPadding(Screen.dp(8f), 0, Screen.dp(8f), 0);
      setMinWidth(Screen.dp(48f));
      RippleSupport.setTransparentSelector(this);
    }

    void setTabSelected (boolean selected) {
      if (tabSelected != selected) {
        tabSelected = selected;
        setSelected(selected);
        updateColors();
      } else {
        updateColors();
      }
    }

    void updateColors () {
      setTextColor(tabSelected ? Theme.textAccent2Color() : Theme.textDecent2Color());
      selectionPaint.setColor(Theme.textAccent2Color());
      invalidate();
    }

    @Override
    protected void onDraw (Canvas canvas) {
      super.onDraw(canvas);
      if (tabSelected) {
        canvas.drawRect(getPaddingLeft(), getHeight() - Screen.dp(2f), getWidth() - getPaddingRight(), getHeight(), selectionPaint);
      }
    }
  }
}
