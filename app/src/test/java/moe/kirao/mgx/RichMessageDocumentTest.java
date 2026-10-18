package moe.kirao.mgx;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import moe.kirao.mgx.utils.RichMessageDocument;
import moe.kirao.mgx.utils.RichMessagePolicy;
import moe.kirao.mgx.utils.RichMessageUtils;

import static org.junit.Assert.*;

public class RichMessageDocumentTest {
  private RichMessageDocument document () {
    RichMessageDocument d = new RichMessageDocument();
    RichMessageDocument.Block p = new RichMessageDocument.Block(RichMessageDocument.PARAGRAPH);
    p.text = "Hello 🐀\nsecond line"; d.blocks.add(p); return d;
  }

  @Test public void localPremiumNeverGrantsTelegramSendInOtherChats () {
    assertEquals(RichMessagePolicy.Mode.LOCAL, RichMessagePolicy.creationMode(false, true, true, true, false));
    assertEquals(RichMessagePolicy.Mode.UNAVAILABLE, RichMessagePolicy.creationMode(false, true, false, true, false));
    assertEquals(RichMessagePolicy.Mode.TELEGRAM, RichMessagePolicy.creationMode(true, false, false, true, false));
    assertEquals(RichMessagePolicy.Mode.TELEGRAM, RichMessagePolicy.creationMode(true, true, true, true, false));
    assertEquals(RichMessagePolicy.Mode.UNAVAILABLE, RichMessagePolicy.creationMode(true, true, false, false, false));
    assertEquals(RichMessagePolicy.Mode.UNAVAILABLE, RichMessagePolicy.creationMode(true, true, false, true, true));
  }

  @Test public void persistedLocalMessagesRemainEditableWithoutExperiment () {
    assertTrue(RichMessagePolicy.canEdit(true, false, false, false));
    assertFalse(RichMessagePolicy.canEdit(false, false, true, true));
    assertFalse(RichMessagePolicy.canEdit(false, true, true, false));
    assertTrue(RichMessagePolicy.canEdit(false, true, true, true));
    assertTrue(RichMessagePolicy.isLocalId(RichMessagePolicy.localId(1234)));
    assertFalse(RichMessagePolicy.isLocalId(-1234));
    assertFalse(RichMessagePolicy.isLocalId(1234));
  }

  @Test public void structuredInputMatchesPreviewForAllCreatedBlocks () {
    RichMessageDocument d = document();
    for (int type = RichMessageDocument.HEADING; type <= RichMessageDocument.CODE; type++) {
      RichMessageDocument.Block b = new RichMessageDocument.Block(type); b.text = "one\ntwo";
      if (type == RichMessageDocument.HEADING) b.size = 6;
      if (type == RichMessageDocument.CODE) b.extra = "java";
      if (type == RichMessageDocument.LIST) b.ordered = true;
      d.blocks.add(b);
    }
    TdApi.InputPageBlock[] blocks = ((TdApi.RichMessageSourceBlocks) d.input().message.source).blocks;
    assertTrue(blocks[0] instanceof TdApi.InputPageBlockParagraph);
    assertEquals(6, ((TdApi.InputPageBlockSectionHeading) blocks[1]).size);
    assertEquals(2, ((TdApi.InputPageBlockList) blocks[2]).items.length);
    assertEquals("1", ((TdApi.InputPageBlockList) blocks[2]).items[0].type);
    assertTrue(blocks[3] instanceof TdApi.InputPageBlockBlockQuote);
    assertEquals("java", ((TdApi.InputPageBlockPreformatted) blocks[4]).language);
    assertTrue(d.output().isFull);
    assertTrue(RichMessageUtils.text(d.output()).text.startsWith("Hello 🐀"));
  }

  @Test public void codecKeepsUnicodeLayoutAndPrivateImagePath () throws Exception {
    RichMessageDocument d = document(); d.isRtl = true;
    RichMessageDocument.Block photo = new RichMessageDocument.Block(RichMessageDocument.PHOTO);
    photo.photoPath = "/private/account-1/image.jpg"; photo.width = 640; photo.height = 480; photo.text = "Caption";
    d.blocks.add(photo);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream(); d.write(new DataOutputStream(buffer));
    RichMessageDocument restored = RichMessageDocument.read(new DataInputStream(new ByteArrayInputStream(buffer.toByteArray())));
    assertTrue(restored.isRtl);
    assertEquals(d.blocks.get(0).text, restored.blocks.get(0).text);
    assertEquals(photo.photoPath, restored.blocks.get(1).photoPath);
    assertEquals(640, restored.blocks.get(1).width);
    TdApi.InputPageBlockPhoto input = (TdApi.InputPageBlockPhoto) ((TdApi.RichMessageSourceBlocks) restored.input().message.source).blocks[1];
    assertEquals(photo.photoPath, ((TdApi.InputFileLocal) input.photo.photo).path);
  }

  @Test public void editingCopyDoesNotMutatePersistedDocument () {
    RichMessageDocument original = document(), edited = original.copy();
    edited.blocks.get(0).text = "Edited";
    assertEquals("Hello 🐀\nsecond line", original.blocks.get(0).text);
  }

  @Test public void unchangedStylesAndListMetadataSurviveEditingAnotherBlock () {
    TdApi.RichText styled = new TdApi.RichTextBold(new TdApi.RichTextPlain("Bold"));
    TdApi.PageBlockParagraph paragraph = new TdApi.PageBlockParagraph(styled);
    TdApi.PageBlockListItem item = new TdApi.PageBlockListItem("a.", new TdApi.PageBlock[] {paragraph}, true, true, 1, "a");
    RichMessageDocument d = RichMessageDocument.fromRichMessage(new TdApi.RichMessage(new TdApi.PageBlock[] {
      paragraph, new TdApi.PageBlockList(new TdApi.PageBlockListItem[] {item})}, false, true));
    d.blocks.add(document().blocks.get(0));
    TdApi.InputPageBlock[] input = ((TdApi.RichMessageSourceBlocks) d.input().message.source).blocks;
    assertSame(styled, ((TdApi.InputPageBlockParagraph) input[0]).text);
    TdApi.InputPageBlockListItem restored = ((TdApi.InputPageBlockList) input[1]).items[0];
    assertTrue(restored.hasCheckbox && restored.isChecked);
    assertEquals("a", restored.type);
    d.blocks.get(1).ordered = false;
    restored = ((TdApi.InputPageBlockList) ((TdApi.RichMessageSourceBlocks) d.input().message.source).blocks[1]).items[0];
    assertEquals("", restored.type);
    assertSame(styled, ((TdApi.InputPageBlockParagraph) restored.blocks[0]).text);
  }

  @Test(expected = IllegalArgumentException.class) public void unsupportedBlockIsRejectedInsteadOfLost () {
    RichMessageDocument.fromRichMessage(new TdApi.RichMessage(new TdApi.PageBlock[] {new TdApi.PageBlockDivider()}, false, true));
  }
  @Test(expected = IllegalArgumentException.class) public void incompleteMessageCannotBeEdited () {
    RichMessageDocument.fromRichMessage(new TdApi.RichMessage(new TdApi.PageBlock[0], false, false));
  }
  @Test(expected = IllegalArgumentException.class) public void deeplyNestedRemoteBlocksAreBounded () {
    TdApi.PageBlock block = new TdApi.PageBlockParagraph(new TdApi.RichTextPlain("text"));
    for (int i = 0; i < 40; i++) block = new TdApi.PageBlockBlockQuote(new TdApi.PageBlock[] {block}, null);
    RichMessageDocument.fromRichMessage(new TdApi.RichMessage(new TdApi.PageBlock[] {block}, false, true));
  }
  @Test(expected = IllegalArgumentException.class) public void emptyContentCannotBeSaved () {
    RichMessageDocument d = document(); d.blocks.get(0).text = " \n "; d.validate();
  }
  @Test(expected = IllegalArgumentException.class) public void listItemsCountTowardBlockLimit () {
    RichMessageDocument d = document(); RichMessageDocument.Block list = new RichMessageDocument.Block(RichMessageDocument.LIST);
    list.text = "line\n".repeat(50); d.blocks.add(list); d.validate();
  }
  @Test(expected = IOException.class) public void corruptedLengthIsRejectedBeforeAllocation () throws Exception {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(buffer);
    out.writeBoolean(false); out.writeInt(1); out.writeInt(0); out.writeInt(Integer.MAX_VALUE);
    RichMessageDocument.read(new DataInputStream(new ByteArrayInputStream(buffer.toByteArray())));
  }
}
