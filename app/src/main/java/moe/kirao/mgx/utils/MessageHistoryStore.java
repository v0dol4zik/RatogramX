package moe.kirao.mgx.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.json.JSONException;
import org.json.JSONObject;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import me.vkryl.leveldb.LevelDB;
import tgx.td.ChatId;

/** Small, opt-in and expiring journal of message text. Media and channel messages are excluded. */
public final class MessageHistoryStore {
  private static final long TTL_MS = 7L * 24L * 60L * 60L * 1000L;
  private static final int MAX_ENTRIES = 5000;
  private static final String PREFIX = "entry:";
  private static volatile MessageHistoryStore instance;
  private final LevelDB db;

  private MessageHistoryStore () {
    File dir = new File(UI.getAppContext().getFilesDir(), "moexconf/history");
    if (!dir.exists()) dir.mkdirs();
    db = new LevelDB(new File(dir, "db").getPath(), true, null);
    prune();
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
    JSONObject value = new JSONObject();
    try {
      value.put("chat_id", message.chatId);
      value.put("message_id", message.id);
      value.put("date", message.date);
      value.put("edit_date", message.editDate);
      value.put("text", text);
      value.put("kind", kind);
      value.put("timestamp", System.currentTimeMillis());
      db.putString(key(message.chatId, message.id), value.toString());
      prune();
    } catch (JSONException ignored) {
    }
  }

  public void markDeleted (@Nullable Tdlib tdlib, long chatId, long messageId) {
    if (!isEnabled() || !isAllowedChat(tdlib, chatId)) return;
    JSONObject value = new JSONObject();
    try {
      value.put("chat_id", chatId);
      value.put("message_id", messageId);
      value.put("kind", "deleted");
      value.put("timestamp", System.currentTimeMillis());
      db.putString(key(chatId, messageId), value.toString());
      prune();
    } catch (JSONException ignored) {
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

  private void prune () {
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
  }

  private static long timestamp (String value) {
    try { return new JSONObject(value).optLong("timestamp", 0L); } catch (JSONException e) { return 0L; }
  }

  private static String key (long chatId, long messageId) {
    return PREFIX + System.currentTimeMillis() + ":" + chatId + ":" + messageId;
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
}
