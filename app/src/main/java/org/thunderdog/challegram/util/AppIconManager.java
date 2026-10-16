/*
 * This file is a part of Ratogram X, a Telegram X fork.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.thunderdog.challegram.util;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.List;

/** Changes the launcher activity alias without restarting the running client. */
public final class AppIconManager {
  private static final String SETTING_KEY = "ratogramx_app_icon";
  public static final String DEFAULT_ICON_ID = "original";

  public static final class Icon {
    public final String id;
    public final String displayName;
    public final String aliasSuffix;
    public final @DrawableRes int drawableRes;
    public final @DrawableRes int launcherIconRes;

    private Icon (String id, String displayName, String aliasSuffix, @DrawableRes int drawableRes, @DrawableRes int launcherIconRes) {
      this.id = id;
      this.displayName = displayName;
      this.aliasSuffix = aliasSuffix;
      this.drawableRes = drawableRes;
      this.launcherIconRes = launcherIconRes;
    }
  }

  private static final Icon[] ICONS = {
    new Icon("original", "Original", "Original", R.drawable.app_icon_original_full, R.mipmap.app_icon_original),
    new Icon("amoled", "AMOLED", "Amoled", R.drawable.app_icon_amoled_full, R.mipmap.app_icon_amoled),
    new Icon("lab", "Lab", "Lab", R.drawable.app_icon_lab_full, R.mipmap.app_icon_lab),
    new Icon("malware", "Malware", "Malware", R.drawable.app_icon_malware_full, R.mipmap.app_icon_malware),
    new Icon("not_you", "Not You", "NotYou", R.drawable.app_icon_not_you_full, R.mipmap.app_icon_not_you),
    new Icon("notele", "NoTele", "NoTele", R.drawable.app_icon_notele_full, R.mipmap.app_icon_notele),
    new Icon("steam", "Steam", "Steam", R.drawable.app_icon_steam_full, R.mipmap.app_icon_steam),
    new Icon("twins", "Twins", "Twins", R.drawable.app_icon_twins_full, R.mipmap.app_icon_twins),
    new Icon("xue", "Xue", "Xue", R.drawable.app_icon_xue_full, R.mipmap.app_icon_xue)
  };

  private static final long DEFERRED_CHANGE_DELAY = 1500;

  private static @Nullable String pendingIconId;
  private static @Nullable UI.StateListener pendingListener;
  private static @Nullable Bitmap notificationIcon;
  private static @Nullable String notificationIconId;
  private static @Nullable Bitmap notificationMask;
  private static @Nullable String notificationMaskId;

  private AppIconManager () { }

  public static @NonNull Icon[] getIcons () {
    return ICONS.clone();
  }

  public static @NonNull String getSelectedIconId () {
    return pendingIconId != null ? pendingIconId : getAppliedIconId();
  }

  private static @NonNull String getAppliedIconId () {
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

    Context appContext = context.getApplicationContext();
    if (selectedIcon.id.equals(getAppliedIconId())) {
      cancelPendingIcon();
      return true;
    }

    if (isTaskStartedByAlias(appContext, selectedIcon)) {
      // Disabling that alias now would close the app, so wait until it's in background.
      if (pendingIconId == null) {
        UI.showToast(R.string.AppIconChangeDeferred, Toast.LENGTH_LONG);
      }
      pendingIconId = selectedIcon.id;
      if (pendingListener == null) {
        pendingListener = newState -> {
          if (newState != UI.State.RESUMED) {
            UI.post(() -> {
              if (UI.getUiState() != UI.State.RESUMED) {
                applyPendingIcon(appContext);
              }
            }, DEFERRED_CHANGE_DELAY);
          }
        };
        UI.addStateListener(pendingListener);
      }
      return true;
    }

    cancelPendingIcon();
    return applyIcon(appContext, selectedIcon);
  }

  private static void applyPendingIcon (@NonNull Context context) {
    Icon icon = findIcon(pendingIconId);
    cancelPendingIcon();
    if (icon != null) {
      applyIcon(context, icon);
    }
  }

  private static void cancelPendingIcon () {
    pendingIconId = null;
    if (pendingListener != null) {
      UI.removeStateListener(pendingListener);
      pendingListener = null;
    }
  }

  private static boolean applyIcon (@NonNull Context context, @NonNull Icon selectedIcon) {
    String previousIconId = getAppliedIconId();
    PackageManager packageManager = context.getPackageManager();
    int[] previousStates = new int[ICONS.length];
    boolean switching = false;
    try {
      for (int i = 0; i < ICONS.length; i++) {
        previousStates[i] = packageManager.getComponentEnabledSetting(componentName(context, ICONS[i]));
      }
      switching = true;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // A single update, so the launcher never shows two icons at once.
        List<PackageManager.ComponentEnabledSetting> settings = new ArrayList<>(ICONS.length);
        for (Icon icon : ICONS) {
          settings.add(new PackageManager.ComponentEnabledSetting(
            componentName(context, icon),
            icon == selectedIcon ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
          ));
        }
        packageManager.setComponentEnabledSettings(settings);
      } else {
        // Enable the destination first so the package always has a launcher entry.
        setComponentEnabled(packageManager, componentName(context, selectedIcon), true);
        for (Icon icon : ICONS) {
          if (icon != selectedIcon) {
            setComponentEnabled(packageManager, componentName(context, icon), false);
          }
        }
      }
      Settings.instance().putString(SETTING_KEY, selectedIcon.id);
    } catch (RuntimeException error) {
      Icon previousIcon = findIcon(previousIconId);
      if (switching && previousIcon != null) {
        try {
          setComponentEnabled(packageManager, componentName(context, previousIcon), true);
          for (int i = 0; i < ICONS.length; i++) {
            if (ICONS[i] != previousIcon) {
              packageManager.setComponentEnabledSetting(componentName(context, ICONS[i]),
                previousStates[i], PackageManager.DONT_KILL_APP);
            }
          }
        } catch (RuntimeException ignored) { }
      }
      Log.e("Unable to change launcher icon to %s", error, selectedIcon.id);
      return false;
    }

    BaseActivity activity = UI.getUiContext();
    if (activity != null) {
      updateTaskDescription(activity);
    }
    for (org.thunderdog.challegram.telegram.TdlibAccount account : org.thunderdog.challegram.telegram.TdlibManager.instance()) {
      if (account.hasTdlib(false)) account.tdlib().notifications().onUpdateNotifications((org.drinkless.tdlib.TdApi.NotificationSettingsScope) null);
    }
    return true;
  }

  public static synchronized @Nullable Bitmap notificationIcon (@NonNull Context context) {
    String id = getAppliedIconId();
    if (notificationIcon == null || !id.equals(notificationIconId)) {
      Icon icon = findIcon(id);
      Drawable drawable = icon != null ? ContextCompat.getDrawable(context, icon.launcherIconRes) : null;
      if (drawable == null) return null;
      int size = org.thunderdog.challegram.tool.Screen.dp(64f);
      notificationIcon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
      drawable.setBounds(0, 0, size, size);
      drawable.draw(new Canvas(notificationIcon));
      notificationIconId = id;
    }
    return notificationIcon;
  }

  public static void setNotificationSmallIcon (androidx.core.app.NotificationCompat.Builder builder, Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      builder.setSmallIcon(R.drawable.ratogram_notification_24);
      return;
    }
    Bitmap mask = notificationMask(context);
    if (mask != null) builder.setSmallIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(mask));
    else builder.setSmallIcon(R.drawable.ratogram_notification_24);
  }

  private static synchronized @Nullable Bitmap notificationMask (Context context) {
    String id = getAppliedIconId();
    if (notificationMask != null && id.equals(notificationMaskId)) return notificationMask;
    final int resource;
    switch (id) {
      case "amoled": resource = R.drawable.app_icon_amoled_foreground; break;
      case "lab": resource = R.drawable.app_icon_lab_foreground; break;
      case "malware": resource = R.drawable.app_icon_malware_foreground; break;
      case "not_you": resource = R.drawable.app_icon_not_you_foreground; break;
      case "notele": resource = R.drawable.app_icon_notele_foreground; break;
      case "steam": resource = R.drawable.app_icon_steam_foreground; break;
      case "twins": resource = R.drawable.app_icon_twins_foreground; break;
      case "xue": resource = R.drawable.app_icon_xue_foreground; break;
      default: resource = R.drawable.app_icon_original_foreground; break;
    }
    Drawable drawable = ContextCompat.getDrawable(context, resource);
    if (drawable == null) return null;
    Bitmap source = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
    drawable.setBounds(0, 0, 128, 128);
    drawable.draw(new Canvas(source));
    android.graphics.Rect bounds = new android.graphics.Rect(128, 128, 0, 0);
    for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
      if (android.graphics.Color.alpha(source.getPixel(x, y)) > 8) bounds.union(x, y, x + 1, y + 1);
    }
    if (bounds.isEmpty()) { source.recycle(); return null; }
    notificationMask = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
    float scale = 56f / Math.max(bounds.width(), bounds.height());
    float width = bounds.width() * scale, height = bounds.height() * scale;
    android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
    paint.setColorFilter(new android.graphics.PorterDuffColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN));
    new Canvas(notificationMask).drawBitmap(source, bounds, new android.graphics.RectF((64f - width) / 2f, (64f - height) / 2f, (64f + width) / 2f, (64f + height) / 2f), paint);
    source.recycle();
    notificationMaskId = id;
    return notificationMask;
  }

  private static void setComponentEnabled (@NonNull PackageManager packageManager, @NonNull ComponentName component, boolean enabled) {
    packageManager.setComponentEnabledSetting(
      component,
      enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    );
  }

  /**
   * The system removes every task started by a disabled component. Aliases start
   * {@link org.thunderdog.challegram.LauncherActivity}, which hands the task over to
   * MainActivity, but a task opened before an update may still point to an alias.
   */
  private static boolean isTaskStartedByAlias (@NonNull Context context, @NonNull Icon keptIcon) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      // relinquishTaskIdentity is not supported.
      return true;
    }
    ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    if (activityManager == null) {
      return true;
    }
    try {
      for (ActivityManager.AppTask task : activityManager.getAppTasks()) {
        Intent baseIntent = task.getTaskInfo().baseIntent;
        ComponentName component = baseIntent != null ? baseIntent.getComponent() : null;
        if (component == null) {
          continue;
        }
        for (Icon icon : ICONS) {
          if (icon != keptIcon && component.equals(componentName(context, icon))) {
            return true;
          }
        }
      }
    } catch (RuntimeException error) {
      // Task disappeared while iterating.
      Log.w("Unable to inspect app tasks", error);
      return true;
    }
    return false;
  }

  /** Recent apps show the MainActivity icon, which is always the default one. */
  public static void onActivityCreated (@NonNull Activity activity) {
    if (!DEFAULT_ICON_ID.equals(getAppliedIconId())) {
      updateTaskDescription(activity);
    }
  }

  @SuppressWarnings("deprecation")
  private static void updateTaskDescription (@NonNull Activity activity) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      return;
    }
    Icon icon = findIcon(getAppliedIconId());
    if (icon == null) {
      return;
    }
    // Launchers ignore TaskDescription icon resources and only show bitmaps.
    int colorPrimary = ContextCompat.getColor(activity, R.color.apps_list);
    activity.setTaskDescription(new ActivityManager.TaskDescription(null, renderIcon(activity, icon), colorPrimary));
  }

  private static @Nullable Bitmap renderIcon (@NonNull Activity activity, @NonNull Icon icon) {
    Drawable drawable = ContextCompat.getDrawable(activity, icon.launcherIconRes);
    ActivityManager activityManager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
    if (drawable == null || activityManager == null) {
      return null;
    }
    int size = activityManager.getLauncherLargeIconSize();
    Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    drawable.setBounds(0, 0, size, size);
    drawable.draw(new Canvas(bitmap));
    return bitmap;
  }

  public static @NonNull ComponentName componentName (@NonNull Context context, @NonNull Icon icon) {
    return new ComponentName(
      context.getPackageName(),
      context.getPackageName() + ".launcher.Icon" + icon.aliasSuffix
    );
  }
}
