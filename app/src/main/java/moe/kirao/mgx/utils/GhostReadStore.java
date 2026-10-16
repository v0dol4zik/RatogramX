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
  private static final Map<String, long[]> topicIndex = new HashMap<>();
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
    LocalReadState own = state(key(accountId, chatId, topicId));
    int count = own.getUnreadCount(inbox.readMessageId, inbox.unreadCount, inbox.lastMessageId);
    if (topicId == null && count > 0) {
      for (long topic : forumTopicIds(accountId, chatId)) {
        count = Math.max(0, count - state(key(accountId, chatId, new TdApi.MessageTopicForum((int) topic))).locallyReadCountAfter(own.readMessageId));
      }
    }
    return count;
  }

  public static boolean isLocallyRead (int accountId, long chatId, @Nullable TdApi.MessageTopic topicId, long messageId) {
    if (messageId <= 0) return false;
    long[] seen = topicId == null ? MoexConfig.instance().getLongArray(key(accountId, chatId, null) + "_seen") : null;
    return seen != null && java.util.Arrays.binarySearch(seen, messageId) >= 0 || messageId <= readMessageId(accountId, chatId, topicId, 0) ||
      topicId != null && messageId <= readMessageId(accountId, chatId, null, 0);
  }

  public static synchronized long[] forumTopicIds (int accountId, long chatId) {
    String listKey = key(accountId, chatId, null) + "_forums";
    long[] cached = topicIndex.get(listKey);
    if (cached != null) return cached;
    long[] topics = MoexConfig.instance().getLongArray(listKey);
    if (topics == null) {
      String prefix = key(accountId, chatId, null) + "_forum_";
      java.util.ArrayList<Long> found = new java.util.ArrayList<>();
      for (LevelDB.Entry entry : MoexConfig.instance().config().find(prefix)) {
        try { found.add(Long.parseLong(entry.key().substring(prefix.length()))); } catch (NumberFormatException ignored) { }
      }
      topics = new long[found.size()];
      for (int i = 0; i < topics.length; i++) topics[i] = found.get(i);
      if (topics.length > 0) MoexConfig.instance().putLongArray(listKey, topics);
    }
    topicIndex.put(listKey, topics);
    return topics;
  }

  public static synchronized void rememberViewedIds (int accountId, long chatId, long[] messageIds) {
    String key = key(accountId, chatId, null) + "_seen";
    long[] before = MoexConfig.instance().getLongArray(key);
    java.util.stream.LongStream combined = before != null ? java.util.stream.LongStream.concat(java.util.Arrays.stream(before), java.util.Arrays.stream(messageIds)) : java.util.Arrays.stream(messageIds);
    long[] ids = combined.filter(id -> id > 0).distinct().sorted().toArray();
    // Only needed for delayed push messages that have no topic metadata.
    if (ids.length > 4096) ids = java.util.Arrays.copyOfRange(ids, ids.length - 4096, ids.length);
    if (java.util.Arrays.equals(before, ids)) return;
    MoexConfig.instance().putLongArray(key, ids);
    notifyChanged(accountId, chatId, null, 0);
  }

  public static void syncForumInbox (int accountId, long chatId, TdApi.ForumTopic topic) {
    TdApi.MessageTopic topicId = new TdApi.MessageTopicForum(topic.info.forumTopicId);
    serverRead(accountId, chatId, topicId, topic.lastReadInboxMessageId);
    boolean changed;
    synchronized (GhostReadStore.class) {
      String key = key(accountId, chatId, topicId);
      LocalReadState state = state(key);
      if (state.readMessageId == 0) return;
      Inbox inbox = Inbox.from(topic);
      changed = state.update(inbox.readMessageId, inbox.unreadCount, inbox.lastMessageId, state.readMessageId, 0, -1);
      if (changed) MoexConfig.instance().putLongArray(key + "_counter", state.counter());
    }
    if (changed) notifyChanged(accountId, chatId, null, 0);
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
      if (topicId instanceof TdApi.MessageTopicForum) {
        String topicsKey = key(accountId, chatId, null) + "_forums";
        long topic = ((TdApi.MessageTopicForum) topicId).forumTopicId;
        long[] topics = forumTopicIds(accountId, chatId);
        boolean found = false;
        if (topics != null) for (long value : topics) if (value == topic) found = true;
        if (!found) {
          long[] updated = topics != null ? java.util.Arrays.copyOf(topics, topics.length + 1) : new long[1];
          updated[updated.length - 1] = topic;
          editor.putLongArray(topicsKey, updated);
          topicIndex.put(topicsKey, updated);
        }
      }
      editor.apply();
      readId = state.readMessageId;
    }
    notifyChanged(accountId, chatId, topicId, readId);
    if (topicId instanceof TdApi.MessageTopicForum) notifyChanged(accountId, chatId, null, 0);
  }

  public static void serverRead (int accountId, long chatId, @Nullable TdApi.MessageTopic topicId, long serverReadId) {
    synchronized (GhostReadStore.class) {
      String key = key(accountId, chatId, topicId);
      LocalReadState state = state(key);
      if (state.readMessageId == 0) return;
      if (state.readMessageId > serverReadId) {
        if (state.invalidateEstimate(serverReadId)) MoexConfig.instance().putLongArray(key + "_counter", state.counter());
        else return;
      } else {
        states.remove(key);
        LevelDB editor = MoexConfig.instance().edit();
        editor.remove(key);
        editor.remove(key + "_counter");
        editor.apply();
      }
    }
    notifyChanged(accountId, chatId, topicId, serverReadId);
    if (topicId instanceof TdApi.MessageTopicForum) notifyChanged(accountId, chatId, null, 0);
  }

  public static void clear (int accountId, long chatId, @Nullable TdApi.MessageTopic topicId) {
    synchronized (GhostReadStore.class) {
      String key = key(accountId, chatId, topicId);
      states.remove(key);
      LevelDB editor = MoexConfig.instance().edit();
      editor.remove(key);
      editor.remove(key + "_counter");
      if (topicId == null) {
        long[] topics = MoexConfig.instance().getLongArray(key + "_forums");
        if (topics != null) for (long topic : topics) {
          String topicKey = key(accountId, chatId, new TdApi.MessageTopicForum((int) topic));
          states.remove(topicKey);
          editor.remove(topicKey);
          editor.remove(topicKey + "_counter");
        }
        editor.remove(key + "_forums");
        topicIndex.remove(key + "_forums");
        editor.remove(key + "_seen");
      }
      editor.apply();
    }
    notifyChanged(accountId, chatId, topicId, 0);
  }

  public static synchronized void clearAccount (int accountId) {
    String prefix = PREFIX + accountId + "_";
    MoexConfig.instance().config().removeByPrefix(prefix);
    states.keySet().removeIf(key -> key.startsWith(prefix));
    topicIndex.keySet().removeIf(key -> key.startsWith(prefix));
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
