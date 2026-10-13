package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ThemeInfo;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.UI;

public final class BasicThemeEditorController extends ThemeListController {
  private final ThemeInfo theme;
  private final @Nullable SettingsThemeController parent;
  private @Nullable Runnable after;

  public BasicThemeEditorController (Context context, Tdlib tdlib, ThemeInfo theme, @Nullable Runnable after,
                                     @Nullable SettingsThemeController parent) {
    super(context, tdlib);
    this.theme = theme;
    this.after = after;
    this.parent = parent;
    setArguments(new Args(theme, R.id.theme_category_basic));
  }

  @Override public int getId () {
    return R.id.controller_basicThemeEditor;
  }

  @Override public CharSequence getName () {
    return theme.getName();
  }

  @Override protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override protected int getMenuId () {
    return R.id.menu_more;
  }

  @Override public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_more) header.addMoreButton(menu, this);
    else super.fillMenuItems(id, header, menu);
  }

  @Override public void onMenuItemPressed (int id, View view) {
    if (id != R.id.menu_btn_more) {
      super.onMenuItemPressed(id, view);
      return;
    }
    showOptions(new int[] {R.id.btn_basicThemeAdvanced}, new String[] {Lang.getString(R.string.BasicThemeAdvanced)}, (item, option) -> {
      finishColorEditing();
      ThemeController editor = new ThemeController(context(), tdlib);
      editor.setArguments(new ThemeController.Args(theme, null, parent));
      navigateTo(editor);
      return true;
    });
  }

  @Override public void onFocus () {
    super.onFocus();
    refreshThemeHeader();
    if (after != null) {
      after.run();
      after = null;
    }
    ThemeManager.instance().changeGlobalTheme(tdlib, theme.getTheme(), true, null);
    UI.setSoftInputMode(context(), WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
  }

  @Override public void destroy () {
    super.destroy();
    context().checkNightMode();
  }
}
