/*
 * This file is a part of Ratogram X, a Telegram X fork.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.thunderdog.challegram.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.unsorted.Settings;

/** Changes the launcher activity alias without restarting the running client. */
public final class AppIconManager {
  private static final String SETTING_KEY = "ratogramx_app_icon";
  public static final String DEFAULT_ICON_ID = "original";

  public static final class Icon {
    public final String id;
    public final String displayName;
    public final String aliasSuffix;
    public final @DrawableRes int drawableRes;

    private Icon (String id, String displayName, String aliasSuffix, @DrawableRes int drawableRes) {
      this.id = id;
      this.displayName = displayName;
      this.aliasSuffix = aliasSuffix;
      this.drawableRes = drawableRes;
    }
  }

  private static final Icon[] ICONS = {
    new Icon("original", "Original", "Original", R.drawable.app_icon_original_full),
    new Icon("amoled", "AMOLED", "Amoled", R.drawable.app_icon_amoled_full),
    new Icon("lab", "Lab", "Lab", R.drawable.app_icon_lab_full),
    new Icon("malware", "Malware", "Malware", R.drawable.app_icon_malware_full),
    new Icon("not_you", "Not You", "NotYou", R.drawable.app_icon_not_you_full),
    new Icon("notele", "NoTele", "NoTele", R.drawable.app_icon_notele_full),
    new Icon("steam", "Steam", "Steam", R.drawable.app_icon_steam_full),
    new Icon("twins", "Twins", "Twins", R.drawable.app_icon_twins_full),
    new Icon("xue", "Xue", "Xue", R.drawable.app_icon_xue_full)
  };

  private AppIconManager () { }

  public static @NonNull Icon[] getIcons () {
    return ICONS.clone();
  }

  public static @NonNull String getSelectedIconId () {
    String iconId = Settings.instance().getString(SETTING_KEY, DEFAULT_ICON_ID);
    return findIcon(iconId) != null ? iconId : DEFAULT_ICON_ID;
  }

  public static @Nullable Icon findIcon (@Nullable String iconId) {
    if (iconId != null) {
      for (Icon icon : ICONS) {
        if (icon.id.equals(iconId)) {
          return icon;
        }
      }
    }
    return null;
  }

  public static boolean setSelectedIcon (@NonNull Context context, @NonNull String iconId) {
    Icon selectedIcon = findIcon(iconId);
    if (selectedIcon == null) {
      return false;
    }

    String previousIconId = getSelectedIconId();
    if (selectedIcon.id.equals(previousIconId)) {
      return true;
    }

    PackageManager packageManager = context.getPackageManager();
    ComponentName selectedComponent = componentName(context, selectedIcon);
    try {
      // Enable the destination first so the package always has a launcher entry.
      packageManager.setComponentEnabledSetting(
        selectedComponent,
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        PackageManager.DONT_KILL_APP
      );
      for (Icon icon : ICONS) {
        if (!icon.id.equals(selectedIcon.id)) {
          packageManager.setComponentEnabledSetting(
            componentName(context, icon),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
          );
        }
      }
      Settings.instance().putString(SETTING_KEY, selectedIcon.id);
      return true;
    } catch (RuntimeException error) {
      Icon previousIcon = findIcon(previousIconId);
      if (previousIcon != null) {
        try {
          packageManager.setComponentEnabledSetting(
            componentName(context, previousIcon),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
          );
        } catch (RuntimeException ignored) { }
      }
      Log.e("Unable to change launcher icon to %s", error, selectedIcon.id);
      return false;
    }
  }

  public static @NonNull ComponentName componentName (@NonNull Context context, @NonNull Icon icon) {
    return new ComponentName(
      context.getPackageName(),
      context.getPackageName() + ".launcher.Icon" + icon.aliasSuffix
    );
  }
}
