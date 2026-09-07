/*
 * This file is a part of Ratogram X, a Telegram X fork.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.thunderdog.challegram.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import org.thunderdog.challegram.Log
import org.thunderdog.challegram.unsorted.Settings
import moe.kirao.mgx.MoexConfig
import org.thunderdog.challegram.core.Background

/**
 * Selects conservative first-run defaults on devices where animated media and
 * transitions are likely to compete with TDLib for memory and CPU time.
 *
 * The marker is written after the first evaluation, so user changes made in
 * Telegram X's regular settings are never overwritten on later launches.
 */
object LowEndPerformanceProfile {
  private const val PROFILE_VERSION_KEY = "ratogramx_low_end_profile_version"
  private const val PROFILE_VERSION = 2
  private const val LOW_MEMORY_CLASS_MIB = 192
  private const val LOW_TOTAL_MEMORY_BYTES = 3L * 1024L * 1024L * 1024L
  private const val LIMITED_CPU_TOTAL_MEMORY_BYTES = 4L * 1024L * 1024L * 1024L

  @JvmStatic
  fun applyInitialDefaults(context: Context) {
    val settings = Settings.instance()
    if (settings.getInt(PROFILE_VERSION_KEY, 0) >= PROFILE_VERSION) {
      return
    }

    if (isLowEndDevice(context)) {
      settings.setAutoplayGIFs(false)
      settings.setReduceMotion(true)
      settings.setNewSetting(Settings.SETTING_FLAG_LIMIT_STICKERS_FPS, true)
      settings.setNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_STICKERS_LOOP, true)
      Background.instance().post {
        val moex = MoexConfig.instance()
        if (!moex.containsKey(MoexConfig.KEY_BLUR_DRAWER)) {
          moex.putBoolean(MoexConfig.KEY_BLUR_DRAWER, false)
          MoexConfig.blurDrawer = false
        }
      }
      Log.i("Applied Ratogram X low-end performance defaults")
    }

    settings.putInt(PROFILE_VERSION_KEY, PROFILE_VERSION)
  }

  @JvmStatic
  fun isLowEndDevice(context: Context): Boolean {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
      ?: return Runtime.getRuntime().availableProcessors() <= 4

    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    return evaluate(
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && activityManager.isLowRamDevice,
      activityManager.memoryClass,
      memoryInfo.totalMem,
      Runtime.getRuntime().availableProcessors()
    )
  }

  /** Pure threshold logic kept separate so it can be verified on the JVM. */
  @JvmStatic
  fun evaluate(isLowRam: Boolean, memoryClassMiB: Int, totalMemBytes: Long, cpus: Int): Boolean {
    if (isLowRam || memoryClassMiB <= LOW_MEMORY_CLASS_MIB) return true
    if (totalMemBytes in 1..LOW_TOTAL_MEMORY_BYTES) return true
    return cpus <= 4 && totalMemBytes in 1..LIMITED_CPU_TOTAL_MEMORY_BYTES
  }
}
