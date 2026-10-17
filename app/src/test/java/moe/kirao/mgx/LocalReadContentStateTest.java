package moe.kirao.mgx;

import moe.kirao.mgx.utils.LocalReadContentState;
import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import static org.junit.Assert.*;

public class LocalReadContentStateTest {
  private static TdApi.UnreadReaction reaction (long sender, String emoji) {
    return new TdApi.UnreadReaction(new TdApi.ReactionTypeEmoji(emoji), new TdApi.MessageSenderUser(sender), false);
  }

  private static TdApi.Message message (TdApi.UnreadReaction... reactions) {
    TdApi.Message message = new TdApi.Message();
    message.containsUnreadMention = true;
    message.unreadReactions = reactions;
    return message;
  }

  @Test public void pinMentionAndMultipleReactionsSurviveReloadWithoutMutatingServerMessage () {
    TdApi.Message message = message(reaction(1, "❤"), reaction(2, "👍"));
    message.content = new TdApi.MessagePinMessage(12);
    LocalReadContentState state = new LocalReadContentState(new TdApi.MessageTopicForum(11));
    assertTrue(state.read(message, true, true));
    state = new LocalReadContentState(state.store());
    assertTrue(state.isMentionRead());
    assertTrue(state.areReactionsRead());
    assertTrue(state.isReactionRead(message.unreadReactions[0]));
    assertTrue(state.matches(new TdApi.MessageTopicForum(11)));
    assertFalse(state.matches(new TdApi.MessageTopicForum(22)));
    assertTrue(message.containsUnreadMention);
    assertEquals(2, message.unreadReactions.length);
    assertFalse(state.read(message, true, true));
  }

  @Test public void newReactionOnAnOldMessageBecomesUnreadWhileSeenReactionsStayRead () {
    TdApi.UnreadReaction old = reaction(1, "❤"), added = reaction(2, "❤");
    LocalReadContentState state = new LocalReadContentState((TdApi.MessageTopic) null);
    state.read(message(old), false, true);
    assertTrue(state.syncReactions(new TdApi.UnreadReaction[] {added, old}));
    assertFalse(state.areReactionsRead());
    assertTrue(state.isReactionRead(old));
    assertFalse(state.isReactionRead(added));
    state = new LocalReadContentState(state.store());
    assertFalse(state.areReactionsRead());
    assertTrue(state.read(message(added, old), false, true));
    assertTrue(state.areReactionsRead());
  }

  @Test public void duplicateAndReorderedUpdatesDoNotRestoreUnreadState () {
    TdApi.UnreadReaction first = reaction(1, "❤"), second = reaction(2, "👍");
    LocalReadContentState state = new LocalReadContentState((TdApi.MessageTopic) null);
    state.read(message(first, second), false, true);
    assertFalse(state.syncReactions(new TdApi.UnreadReaction[] {second, first}));
    assertTrue(state.areReactionsRead());
    assertTrue(state.syncReactions(new TdApi.UnreadReaction[] {first}));
    assertTrue(state.areReactionsRead());
  }

  @Test public void serverReadAllowsTheSameSenderAndReactionToBecomeUnreadAgain () {
    TdApi.UnreadReaction reaction = reaction(1, "❤");
    LocalReadContentState state = new LocalReadContentState((TdApi.MessageTopic) null);
    state.read(message(reaction), false, true);
    assertTrue(state.syncReactions(new TdApi.UnreadReaction[0]));
    assertTrue(state.isEmpty());
    assertFalse(state.isReactionRead(reaction));
    assertFalse(state.areReactionsRead());
  }

  @Test public void senderAndReactionTypesHaveSeparateIdentities () {
    TdApi.UnreadReaction custom = new TdApi.UnreadReaction(new TdApi.ReactionTypeCustomEmoji(7), new TdApi.MessageSenderChat(1), false);
    LocalReadContentState state = new LocalReadContentState((TdApi.MessageTopic) null);
    state.read(message(custom, reaction(1, "❤")), false, true);
    assertTrue(state.isReactionRead(custom));
    assertFalse(state.isReactionRead(new TdApi.UnreadReaction(custom.type, new TdApi.MessageSenderUser(1), false)));
    assertFalse(state.isReactionRead(reaction(1, "👍")));
  }

  @Test public void explicitMentionAndReactionReadsAreIndependent () {
    TdApi.Message message = message(reaction(1, "❤"));
    LocalReadContentState state = new LocalReadContentState((TdApi.MessageTopic) null);
    state.read(message, true, false);
    assertTrue(state.isMentionRead());
    assertFalse(state.areReactionsRead());
    state.clear(true, false);
    state.read(message, false, true);
    assertFalse(state.isMentionRead());
    assertTrue(state.areReactionsRead());
  }
}
