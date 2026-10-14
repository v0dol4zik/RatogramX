package moe.kirao.mgx.utils;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** Bounded text and media representations for chat lists, notifications and bubbles. */
public final class RichMessageUtils {
  private static final int MAX_DEPTH = 32, MAX_BLOCKS = 1024, MAX_TEXT = 32768;
  private RichMessageUtils () { }

  public static List<TdApi.PageBlock> leafBlocks (@Nullable TdApi.RichMessage message) {
    List<TdApi.PageBlock> result = new ArrayList<>();
    if (message != null) collect(message.blocks, result, 0, new int[] {MAX_BLOCKS});
    return result;
  }

  private static void collect (@Nullable TdApi.PageBlock[] blocks, List<TdApi.PageBlock> result, int depth, int[] budget) {
    if (blocks == null || depth >= MAX_DEPTH) return;
    for (TdApi.PageBlock block : blocks) {
      if (budget[0]-- <= 0) return;
      if (block == null) continue;
      switch (block.getConstructor()) {
        case TdApi.PageBlockCover.CONSTRUCTOR: collect(new TdApi.PageBlock[] {((TdApi.PageBlockCover) block).cover}, result, depth + 1, budget); break;
        case TdApi.PageBlockCollage.CONSTRUCTOR:
          collect(((TdApi.PageBlockCollage) block).blocks, result, depth + 1, budget);
          addCaption(((TdApi.PageBlockCollage) block).caption, result);
          break;
        case TdApi.PageBlockSlideshow.CONSTRUCTOR:
          collect(((TdApi.PageBlockSlideshow) block).blocks, result, depth + 1, budget);
          addCaption(((TdApi.PageBlockSlideshow) block).caption, result);
          break;
        case TdApi.PageBlockEmbeddedPost.CONSTRUCTOR:
          collect(((TdApi.PageBlockEmbeddedPost) block).blocks, result, depth + 1, budget);
          addCaption(((TdApi.PageBlockEmbeddedPost) block).caption, result);
          break;
        case TdApi.PageBlockBlockQuote.CONSTRUCTOR:
          collect(((TdApi.PageBlockBlockQuote) block).blocks, result, depth + 1, budget);
          TdApi.RichText credit = ((TdApi.PageBlockBlockQuote) block).credit;
          if (credit != null) result.add(new TdApi.PageBlockParagraph(credit));
          break;
        case TdApi.PageBlockDetails.CONSTRUCTOR:
          result.add(new TdApi.PageBlockParagraph(((TdApi.PageBlockDetails) block).header));
          collect(((TdApi.PageBlockDetails) block).blocks, result, depth + 1, budget);
          break;
        case TdApi.PageBlockList.CONSTRUCTOR:
          TdApi.PageBlockListItem[] items = ((TdApi.PageBlockList) block).items;
          if (items != null) for (TdApi.PageBlockListItem item : items) {
            if (budget[0]-- <= 0) return;
            if (item != null) collect(item.blocks, result, depth + 1, budget);
          }
          break;
        default: result.add(block); break;
      }
    }
  }

  private static void addCaption (@Nullable TdApi.PageBlockCaption caption, List<TdApi.PageBlock> result) {
    if (caption != null) result.add(new TdApi.PageBlockParagraph(caption(caption)));
  }

  public static @Nullable TdApi.RichText blockText (TdApi.PageBlock block) {
    switch (block.getConstructor()) {
      case TdApi.PageBlockTitle.CONSTRUCTOR: return new TdApi.RichTextBold(((TdApi.PageBlockTitle) block).title);
      case TdApi.PageBlockSubtitle.CONSTRUCTOR: return ((TdApi.PageBlockSubtitle) block).subtitle;
      case TdApi.PageBlockHeader.CONSTRUCTOR: return new TdApi.RichTextBold(((TdApi.PageBlockHeader) block).header);
      case TdApi.PageBlockSubheader.CONSTRUCTOR: return new TdApi.RichTextBold(((TdApi.PageBlockSubheader) block).subheader);
      case TdApi.PageBlockSectionHeading.CONSTRUCTOR: return new TdApi.RichTextBold(((TdApi.PageBlockSectionHeading) block).text);
      case TdApi.PageBlockKicker.CONSTRUCTOR: return ((TdApi.PageBlockKicker) block).kicker;
      case TdApi.PageBlockParagraph.CONSTRUCTOR: return ((TdApi.PageBlockParagraph) block).text;
      case TdApi.PageBlockPreformatted.CONSTRUCTOR: return new TdApi.RichTextFixed(((TdApi.PageBlockPreformatted) block).text);
      case TdApi.PageBlockFooter.CONSTRUCTOR: return ((TdApi.PageBlockFooter) block).footer;
      case TdApi.PageBlockThinking.CONSTRUCTOR: return ((TdApi.PageBlockThinking) block).text;
      case TdApi.PageBlockMathematicalExpression.CONSTRUCTOR: return new TdApi.RichTextFixed(new TdApi.RichTextPlain(((TdApi.PageBlockMathematicalExpression) block).expression));
      case TdApi.PageBlockAuthorDate.CONSTRUCTOR: return ((TdApi.PageBlockAuthorDate) block).author;
      case TdApi.PageBlockExpandableBlockQuote.CONSTRUCTOR: return ((TdApi.PageBlockExpandableBlockQuote) block).text;
      case TdApi.PageBlockPullQuote.CONSTRUCTOR: return ((TdApi.PageBlockPullQuote) block).text;
      case TdApi.PageBlockPhoto.CONSTRUCTOR: return caption(((TdApi.PageBlockPhoto) block).caption);
      case TdApi.PageBlockVideo.CONSTRUCTOR: return caption(((TdApi.PageBlockVideo) block).caption);
      case TdApi.PageBlockAnimation.CONSTRUCTOR: return caption(((TdApi.PageBlockAnimation) block).caption);
      case TdApi.PageBlockAudio.CONSTRUCTOR: return caption(((TdApi.PageBlockAudio) block).caption);
      case TdApi.PageBlockDocument.CONSTRUCTOR: return caption(((TdApi.PageBlockDocument) block).caption);
      case TdApi.PageBlockVoiceNote.CONSTRUCTOR: return caption(((TdApi.PageBlockVoiceNote) block).caption);
      case TdApi.PageBlockTable.CONSTRUCTOR:
        TdApi.PageBlockTable table = (TdApi.PageBlockTable) block;
        List<TdApi.RichText> texts = new ArrayList<>();
        if (table.caption != null) { texts.add(table.caption); texts.add(new TdApi.RichTextPlain("\n")); }
        if (table.cells != null) for (TdApi.PageBlockTableCell[] row : table.cells) {
          if (row == null) continue;
          for (TdApi.PageBlockTableCell cell : row) {
            if (cell != null && cell.text != null) { texts.add(cell.text); texts.add(new TdApi.RichTextPlain("\t")); }
            if (texts.size() >= MAX_BLOCKS) break;
          }
          texts.add(new TdApi.RichTextPlain("\n"));
          if (texts.size() >= MAX_BLOCKS) break;
        }
        return new TdApi.RichTexts(texts.toArray(new TdApi.RichText[0]));
      case TdApi.PageBlockButtonRow.CONSTRUCTOR:
        TdApi.InlineButton[] buttons = ((TdApi.PageBlockButtonRow) block).buttons;
        List<TdApi.RichText> labels = new ArrayList<>();
        if (buttons != null) for (TdApi.InlineButton button : buttons) {
          if (button != null) { if (!labels.isEmpty()) labels.add(new TdApi.RichTextPlain(" ")); labels.add(new TdApi.RichTextButton(button)); }
          if (labels.size() >= MAX_BLOCKS) break;
        }
        return new TdApi.RichTexts(labels.toArray(new TdApi.RichText[0]));
      default: return null;
    }
  }

  private static @Nullable TdApi.RichText caption (@Nullable TdApi.PageBlockCaption caption) {
    if (caption == null) return null;
    if (plainText(caption.text).isEmpty()) return caption.credit;
    if (plainText(caption.credit).isEmpty()) return caption.text;
    return new TdApi.RichTexts(new TdApi.RichText[] {caption.text, new TdApi.RichTextPlain("\n"), caption.credit});
  }

  public static TdApi.RichText previewRichText (TdApi.RichMessage message) {
    List<TdApi.RichText> texts = new ArrayList<>();
    for (TdApi.PageBlock block : leafBlocks(message)) {
      if (block instanceof TdApi.PageBlockThinking) continue;
      TdApi.RichText text = blockText(block);
      if (text != null) {
        if (!texts.isEmpty()) texts.add(new TdApi.RichTextPlain("\n"));
        texts.add(text);
        if (texts.size() >= 15) break;
      }
    }
    return new TdApi.RichTexts(texts.toArray(new TdApi.RichText[0]));
  }

  public static TdApi.FormattedText text (@Nullable TdApi.RichMessage message) {
    TextWriter writer = new TextWriter(MAX_TEXT);
    for (TdApi.PageBlock block : leafBlocks(message)) {
      TdApi.RichText text = blockText(block);
      if (text == null) continue;
      int before = writer.value.length();
      if (before != 0) writer.append("\n");
      int start = writer.value.length();
      writer.write(text, 0);
      if (writer.value.length() == start) writer.value.setLength(before);
      if (writer.value.length() >= MAX_TEXT) break;
    }
    Collections.sort(writer.entities, (a, b) -> a.offset != b.offset ? Integer.compare(a.offset, b.offset) : Integer.compare(b.length, a.length));
    return new TdApi.FormattedText(writer.value.toString(), writer.entities.toArray(new TdApi.TextEntity[0]));
  }

  public static String plainText (@Nullable TdApi.RichText text) {
    TextWriter writer = new TextWriter(MAX_TEXT);
    writer.write(text, 0);
    return writer.value.toString();
  }

  public static boolean isThinking (TdApi.RichMessage message) {
    for (TdApi.PageBlock block : leafBlocks(message)) if (block instanceof TdApi.PageBlockThinking) return true;
    return false;
  }

  public static int mediaCount (TdApi.RichMessage message) {
    int count = 0;
    for (TdApi.PageBlock block : leafBlocks(message)) if (file(block) != null) count++;
    return count;
  }

  public static @Nullable TdApi.File firstFile (TdApi.RichMessage message) {
    for (TdApi.PageBlock block : leafBlocks(message)) {
      TdApi.File file = file(block);
      if (file != null) return file;
    }
    return null;
  }

  public static List<TdApi.File> files (TdApi.RichMessage message) {
    List<TdApi.File> files = new ArrayList<>();
    Set<Integer> ids = new HashSet<>();
    for (TdApi.PageBlock block : leafBlocks(message)) {
      TdApi.File file = file(block);
      if (file != null && ids.add(file.id)) files.add(file);
    }
    return files;
  }

  private static @Nullable TdApi.File file (TdApi.PageBlock block) {
    switch (block.getConstructor()) {
      case TdApi.PageBlockPhoto.CONSTRUCTOR:
        TdApi.Photo photo = ((TdApi.PageBlockPhoto) block).photo;
        TdApi.PhotoSize largest = null;
        if (photo != null && photo.sizes != null) for (TdApi.PhotoSize size : photo.sizes) {
          if (size != null && (largest == null || (long) size.width * size.height > (long) largest.width * largest.height)) largest = size;
        }
        return largest != null ? largest.photo : null;
      case TdApi.PageBlockVideo.CONSTRUCTOR: TdApi.Video video = ((TdApi.PageBlockVideo) block).video; return video != null ? video.video : null;
      case TdApi.PageBlockAnimation.CONSTRUCTOR: TdApi.Animation animation = ((TdApi.PageBlockAnimation) block).animation; return animation != null ? animation.animation : null;
      case TdApi.PageBlockAudio.CONSTRUCTOR: TdApi.Audio audio = ((TdApi.PageBlockAudio) block).audio; return audio != null ? audio.audio : null;
      case TdApi.PageBlockDocument.CONSTRUCTOR: TdApi.Document document = ((TdApi.PageBlockDocument) block).document; return document != null ? document.document : null;
      case TdApi.PageBlockVoiceNote.CONSTRUCTOR: TdApi.VoiceNote voice = ((TdApi.PageBlockVoiceNote) block).voiceNote; return voice != null ? voice.voice : null;
      default: return null;
    }
  }

  private static final class TextWriter {
    final StringBuilder value = new StringBuilder();
    final List<TdApi.TextEntity> entities = new ArrayList<>();
    final int limit;
    int budget = MAX_TEXT;
    TextWriter (int limit) { this.limit = limit; }

    void append (@Nullable String text) {
      if (text == null) return;
      int length = Math.min(text.length(), limit - value.length());
      if (length > 0 && length < text.length() && Character.isHighSurrogate(text.charAt(length - 1)) && Character.isLowSurrogate(text.charAt(length))) length--;
      if (length > 0) value.append(text, 0, length);
    }

    void write (@Nullable TdApi.RichText text, int depth) {
      if (text == null || depth >= MAX_DEPTH || budget-- <= 0 || value.length() >= limit) return;
      int start = value.length();
      TdApi.RichText child = null;
      TdApi.TextEntityType entity = null;
      switch (text.getConstructor()) {
        case TdApi.RichTextPlain.CONSTRUCTOR: append(((TdApi.RichTextPlain) text).text); break;
        case TdApi.RichTexts.CONSTRUCTOR:
          TdApi.RichText[] parts = ((TdApi.RichTexts) text).texts;
          if (parts != null) for (TdApi.RichText part : parts) { write(part, depth + 1); if (budget <= 0 || value.length() >= limit) break; }
          break;
        case TdApi.RichTextBold.CONSTRUCTOR: child = ((TdApi.RichTextBold) text).text; entity = new TdApi.TextEntityTypeBold(); break;
        case TdApi.RichTextItalic.CONSTRUCTOR: child = ((TdApi.RichTextItalic) text).text; entity = new TdApi.TextEntityTypeItalic(); break;
        case TdApi.RichTextUnderline.CONSTRUCTOR: child = ((TdApi.RichTextUnderline) text).text; entity = new TdApi.TextEntityTypeUnderline(); break;
        case TdApi.RichTextStrikethrough.CONSTRUCTOR: child = ((TdApi.RichTextStrikethrough) text).text; entity = new TdApi.TextEntityTypeStrikethrough(); break;
        case TdApi.RichTextSpoiler.CONSTRUCTOR: child = ((TdApi.RichTextSpoiler) text).text; entity = new TdApi.TextEntityTypeSpoiler(); break;
        case TdApi.RichTextFixed.CONSTRUCTOR: child = ((TdApi.RichTextFixed) text).text; entity = new TdApi.TextEntityTypeCode(); break;
        case TdApi.RichTextMarked.CONSTRUCTOR: child = ((TdApi.RichTextMarked) text).text; entity = new TdApi.TextEntityTypeBold(); break;
        case TdApi.RichTextSubscript.CONSTRUCTOR: child = ((TdApi.RichTextSubscript) text).text; break;
        case TdApi.RichTextSuperscript.CONSTRUCTOR: child = ((TdApi.RichTextSuperscript) text).text; break;
        case TdApi.RichTextUrl.CONSTRUCTOR: child = ((TdApi.RichTextUrl) text).text; entity = new TdApi.TextEntityTypeTextUrl(((TdApi.RichTextUrl) text).url); break;
        case TdApi.RichTextEmailAddress.CONSTRUCTOR: child = ((TdApi.RichTextEmailAddress) text).text; entity = new TdApi.TextEntityTypeTextUrl("mailto:" + ((TdApi.RichTextEmailAddress) text).emailAddress); break;
        case TdApi.RichTextPhoneNumber.CONSTRUCTOR: child = ((TdApi.RichTextPhoneNumber) text).text; entity = new TdApi.TextEntityTypeTextUrl("tel:" + ((TdApi.RichTextPhoneNumber) text).phoneNumber); break;
        case TdApi.RichTextBankCardNumber.CONSTRUCTOR: child = ((TdApi.RichTextBankCardNumber) text).text; entity = new TdApi.TextEntityTypeBankCardNumber(); break;
        case TdApi.RichTextBotCommand.CONSTRUCTOR: child = ((TdApi.RichTextBotCommand) text).text; entity = new TdApi.TextEntityTypeBotCommand(); break;
        case TdApi.RichTextHashtag.CONSTRUCTOR: child = ((TdApi.RichTextHashtag) text).text; entity = new TdApi.TextEntityTypeHashtag(); break;
        case TdApi.RichTextCashtag.CONSTRUCTOR: child = ((TdApi.RichTextCashtag) text).text; entity = new TdApi.TextEntityTypeCashtag(); break;
        case TdApi.RichTextMention.CONSTRUCTOR:
          child = ((TdApi.RichTextMention) text).text;
          entity = new TdApi.TextEntityTypeTextUrl("https://t.me/" + ((TdApi.RichTextMention) text).username);
          break;
        case TdApi.RichTextMentionName.CONSTRUCTOR: child = ((TdApi.RichTextMentionName) text).text; entity = new TdApi.TextEntityTypeMentionName(((TdApi.RichTextMentionName) text).userId); break;
        case TdApi.RichTextDateTime.CONSTRUCTOR: child = ((TdApi.RichTextDateTime) text).text; entity = new TdApi.TextEntityTypeDateTime(((TdApi.RichTextDateTime) text).unixTime, null); break;
        case TdApi.RichTextReference.CONSTRUCTOR: child = ((TdApi.RichTextReference) text).text; break;
        case TdApi.RichTextReferenceLink.CONSTRUCTOR: child = ((TdApi.RichTextReferenceLink) text).text; break;
        case TdApi.RichTextAnchorLink.CONSTRUCTOR: child = ((TdApi.RichTextAnchorLink) text).text; break;
        case TdApi.RichTextButton.CONSTRUCTOR: TdApi.InlineButton button = ((TdApi.RichTextButton) text).button; child = button != null ? button.text : null; break;
        case TdApi.RichTextCustomEmoji.CONSTRUCTOR: append(((TdApi.RichTextCustomEmoji) text).alternativeText); entity = new TdApi.TextEntityTypeCustomEmoji(((TdApi.RichTextCustomEmoji) text).customEmojiId); break;
        case TdApi.RichTextMathematicalExpression.CONSTRUCTOR: append(((TdApi.RichTextMathematicalExpression) text).expression); entity = new TdApi.TextEntityTypeCode(); break;
        case TdApi.RichTextDiff.CONSTRUCTOR:
          TdApi.RichTextDiff diff = (TdApi.RichTextDiff) text;
          write(new TdApi.RichTextStrikethrough(diff.oldText), depth + 1);
          int oldEnd = value.length();
          if (value.length() > start) append(" ");
          int newStart = value.length();
          write(new TdApi.RichTextBold(diff.text), depth + 1);
          if (value.length() == newStart) value.setLength(oldEnd);
          break;
        default: break;
      }
      if (child != null) write(child, depth + 1);
      if (entity != null && value.length() > start) entities.add(new TdApi.TextEntity(start, value.length() - start, entity));
    }
  }
}
