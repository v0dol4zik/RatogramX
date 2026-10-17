package moe.kirao.mgx.utils;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

import java.util.Arrays;
import java.util.TreeSet;

/** Read content is separate from the inbox: old messages can receive new reactions. */
public final class LocalReadContentState {
  private final String topicKey;
  private boolean mentionRead, reactionsRead;
  private String[] readReactions;

  public LocalReadContentState (@Nullable TdApi.MessageTopic topicId) {
    topicKey = GhostReadPolicy.key(0, 0, topicId);
    readReactions = new String[0];
  }

  public LocalReadContentState (String[] stored) {
    topicKey = stored[0];
    mentionRead = "1".equals(stored[1]);
    reactionsRead = "1".equals(stored[2]);
    readReactions = Arrays.copyOfRange(stored, 3, stored.length);
    Arrays.sort(readReactions);
  }

  public String[] store () {
    String[] stored = new String[3 + readReactions.length];
    stored[0] = topicKey;
    stored[1] = mentionRead ? "1" : "0";
    stored[2] = reactionsRead ? "1" : "0";
    System.arraycopy(readReactions, 0, stored, 3, readReactions.length);
    return stored;
  }

  public boolean matches (@Nullable TdApi.MessageTopic topicId) {
    return topicId == null || topicKey.equals(GhostReadPolicy.key(0, 0, topicId));
  }

  public boolean isMentionRead () { return mentionRead; }
  public boolean areReactionsRead () { return reactionsRead; }
  public boolean isEmpty () { return !mentionRead && readReactions.length == 0; }

  public boolean read (TdApi.Message message, boolean mentions, boolean reactions) {
    String[] before = store();
    if (mentions && message.containsUnreadMention) mentionRead = true;
    if (reactions) {
      readReactions = reactionKeys(message.unreadReactions);
      reactionsRead = readReactions.length > 0;
    }
    return !Arrays.equals(before, store());
  }

  public boolean clear (boolean mentions, boolean reactions) {
    boolean changed = mentions && mentionRead || reactions && readReactions.length > 0;
    if (mentions) mentionRead = false;
    if (reactions) {
      readReactions = new String[0];
      reactionsRead = false;
    }
    return changed;
  }

  public boolean syncReactions (@Nullable TdApi.UnreadReaction[] reactions) {
    String[] current = reactionKeys(reactions);
    String[] retained = Arrays.stream(readReactions).filter(key -> Arrays.binarySearch(current, key) >= 0).toArray(String[]::new);
    boolean allRead = current.length > 0 && retained.length == current.length;
    boolean changed = reactionsRead != allRead || !Arrays.equals(readReactions, retained);
    readReactions = retained;
    reactionsRead = allRead;
    return changed;
  }

  public boolean isReactionRead (TdApi.UnreadReaction reaction) {
    return Arrays.binarySearch(readReactions, reactionKey(reaction)) >= 0;
  }

  private static String[] reactionKeys (@Nullable TdApi.UnreadReaction[] reactions) {
    TreeSet<String> keys = new TreeSet<>();
    if (reactions != null) for (TdApi.UnreadReaction reaction : reactions) keys.add(reactionKey(reaction));
    return keys.toArray(new String[0]);
  }

  private static String reactionKey (TdApi.UnreadReaction reaction) {
    String type;
    if (reaction.type instanceof TdApi.ReactionTypeEmoji) {
      type = ((TdApi.ReactionTypeEmoji) reaction.type).emoji;
    } else if (reaction.type instanceof TdApi.ReactionTypeCustomEmoji) {
      type = Long.toString(((TdApi.ReactionTypeCustomEmoji) reaction.type).customEmojiId);
    } else {
      type = "";
    }
    long senderId = reaction.senderId instanceof TdApi.MessageSenderUser ?
      ((TdApi.MessageSenderUser) reaction.senderId).userId : ((TdApi.MessageSenderChat) reaction.senderId).chatId;
    return reaction.senderId.getConstructor() + ":" + senderId + ":" + reaction.isBig + ":" + reaction.type.getConstructor() + ":" + type;
  }
}
