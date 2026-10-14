package moe.kirao.mgx;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import java.util.Arrays;

import moe.kirao.mgx.utils.RichMessageUtils;

import static org.junit.Assert.*;

public class RichMessageUtilsTest {
  private static TdApi.RichText plain (String text) { return new TdApi.RichTextPlain(text); }
  private static TdApi.PageBlock paragraph (String text) { return new TdApi.PageBlockParagraph(plain(text)); }
  private static TdApi.RichMessage message (TdApi.PageBlock... blocks) { return new TdApi.RichMessage(blocks, false, true); }
  private static TdApi.File file (int id) { TdApi.File file = new TdApi.File(); file.id = id; return file; }
  private static TdApi.PageBlockPhoto photo (int id) {
    TdApi.PhotoSize small = new TdApi.PhotoSize(); small.width = small.height = 20; small.photo = file(id + 100);
    TdApi.PhotoSize large = new TdApi.PhotoSize(); large.width = 640; large.height = 480; large.photo = file(id);
    TdApi.Photo photo = new TdApi.Photo(); photo.sizes = new TdApi.PhotoSize[] {large, small};
    return new TdApi.PageBlockPhoto(photo, null, null, false);
  }

  @Test public void nestedContainersKeepTextAndCaptionOrder () {
    TdApi.PageBlockListItem item = new TdApi.PageBlockListItem("1.", new TdApi.PageBlock[] {paragraph("Item")}, false, false, 1, "1");
    TdApi.RichMessage message = message(
      new TdApi.PageBlockSectionHeading(plain("Heading"), 1),
      new TdApi.PageBlockCover(paragraph("Cover")),
      new TdApi.PageBlockBlockQuote(new TdApi.PageBlock[] {paragraph("Quote")}, plain("Credit")),
      new TdApi.PageBlockDetails(plain("Details"), new TdApi.PageBlock[] {new TdApi.PageBlockList(new TdApi.PageBlockListItem[] {item})}, false),
      new TdApi.PageBlockCollage(new TdApi.PageBlock[] {photo(1)}, new TdApi.PageBlockCaption(plain("Album"), plain("Author")))
    );
    assertEquals("Heading\nCover\nQuote\nCredit\nDetails\nItem\nAlbum\nAuthor", RichMessageUtils.text(message).text);
    assertEquals(1, RichMessageUtils.mediaCount(message));
  }

  @Test public void allMediaKindsAndLargestPhotoAreDiscoveredInsideQuotes () {
    TdApi.Video video = new TdApi.Video(); video.video = file(2);
    TdApi.Animation animation = new TdApi.Animation(); animation.animation = file(3);
    TdApi.Audio audio = new TdApi.Audio(); audio.audio = file(4);
    TdApi.Document document = new TdApi.Document(); document.document = file(5);
    TdApi.VoiceNote voice = new TdApi.VoiceNote(); voice.voice = file(6);
    TdApi.RichMessage message = message(new TdApi.PageBlockBlockQuote(new TdApi.PageBlock[] {
      photo(1), new TdApi.PageBlockVideo(video, null, false, false, false),
      new TdApi.PageBlockAnimation(animation, null, false, false), new TdApi.PageBlockAudio(audio, null),
      new TdApi.PageBlockDocument(document, null), new TdApi.PageBlockVoiceNote(voice, null), photo(1)
    }, null));
    assertEquals(7, RichMessageUtils.mediaCount(message));
    assertEquals(1, RichMessageUtils.firstFile(message).id);
    assertEquals(6, RichMessageUtils.files(message).size());
    for (int i = 0; i < 6; i++) assertEquals(i + 1, RichMessageUtils.files(message).get(i).id);
  }

  @Test public void thinkingIsSeparateFromBubbleTextAndDisappearsOnReplacement () {
    TdApi.RichMessage pending = message(paragraph("Partial"), new TdApi.PageBlockThinking(plain("Generating")));
    assertTrue(RichMessageUtils.isThinking(pending));
    assertEquals("Partial", RichMessageUtils.plainText(RichMessageUtils.previewRichText(pending)));
    assertFalse(RichMessageUtils.isThinking(message(paragraph("Done"))));
  }

  @Test public void diffKeepsBothVersionsAndUtf16EntityOffsets () {
    TdApi.RichMessage message = message(new TdApi.PageBlockParagraph(new TdApi.RichTextDiff(plain("🧪 new"), plain("old"))));
    TdApi.FormattedText text = RichMessageUtils.text(message);
    assertEquals("old 🧪 new", text.text);
    assertEquals(2, text.entities.length);
    assertTrue(text.entities[0].type instanceof TdApi.TextEntityTypeStrikethrough);
    assertEquals(3, text.entities[0].length);
    assertTrue(text.entities[1].type instanceof TdApi.TextEntityTypeBold);
    assertEquals(4, text.entities[1].offset);
    assertEquals(6, text.entities[1].length);
    TdApi.FormattedText deleted = RichMessageUtils.text(message(new TdApi.PageBlockParagraph(new TdApi.RichTextDiff(plain(""), plain("removed")))));
    assertEquals("removed", deleted.text);
    assertEquals(1, deleted.entities.length);
    assertEquals(deleted.text.length(), deleted.entities[0].length);
  }

  @Test public void nestedStylesAndSpoilersHaveSortedValidEntityRanges () {
    TdApi.RichText styled = new TdApi.RichTextBold(new TdApi.RichTexts(new TdApi.RichText[] {
      plain("a "), new TdApi.RichTextSpoiler(new TdApi.RichTextItalic(plain("secret"))), plain(" link")
    }));
    TdApi.FormattedText text = RichMessageUtils.text(message(new TdApi.PageBlockParagraph(styled)));
    assertEquals("a secret link", text.text);
    assertEquals(0, text.entities[0].offset);
    assertEquals(text.text.length(), text.entities[0].length);
    assertTrue(text.entities[0].type instanceof TdApi.TextEntityTypeBold);
    boolean spoiler = false;
    for (TdApi.TextEntity entity : text.entities) {
      assertTrue(entity.offset >= 0 && entity.offset + entity.length <= text.text.length());
      if (entity.type instanceof TdApi.TextEntityTypeSpoiler) {
        spoiler = true; assertEquals("secret", text.text.substring(entity.offset, entity.offset + entity.length));
      }
    }
    assertTrue(spoiler);
  }

  @Test public void formulasReferencesDatesAndEmojiKeepVisibleContent () {
    TdApi.RichTextDateTime date = new TdApi.RichTextDateTime(); date.text = plain("Today"); date.unixTime = 123;
    TdApi.RichTextCustomEmoji emoji = new TdApi.RichTextCustomEmoji(); emoji.alternativeText = "🐀"; emoji.customEmojiId = 9L;
    TdApi.RichTextMathematicalExpression formula = new TdApi.RichTextMathematicalExpression(); formula.expression = "x^2";
    TdApi.FormattedText text = RichMessageUtils.text(message(new TdApi.PageBlockParagraph(new TdApi.RichTexts(new TdApi.RichText[] {
      new TdApi.RichTextReference("ref", plain("Reference")), plain(" "), date, plain(" "), emoji, plain(" "), formula
    })), new TdApi.PageBlockMathematicalExpression("\\frac{a}{b}")));
    assertEquals("Reference Today 🐀 x^2\n\\frac{a}{b}", text.text);
    assertEquals(4, text.entities.length);
    assertEquals(123, ((TdApi.TextEntityTypeDateTime) text.entities[0].type).unixTime);
    assertEquals(2, text.entities[1].length);
    assertEquals(9L, ((TdApi.TextEntityTypeCustomEmoji) text.entities[1].type).customEmojiId);
    assertTrue(text.entities[2].type instanceof TdApi.TextEntityTypeCode);
    assertTrue(text.entities[3].type instanceof TdApi.TextEntityTypeCode);
  }

  @Test public void labeledLinksPreserveTheirTargets () {
    TdApi.RichTextEmailAddress email = new TdApi.RichTextEmailAddress(plain("Mail"), "someone@example.org");
    TdApi.RichTextPhoneNumber phone = new TdApi.RichTextPhoneNumber(plain("Call"), "+1234567890");
    TdApi.RichTextUrl url = new TdApi.RichTextUrl(plain("Site"), "https://example.org", false);
    TdApi.FormattedText text = RichMessageUtils.text(message(new TdApi.PageBlockParagraph(new TdApi.RichTexts(new TdApi.RichText[] {email, phone, url}))));
    assertEquals("MailCallSite", text.text);
    assertEquals("mailto:someone@example.org", ((TdApi.TextEntityTypeTextUrl) text.entities[0].type).url);
    assertEquals("tel:+1234567890", ((TdApi.TextEntityTypeTextUrl) text.entities[1].type).url);
    assertEquals("https://example.org", ((TdApi.TextEntityTypeTextUrl) text.entities[2].type).url);
  }

  @Test public void truncationDoesNotSplitEmojiOrLeaveOutOfBoundsEntities () {
    char[] prefix = new char[32767]; Arrays.fill(prefix, 'a');
    String large = new String(prefix) + "🐀tail";
    TdApi.FormattedText text = RichMessageUtils.text(message(new TdApi.PageBlockParagraph(new TdApi.RichTextBold(plain(large)))));
    assertEquals(32767, text.text.length());
    assertFalse(Character.isHighSurrogate(text.text.charAt(text.text.length() - 1)));
    assertEquals(text.text.length(), text.entities[0].length);
  }

  @Test(timeout = 1000) public void recursiveContentAndBlockCountsAreBounded () {
    TdApi.PageBlockCover cycle = new TdApi.PageBlockCover(); cycle.cover = cycle;
    assertTrue(RichMessageUtils.leafBlocks(message(cycle)).isEmpty());
    TdApi.RichTextBold richCycle = new TdApi.RichTextBold(); richCycle.text = richCycle;
    assertEquals("", RichMessageUtils.plainText(richCycle));
    TdApi.PageBlock[] many = new TdApi.PageBlock[4096]; Arrays.fill(many, paragraph("x"));
    assertEquals(1024, RichMessageUtils.leafBlocks(message(many)).size());
  }

  @Test public void unknownBlocksAndMissingMediaDoNotHideFollowingText () {
    TdApi.PageBlock unknown = new TdApi.PageBlock() { @Override public int getConstructor () { return 42; } };
    TdApi.RichMessage message = message(unknown, new TdApi.PageBlockUnsupported(), new TdApi.PageBlockPhoto(null, null, null, false), paragraph("Supported"));
    assertEquals("Supported", RichMessageUtils.text(message).text);
    assertEquals(0, RichMessageUtils.mediaCount(message));
    assertNull(RichMessageUtils.firstFile(message));
    assertEquals("", RichMessageUtils.text(null).text);
  }

  @Test public void emptyCaptionsDoNotCreateFalseTextInMediaOnlyMessages () {
    TdApi.PageBlockPhoto photo = photo(1); photo.caption = new TdApi.PageBlockCaption(plain(""), plain(""));
    assertEquals("", RichMessageUtils.text(message(photo)).text);
  }

  @Test public void tablesIncludeTheirCaptionAndCellText () {
    TdApi.PageBlockTableCell a = new TdApi.PageBlockTableCell(); a.text = plain("A");
    TdApi.PageBlockTableCell b = new TdApi.PageBlockTableCell(); b.text = new TdApi.RichTextBold(plain("B"));
    TdApi.PageBlockTable table = new TdApi.PageBlockTable(); table.caption = plain("Table"); table.cells = new TdApi.PageBlockTableCell[][] {{a, b}};
    TdApi.FormattedText text = RichMessageUtils.text(message(table));
    assertEquals("Table\nA\tB\t\n", text.text);
    assertEquals(8, text.entities[0].offset);
    assertEquals(1, text.entities[0].length);
  }
}
