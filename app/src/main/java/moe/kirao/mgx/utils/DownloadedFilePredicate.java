package moe.kirao.mgx.utils;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

/** Pure file-state predicate shared by downloads search and JVM tests. */
public final class DownloadedFilePredicate {
  private DownloadedFilePredicate () { }

  public static boolean isDownloaded (@Nullable TdApi.File file) {
    return file != null && file.local != null && file.local.isDownloadingCompleted;
  }
}
