package moe.kirao.mgx.utils;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

import tgx.td.ChatId;

public final class VideoPositionPolicy {
  private VideoPositionPolicy () { }

  public static boolean canRemember (boolean regularVideo, boolean privateMedia, @Nullable TdApi.Message message) {
    return regularVideo && !privateMedia && (message == null ||
      (message.selfDestructType == null && message.sendingState == null && message.schedulingState == null));
  }

  public static @Nullable String key (int accountId, long chatId, long messageId, @Nullable String remoteUniqueId) {
    if (accountId < 0 || ChatId.isSecret(chatId)) return null;
    String prefix = "video_pos_" + accountId + "_";
    if (chatId != 0 && messageId > 0) return prefix + chatId + "_" + messageId;
    return remoteUniqueId != null && !remoteUniqueId.isEmpty() ? prefix + "remote_" + remoteUniqueId : null;
  }

  public static long retainedPosition (long positionMs, long durationMs) {
    return durationMs > 60_000 && positionMs >= 5_000 && positionMs <= durationMs - 5_000 ? positionMs : 0;
  }
}
