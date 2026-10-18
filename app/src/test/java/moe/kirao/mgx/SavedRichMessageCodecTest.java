package moe.kirao.mgx;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;

import moe.kirao.mgx.utils.SavedRichMessageCodec;

import static org.junit.Assert.*;

public class SavedRichMessageCodecTest {
  private TdApi.Message message (TdApi.FormattedText text) {
    TdApi.Message message = new TdApi.Message();
    message.id = 1048576; message.chatId = 77; message.senderId = new TdApi.MessageSenderUser(77);
    message.content = new TdApi.MessageText(text, null, null);
    return message;
  }

  @Test public void sourceIsOrdinaryTelegramTextWithLiteralMarkdown () {
    String source = "# Header 🐀\n\n**bold** __italic__\n\n```java\n**literal**\n```";
    TdApi.Message message = message(SavedRichMessageCodec.marked(source, false));
    assertTrue(SavedRichMessageCodec.recognizes(message, 77));
    TdApi.FormattedText text = ((TdApi.MessageText) message.content).text;
    assertEquals(source, text.text);
    assertEquals(1, text.entities.length);
    assertEquals(0, text.entities[0].offset);
    assertEquals(source.length(), text.entities[0].length);
    assertTrue(text.entities[0].type instanceof TdApi.TextEntityTypePreCode);
    assertFalse(SavedRichMessageCodec.isRtl(message.content));
  }

  @Test public void sourceIsNeverInterpretedInAnotherChatOrAccount () {
    TdApi.Message message = message(SavedRichMessageCodec.marked("# Header", false));
    assertFalse(SavedRichMessageCodec.recognizes(message, 78));
    message.chatId = 78;
    assertFalse(SavedRichMessageCodec.recognizes(message, 78));
    message.senderId = new TdApi.MessageSenderChat(78);
    assertFalse(SavedRichMessageCodec.recognizes(message, 78));
  }

  @Test public void ordinaryMarkdownAndUnrelatedCodeBlocksStayOrdinary () {
    assertFalse(SavedRichMessageCodec.recognizes(message(new TdApi.FormattedText("# Header", null)), 77));
    TdApi.FormattedText text = SavedRichMessageCodec.marked("# Header", false);
    text.entities[0].type = new TdApi.TextEntityTypePreCode("markdown");
    assertFalse(SavedRichMessageCodec.recognizes(message(text), 77));
    text.entities[0].type = new TdApi.TextEntityTypePreCode("ratogramx-rich-v2");
    assertFalse(SavedRichMessageCodec.recognizes(message(text), 77));
  }

  @Test public void partialOrMixedEntityMarkersAreNotAccepted () {
    TdApi.FormattedText text = SavedRichMessageCodec.marked("# Header", false);
    text.entities[0].length--;
    assertFalse(SavedRichMessageCodec.recognizes(message(text), 77));
    text = SavedRichMessageCodec.marked("# Header", false);
    text.entities = new TdApi.TextEntity[] {text.entities[0], new TdApi.TextEntity(2, 3, new TdApi.TextEntityTypeBold())};
    assertFalse(SavedRichMessageCodec.recognizes(message(text), 77));
  }

  @Test public void documentFallbackRequiresBothTheMarkerAndBoundedMarkdownFile () {
    TdApi.Message message = message(SavedRichMessageCodec.marked("# RTL", true));
    TdApi.MessageDocument content = new TdApi.MessageDocument();
    content.caption = ((TdApi.MessageText) message.content).text;
    content.document = new TdApi.Document();
    content.document.fileName = SavedRichMessageCodec.FILE_PREFIX + "fixture.md";
    content.document.document = new TdApi.File();
    content.document.document.size = 9000;
    message.content = content;
    assertTrue(SavedRichMessageCodec.recognizes(message, 77));
    assertTrue(SavedRichMessageCodec.isRtl(content));
    content.document.fileName = "ordinary.md";
    assertFalse(SavedRichMessageCodec.recognizes(message, 77));
    content.document.fileName = SavedRichMessageCodec.FILE_PREFIX + "fixture.md";
    content.document.document.expectedSize = SavedRichMessageCodec.MAX_FILE_BYTES + 1;
    assertFalse(SavedRichMessageCodec.recognizes(message, 77));
  }

  @Test public void photoReferenceReplacementPreservesTextCaptionsAndLineEndings () {
    String source = "# photo-1 🐀\r\n\r\n![**caption**](attachment:photo-1)\r\n\r\ntext attachment:photo-1\n![](attachment:photo-2)";
    String replaced = SavedRichMessageCodec.replacePhotos(source, Map.of("photo-1", "tg_2097152"));
    assertEquals(source.replace("![**caption**](attachment:photo-1)", "![**caption**](attachment:tg_2097152)"), replaced);
    assertEquals(new LinkedHashSet<>(Arrays.asList("tg_2097152", "photo-2")), SavedRichMessageCodec.photoNames(replaced));
  }

  @Test public void codeExamplesAndQuotedImagesAreNotAttachments () {
    String source = "```md\n![](attachment:photo-1)\n```\n\n> ![](attachment:photo-1)\n\n![](attachment:photo-1)\n\n~~~~\n![](attachment:photo-2)\n~~~\n![](attachment:photo-3)";
    assertEquals(new LinkedHashSet<>(Arrays.asList("photo-1")), SavedRichMessageCodec.photoNames(source));
    assertEquals(source.replace("\n\n![](attachment:photo-1)", "\n\n![](attachment:tg_1)"),
      SavedRichMessageCodec.replacePhotos(source, Map.of("photo-1", "tg_1")));
  }

  @Test public void photoIdentityIsStableAndRejectsNonCanonicalValues () {
    assertEquals(123456789123456L, SavedRichMessageCodec.photoMessageId(SavedRichMessageCodec.photoName(123456789123456L)));
    for (String name : new String[] {"photo-1", "tg_0", "tg_-1", "tg_01", "tg_9223372036854775808", "tg_+1"}) {
      try { SavedRichMessageCodec.photoMessageId(name); fail(name); } catch (IllegalArgumentException expected) { }
    }
  }

  @Test public void captionTruncationNeverSplitsUnicodeSurrogates () {
    assertEquals("a", SavedRichMessageCodec.excerpt("a🐀b", 2));
    assertEquals("a🐀", SavedRichMessageCodec.excerpt("a🐀b", 3));
    assertEquals("a🐀b", SavedRichMessageCodec.excerpt("a🐀b", 512));
  }
}
