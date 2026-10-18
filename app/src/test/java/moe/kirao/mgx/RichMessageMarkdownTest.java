package moe.kirao.mgx;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import moe.kirao.mgx.utils.RichMessageDocument;
import moe.kirao.mgx.utils.RichMessageMarkdown;
import moe.kirao.mgx.utils.RichMessageUtils;

import static org.junit.Assert.*;

public class RichMessageMarkdownTest {
  // Block parsing is independent of native inline parsing, exercised on Android.
  private RichMessageDocument parse (String text) {
    return RichMessageMarkdown.parse(text, new RichMessageMarkdown.Source(text), false, TdApi.RichTextPlain::new);
  }

  @Test public void sourceBuildsStructuredHeadingsListsQuotesAndParagraphs () {
    RichMessageDocument document = parse("# Title\n\nParagraph\nsecond line\n\n> Quote\n>\n> - nested\n\n3. Third\n  - child\n4. [x] Fourth");
    TdApi.InputPageBlock[] blocks = ((TdApi.RichMessageSourceBlocks) document.input().message.source).blocks;
    assertEquals(4, blocks.length);
    assertEquals(1, ((TdApi.InputPageBlockSectionHeading) blocks[0]).size);
    assertEquals("Paragraph\nsecond line", RichMessageUtils.plainText(((TdApi.InputPageBlockParagraph) blocks[1]).text));
    assertEquals(2, ((TdApi.InputPageBlockBlockQuote) blocks[2]).blocks.length);
    TdApi.InputPageBlockListItem[] items = ((TdApi.InputPageBlockList) blocks[3]).items;
    assertEquals(3, items[0].value);
    assertTrue(items[0].blocks[1] instanceof TdApi.InputPageBlockList);
    assertTrue(items[1].hasCheckbox && items[1].isChecked);
    TdApi.PageBlockList list = (TdApi.PageBlockList) document.output().blocks[3];
    assertEquals("4. ☑", list.items[1].label);
    assertEquals("•", ((TdApi.PageBlockList) list.items[0].blocks[1]).items[0].label);
  }

  @Test public void fencedCodeRetainsLeadingBlankLinesAndFormattingCharacters () {
    TdApi.PageBlockPreformatted block = (TdApi.PageBlockPreformatted) parse("````java\n\n**literal**\n```\n\n````").output().blocks[0];
    assertEquals("\n**literal**\n```\n", RichMessageUtils.plainText(block.text));
    assertEquals("java", block.language);
  }

  @Test public void unclosedFenceCanStillBePreviewedWhileWriting () {
    TdApi.PageBlockPreformatted block = (TdApi.PageBlockPreformatted) parse("~~~text\na\nb").output().blocks[0];
    assertEquals("a\nb", RichMessageUtils.plainText(block.text));
  }

  @Test public void photoRegistryKeepsPrivatePathAndIgnoresUnusedAttachments () {
    RichMessageDocument document = parse("Before");
    RichMessageDocument.Block photo = new RichMessageDocument.Block(RichMessageDocument.PHOTO);
    photo.photoPath = "/private/account/photo.jpg"; photo.width = 96; photo.height = 64;
    document.blocks.add(photo);
    document.markdownSource = null;
    RichMessageMarkdown.Source source = RichMessageMarkdown.source(document);
    assertEquals(1, source.photos.size());
    assertEquals(photo.photoPath, source.photos.get("photo-1").photoPath);
    RichMessageDocument restored = RichMessageMarkdown.parse(source.text, source, false, TdApi.RichTextPlain::new);
    assertEquals(photo.photoPath, restored.blocks.get(1).photoPath);
    assertEquals("photo-1", restored.blocks.get(1).extra);
    assertEquals(photo.photoPath, ((TdApi.InputFileLocal) ((TdApi.InputPageBlockPhoto)
      ((TdApi.RichMessageSourceBlocks) restored.input().message.source).blocks[1]).photo.photo).path);
    assertEquals(1, RichMessageMarkdown.parse("Before", source, false, TdApi.RichTextPlain::new).blocks.size());
    assertEquals("", photo.extra);
  }

  @Test public void unchangedRemoteListKeepsItsNumberingAndCheckboxMetadata () {
    TdApi.PageBlockListItem item = new TdApi.PageBlockListItem("b.", new TdApi.PageBlock[] {
      new TdApi.PageBlockParagraph(new TdApi.RichTextBold(new TdApi.RichTextPlain("Item")))}, true, true, 2, "a");
    RichMessageDocument document = RichMessageDocument.fromRichMessage(new TdApi.RichMessage(new TdApi.PageBlock[] {
      new TdApi.PageBlockParagraph(new TdApi.RichTextPlain("Before")), new TdApi.PageBlockList(new TdApi.PageBlockListItem[] {item})}, false, true));
    RichMessageMarkdown.Source source = RichMessageMarkdown.source(document);
    RichMessageDocument edited = RichMessageMarkdown.parse(source.text.replace("Before", "Changed"), source, false, TdApi.RichTextPlain::new);
    TdApi.InputPageBlockListItem actual = ((TdApi.InputPageBlockList) ((TdApi.RichMessageSourceBlocks) edited.input().message.source).blocks[1]).items[0];
    assertEquals("a", actual.type);
    assertEquals(2, actual.value);
    assertTrue(actual.isChecked && actual.hasCheckbox);
    assertTrue(((TdApi.InputPageBlockParagraph) actual.blocks[0]).text instanceof TdApi.RichTextBold);
  }

  @Test public void overlappingEntitiesKeepUtf16OffsetsAndStyles () {
    TdApi.RichText rich = RichMessageMarkdown.fromFormattedText(new TdApi.FormattedText("🐀AB", new TdApi.TextEntity[] {
      new TdApi.TextEntity(0, 3, new TdApi.TextEntityTypeBold()),
      new TdApi.TextEntity(2, 2, new TdApi.TextEntityTypeItalic())}));
    TdApi.RichText[] parts = ((TdApi.RichTexts) rich).texts;
    assertEquals("🐀", RichMessageUtils.plainText(parts[0]));
    assertTrue(parts[0] instanceof TdApi.RichTextBold);
    assertTrue(parts[1] instanceof TdApi.RichTextBold);
    assertTrue(((TdApi.RichTextBold) parts[1]).text instanceof TdApi.RichTextItalic);
    assertTrue(parts[2] instanceof TdApi.RichTextItalic);
    assertEquals("🐀AB", RichMessageUtils.plainText(rich));
  }

  @Test public void legacyCodecLoadsBeforeMarkdownMigration () throws Exception {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(buffer);
    out.writeBoolean(true); out.writeInt(1); out.writeInt(RichMessageDocument.PARAGRAPH);
    for (String value : new String[] {"Old message 🐀", "", ""}) {
      byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      out.writeInt(bytes.length); out.write(bytes);
    }
    out.writeInt(2); out.writeInt(0); out.writeInt(0); out.writeBoolean(false);
    RichMessageDocument document = RichMessageDocument.read(new DataInputStream(new ByteArrayInputStream(buffer.toByteArray())), 1);
    assertNull(document.markdownSource);
    assertTrue(document.isRtl);
    assertEquals("Old message 🐀", RichMessageMarkdown.source(document).text);
    ByteArrayOutputStream migrated = new ByteArrayOutputStream(); document.write(new DataOutputStream(migrated));
    assertEquals("Old message 🐀", RichMessageDocument.read(new DataInputStream(new ByteArrayInputStream(migrated.toByteArray()))).blocks.get(0).text);
  }

  @Test(expected = IllegalArgumentException.class) public void missingPhotoReferenceIsRejected () { parse("![](attachment:missing)"); }

  @Test(expected = IllegalArgumentException.class) public void nestedPhotoIsRejectedBeforeItsPrivatePathCanBeLost () { parse("> ![](attachment:missing)"); }

  @Test(expected = IllegalArgumentException.class) public void legacyMultilineCaptionCannotSilentlyTurnItsPhotoIntoText () {
    RichMessageDocument document = new RichMessageDocument();
    RichMessageDocument.Block photo = new RichMessageDocument.Block(RichMessageDocument.PHOTO);
    photo.text = "First line\nSecond line"; photo.photoPath = "/private/photo.jpg";
    document.blocks.add(photo);
    RichMessageMarkdown.source(document);
  }

  @Test(expected = IllegalArgumentException.class) public void excessiveNestingIsBounded () { parse("> ".repeat(20) + "text"); }

  @Test(expected = IllegalArgumentException.class) public void listItemsCountTowardStructuredBlockLimit () { parse("- item\n".repeat(51)); }

  @Test(expected = IllegalArgumentException.class) public void sourceLengthIsBounded () { parse("a".repeat(RichMessageMarkdown.MAX_SOURCE + 1)); }

  @Test(expected = IllegalArgumentException.class) public void longParagraphCannotBypassThePreviewTextLimit () { parse("a".repeat(RichMessageDocument.MAX_TEXT + 1)); }

  @Test(expected = IllegalArgumentException.class) public void invalidEntityRangeIsRejected () {
    RichMessageMarkdown.fromFormattedText(new TdApi.FormattedText("🐀", new TdApi.TextEntity[] {
      new TdApi.TextEntity(1, Integer.MAX_VALUE, new TdApi.TextEntityTypeBold())}));
  }
}
