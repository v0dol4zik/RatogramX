package moe.kirao.mgx.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.json.JSONException;
import org.json.JSONObject;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import me.vkryl.leveldb.LevelDB;
import tgx.td.ChatId;

/** Small, opt-in and expiring journal of message text. Media and channel messages are excluded. */
public final class MessageHistoryStore {
  private static final long TTL_MS = 7L * 24L * 60L * 60L * 1000L;
  private static final int MAX_ENTRIES = 5000;
  private static final String PREFIX = "entry:";
  private static volatile MessageHistoryStore instance;
  private final LevelDB db;
  private final AtomicLong keySequence = new AtomicLong();
  private long lastPruneAt;
  private int writesSincePrune;

  private MessageHistoryStore () {
    File dir = new File(AppContext.get().getFilesDir(), "moexconf/history");
    if (!dir.exists()) dir.mkdirs();
    db = new LevelDB(new File(dir, "db").getPath(), true, null);
    // Database maintenance must never run on the TDLib callback or UI thread.
    Background.instance().post(this::pruneInternal);
  }

  public static MessageHistoryStore instance () {
    if (instance == null) {
      synchronized (MessageHistoryStore.class) {
        if (instance == null) instance = new MessageHistoryStore();
      }
    }
    return instance;
  }

  public static boolean isEnabled () {
    return Settings.instance().isMessageHistoryExperimentEnabled();
  }

  public void recordPrevious (@Nullable Tdlib tdlib, @Nullable TdApi.Message message, @NonNull String kind) {
    if (!isEnabled() || message == null || !isAllowedChat(tdlib, message.chatId)) return;
    String text = TD.getTextFromMessage(message);
    if (text == null || text.trim().isEmpty()) return;
    enqueue(new PendingEntry(message.chatId, message.id, message.date, message.editDate,
      MessageHistoryPolicy.limitText(text), kind, System.currentTimeMillis()));
  }

  public void markDeleted (@Nullable Tdlib tdlib, long chatId, long messageId) {
    if (!isEnabled() || !isAllowedChat(tdlib, chatId)) return;
    enqueue(new PendingEntry(chatId, messageId, 0, 0,
      null, "deleted", System.currentTimeMillis()));
  }

  private void enqueue (@NonNull PendingEntry pending) {
    Background.instance().post(() -> writeInternal(pending));
  }

  private void writeInternal (@NonNull PendingEntry pending) {
    JSONObject value = new JSONObject();
    try {
      value.put("chat_id", pending.chatId);
      value.put("message_id", pending.messageId);
      if (pending.date != 0) value.put("date", pending.date);
      if (pending.editDate != 0) value.put("edit_date", pending.editDate);
      if (pending.text != null) value.put("text", pending.text);
      value.put("kind", pending.kind);
      value.put("timestamp", pending.timestamp);
      db.putString(key(pending.timestamp, pending.chatId, pending.messageId), value.toString());
      writesSincePrune++;
      long now = System.currentTimeMillis();
      if (MessageHistoryPolicy.shouldPrune(now, lastPruneAt, writesSincePrune)) {
        pruneInternal();
      }
    } catch (JSONException ignored) {
      // The values above are primitive/string values and should never fail. Keep a
      // malformed event from interrupting the TDLib background queue if it does.
    }
  }

  @NonNull
  public String dump () {
    List<String> values = new ArrayList<>();
    for (LevelDB.Entry entry : db.find(PREFIX)) {
      try {
        values.add(entry.asString());
      } finally {
        entry.release();
      }
    }
    Collections.sort(values, Comparator.comparingLong(MessageHistoryStore::timestamp));
    StringBuilder out = new StringBuilder();
    for (String value : values) out.append(value).append('\n');
    return out.toString();
  }

  /**
   * Reads the journal off the caller thread and delivers the result on the UI
   * thread. This is the preferred API for settings screens.
   */
  public void dumpAsync (@NonNull DumpCallback callback) {
    Background.instance().post(() -> {
      final String dump = dump();
      UI.post(() -> callback.onDumpReady(dump));
    });
  }

  public interface DumpCallback {
    void onDumpReady (@NonNull String dump);
  }

  private void pruneInternal () {
    long cutoff = System.currentTimeMillis() - TTL_MS;
    List<String> keys = new ArrayList<>();
    List<EntryInfo> entries = new ArrayList<>();
    for (LevelDB.Entry entry : db.find(PREFIX)) {
      String key = entry.key();
      String raw = entry.asString();
      entry.release();
      try {
        long ts = new JSONObject(raw).optLong("timestamp", 0L);
        if (ts < cutoff) keys.add(key); else entries.add(new EntryInfo(key, ts));
      } catch (JSONException e) {
        keys.add(key);
      }
    }
    Collections.sort(entries, Comparator.comparingLong(e -> e.timestamp));
    for (int i = 0; i < Math.max(0, entries.size() - MAX_ENTRIES); i++) keys.add(entries.get(i).key);
    for (String key : keys) db.remove(key);
    lastPruneAt = System.currentTimeMillis();
    writesSincePrune = 0;
  }

  private static long timestamp (String value) {
    try { return new JSONObject(value).optLong("timestamp", 0L); } catch (JSONException e) { return 0L; }
  }

  private String key (long timestamp, long chatId, long messageId) {
    // The sequence avoids collisions when several TDLib updates arrive in one
    // millisecond while retaining the timestamp for diagnostics and migration.
    return PREFIX + timestamp + ":" + keySequence.incrementAndGet() + ":" + chatId + ":" + messageId;
  }

  private static boolean isAllowedChat (@Nullable Tdlib tdlib, long chatId) {
    int type = ChatId.getType(chatId, false);
    if (type == TdApi.ChatTypePrivate.CONSTRUCTOR || type == TdApi.ChatTypeBasicGroup.CONSTRUCTOR) return true;
    return type == TdApi.ChatTypeSupergroup.CONSTRUCTOR && tdlib != null && !tdlib.isChannel(chatId);
  }

  private static final class EntryInfo {
    final String key; final long timestamp;
    EntryInfo (String key, long timestamp) { this.key = key; this.timestamp = timestamp; }
  }

  private static final class PendingEntry {
    final long chatId;
    final long messageId;
    final int date;
    final int editDate;
    final String text;
    final String kind;
    final long timestamp;

    PendingEntry (long chatId, long messageId, int date, int editDate, @Nullable String text,
                  @NonNull String kind, long timestamp) {
      this.chatId = chatId;
      this.messageId = messageId;
      this.date = date;
      this.editDate = editDate;
      this.text = text;
      this.kind = kind;
      this.timestamp = timestamp;
    }
  }
}
