package moe.kirao.mgx;

import moe.kirao.mgx.utils.PollTranslationUtils;
import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class PollTranslationUtilsTest {
  private static TdApi.FormattedText text (String text) {
    return new TdApi.FormattedText(text, new TdApi.TextEntity[0]);
  }

  private static TdApi.Poll poll () {
    TdApi.Poll poll = new TdApi.Poll();
    poll.question = text("Question");
    TdApi.PollOption yes = new TdApi.PollOption();
    yes.text = text("yes");
    TdApi.PollOption no = new TdApi.PollOption();
    no.text = text("no");
    poll.options = new TdApi.PollOption[]{yes, no};
    return poll;
  }

  @Test public void descriptionEntitiesSurviveSeparatorsAndUtf16Emoji () {
    String caption = "🧪 Details • link";
    TdApi.TextEntity emoji = new TdApi.TextEntity(0, 2, new TdApi.TextEntityTypeCustomEmoji(501L));
    TdApi.TextEntity link = new TdApi.TextEntity(caption.indexOf("link"), 4, new TdApi.TextEntityTypeTextUrl("https://example.org"));
    TdApi.FormattedText description = new TdApi.FormattedText(caption, new TdApi.TextEntity[]{emoji, link});
    TdApi.Poll poll = poll();
    TdApi.TextEntity bold = new TdApi.TextEntity(2, 3, new TdApi.TextEntityTypeBold());
    poll.options[0].text = new TdApi.FormattedText("• yes", new TdApi.TextEntity[]{bold});

    TdApi.FormattedText source = PollTranslationUtils.buildSource(description, poll);
    TdApi.FormattedText[] parts = PollTranslationUtils.splitResult(source, description, poll.options.length);

    assertNotNull(parts);
    assertEquals(4, parts.length);
    assertEquals("🧪 Details   link", parts[0].text);
    assertEquals(0, parts[0].entities[0].offset);
    assertEquals(2, parts[0].entities[0].length);
    assertEquals(501L, ((TdApi.TextEntityTypeCustomEmoji) parts[0].entities[0].type).customEmojiId);
    assertEquals(caption.indexOf("link"), parts[0].entities[1].offset);
    assertEquals("Question", parts[1].text);
    assertEquals("yes", parts[2].text);
    assertEquals(0, parts[2].entities[0].offset);
    assertEquals(3, parts[2].entities[0].length);
    assertEquals(caption, description.text);
    assertEquals(2, bold.offset);
  }

  @Test public void pollsWithoutDescriptionKeepQuestionAndOptionOrder () {
    TdApi.Poll poll = poll();
    TdApi.FormattedText description = text("");
    TdApi.FormattedText[] parts = PollTranslationUtils.splitResult(PollTranslationUtils.buildSource(description, poll), description, 2);

    assertNotNull(parts);
    assertEquals(0, PollTranslationUtils.getQuestionIndex(description));
    assertEquals(3, parts.length);
    assertEquals("Question", parts[0].text);
    assertEquals("yes", parts[1].text);
    assertEquals("no", parts[2].text);
  }

  @Test public void malformedTranslationFallsBackInsteadOfShiftingAnswers () {
    assertNull(PollTranslationUtils.splitResult(text("Question • yes • no"), text("Description"), 2));
    assertNull(PollTranslationUtils.splitResult(text("Description • Question • yes • no • extra"), text("Description"), 2));
    assertNull(PollTranslationUtils.splitResult(null, text("Description"), 2));
  }

  @Test public void translatorWhitespaceIsTrimmedForEveryPart () {
    TdApi.FormattedText[] parts = PollTranslationUtils.splitResult(text("  intro \n\n•   Question  \n\n•  yes \n\n• no  "), text("Description"), 2);

    assertNotNull(parts);
    assertEquals("intro", parts[0].text);
    assertEquals("Question", parts[1].text);
    assertEquals("yes", parts[2].text);
    assertEquals("no", parts[3].text);
  }
}
