package moe.kirao.mgx.utils;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.mediaview.data.MediaItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.leveldb.LevelDB;
import moe.kirao.mgx.MoexConfig;

/** Local playback bookmarks; account data is excluded from settings export. */
public final class VideoPositionStore {
  private VideoPositionStore () { }

  private static final String PREFIX = "video_pos_";
  private static final int MAX_ENTRIES = 200;

  private static @Nullable String key (@Nullable MediaItem item) {
    if (item == null || !VideoPositionPolicy.canRemember(item.getType() == MediaItem.TYPE_VIDEO,
      item.isSecret() || item.isViewOnce() || item.getSelfDestructType() != null || item.getSourceSponsoredMessage() != null,
      item.getMessage())) return null;
    TdApi.File file = item.getTargetFile();
    return VideoPositionPolicy.key(item.tdlib().id(), item.getSourceChatId(), item.getSourceMessageId(),
      file != null && file.remote != null ? file.remote.uniqueId : null);
  }

  public static synchronized long get (@Nullable MediaItem item) {
    String key = key(item);
    if (key == null) return 0;
    MoexConfig config = MoexConfig.instance();
    long[] record = config.getLongArray(key);
    if (record == null) return 0;
    long durationMs = item.getVideoDuration(false, TimeUnit.MILLISECONDS);
    long positionMs = record.length == 3 ? VideoPositionPolicy.retainedPosition(record[0], durationMs > 0 ? durationMs : record[1]) : 0;
    if (positionMs == 0) {
      config.remove(key);
    } else {
      config.putLongArray(key, new long[] {positionMs, record[1], System.currentTimeMillis()});
    }
    return positionMs;
  }

  public static synchronized void save (@Nullable MediaItem item, long positionMs, long durationMs) {
    String key = key(item);
    if (key == null) return;
    if (durationMs <= 0) durationMs = item.getVideoDuration(false, TimeUnit.MILLISECONDS);
    positionMs = VideoPositionPolicy.retainedPosition(positionMs, durationMs);
    MoexConfig config = MoexConfig.instance();
    if (positionMs == 0) {
      config.remove(key);
      return;
    }

    List<SavedEntry> entries = new ArrayList<>();
    for (LevelDB.Entry entry : config.config().find(PREFIX)) {
      String entryKey = entry.key();
      if (entryKey.equals(key)) continue;
      long[] record = entry.asLongArray();
      entries.add(new SavedEntry(entryKey, record != null && record.length == 3 ? record[2] : 0));
    }
    entries.sort((a, b) -> Long.compare(a.lastUsed, b.lastUsed));
    LevelDB editor = config.edit();
    editor.putLongArray(key, new long[] {positionMs, durationMs, System.currentTimeMillis()});
    for (int i = 0; i < entries.size() + 1 - MAX_ENTRIES; i++) editor.remove(entries.get(i).key);
    editor.apply();
  }

  public static synchronized void clear (@Nullable MediaItem item) {
    String key = key(item);
    if (key != null) MoexConfig.instance().remove(key);
  }

  public static synchronized void clearAccount (int accountId) {
    MoexConfig.instance().config().removeByPrefix(PREFIX + accountId + "_");
  }

  private static final class SavedEntry {
    final String key;
    final long lastUsed;

    SavedEntry (String key, long lastUsed) {
      this.key = key;
      this.lastUsed = lastUsed;
    }
  }
}
