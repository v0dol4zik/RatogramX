package moe.kirao.mgx.utils;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.ThreadInfo;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.vkryl.core.reference.ReferenceList;
import me.vkryl.leveldb.LevelDB;
import moe.kirao.mgx.MoexConfig;

/** Private per-account inbox markers, deliberately excluded from settings export. */
public final class GhostReadStore {
  private GhostReadStore () { }

  private static final String PREFIX = "ghost_read_";
  private static final Map<String, LocalReadState> states = new HashMap<>();
  private static final ReferenceList<Listener> listeners = new ReferenceList<>();

  public interface Listener {
    void onLocalReadStateChanged (int accountId, long chatId, @Nullable TdApi.MessageTopic topicId, long readMessageId);
  }

  public static final class Inbox {
    public final long readMessageId, lastMessageId;
    public final int unreadCount;

    public Inbox (long readMessageId, int unreadCount, long lastMessageId) {
      this.readMessageId = readMessageId;
      this.unreadCount = unreadCount;
      this.lastMessageId = lastMessageId;
    }

    public static Inbox from (TdApi.Chat chat) {
      return new Inbox(chat.lastReadInboxMessageId, chat.unreadCount, chat.lastMessage != null ? chat.lastMessage.id : 0);
    }

    public static Inbox from (TdApi.ForumTopic topic) {
      return new Inbox(topic.lastReadInboxMessageId, topic.unreadCount, topic.lastMessage != null ? topic.lastMessage.id : 0);
    }

    public static Inbox from (ThreadInfo thread) {
      return new Inbox(thread.getLastReadInboxMessageId(), thread.getUnreadMessageCount(), thread.getLastMessageId());
    }
  }

  static String key (int accountId, long chatId, @Nullable TdApi.MessageTopic topicId) {
    return GhostReadPolicy.key(accountId, chatId, topicId);
  }

  private static LocalReadState state (String key) {
    LocalReadState state = states.get(key);
    if (state == null) {
      MoexConfig config = MoexConfig.instance();
      state = new LocalReadState(config.getLong(key, 0), config.getLongArray(key + "_counter"));
      states.put(key, state);
    }
    return state;
  }

  public static synchronized long readMessageId (int accountId, long chatId,
                                                @Nullable TdApi.MessageTopic topicId, long serverReadId) {
    return Math.max(serverReadId, state(key(accountId, chatId, topicId)).readMessageId);
  }

  public static synchronized int unreadCount (int accountId, long chatId,
                                              @Nullable TdApi.MessageTopic topicId, Inbox inbox) {
    return state(key(accountId, chatId, topicId)).getUnreadCount(inbox.readMessageId, inbox.unreadCount, inbox.lastMessageId);
  }

  public static void record (int accountId, long chatId, @Nullable TdApi.MessageTopic topicId,
                             Inbox inbox, long viewedMessageId, int newlyReadCount, int exactRemainingCount) {
    if (!GhostModeUtils.shouldSuppressRead()) return;
    long readId;
    synchronized (GhostReadStore.class) {
      String key = key(accountId, chatId, topicId);
      LocalReadState state = state(key);
      if (!state.update(inbox.readMessageId, inbox.unreadCount, inbox.lastMessageId,
                        viewedMessageId, newlyReadCount, exactRemainingCount)) return;
      LevelDB editor = MoexConfig.instance().edit();
      editor.putLong(key, state.readMessageId);
      editor.putLongArray(key + "_counter", state.counter());
      editor.apply();
      readId = state.readMessageId;
    }
    notifyChanged(accountId, chatId, topicId, readId);
  }

  public static void serverRead (int accountId, long chatId, @Nullable TdApi.MessageTopic topicId, long serverReadId) {
    synchronized (GhostReadStore.class) {
      String key = key(accountId, chatId, topicId);
      LocalReadState state = state(key);
      if (state.readMessageId == 0 || state.readMessageId > serverReadId) return;
      states.remove(key);
      LevelDB editor = MoexConfig.instance().edit();
      editor.remove(key);
      editor.remove(key + "_counter");
      editor.apply();
    }
    notifyChanged(accountId, chatId, topicId, serverReadId);
  }

  public static void clear (int accountId, long chatId, @Nullable TdApi.MessageTopic topicId) {
    synchronized (GhostReadStore.class) {
      String key = key(accountId, chatId, topicId);
      states.remove(key);
      LevelDB editor = MoexConfig.instance().edit();
      editor.remove(key);
      editor.remove(key + "_counter");
      editor.apply();
    }
    notifyChanged(accountId, chatId, topicId, 0);
  }

  public static synchronized void clearAccount (int accountId) {
    String prefix = PREFIX + accountId + "_";
    MoexConfig.instance().config().removeByPrefix(prefix);
    states.keySet().removeIf(key -> key.startsWith(prefix));
  }

  public static synchronized void addListener (Listener listener) {
    listeners.add(listener);
  }

  public static synchronized void removeListener (Listener listener) {
    listeners.remove(listener);
  }

  private static void notifyChanged (int accountId, long chatId, @Nullable TdApi.MessageTopic topicId, long readId) {
    List<Listener> copy = new ArrayList<>();
    synchronized (GhostReadStore.class) {
      for (Listener listener : listeners) copy.add(listener);
    }
    UI.post(() -> {
      for (Listener listener : copy) listener.onLocalReadStateChanged(accountId, chatId, topicId, readId);
    });
  }
}
