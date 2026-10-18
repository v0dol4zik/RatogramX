package moe.kirao.mgx.utils;

import org.drinkless.tdlib.TdApi;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Editable blocks shared by the local preview and the TDLib input. */
public final class RichMessageDocument {
  public static final int PARAGRAPH = 0, HEADING = 1, LIST = 2, QUOTE = 3, CODE = 4, PHOTO = 5;
  public static final int MAX_BLOCKS = 100, MAX_TEXT = 32768, MAX_MEDIA = 10;
  public final List<Block> blocks = new ArrayList<>();
  public boolean isRtl;

  public static final class Block {
    public final int type;
    public String text = "", extra = "", photoPath = "";
    public int size = 2, width, height;
    public boolean ordered;
    private TdApi.PageBlock original;
    private String originalText;
    private boolean originalOrdered;

    public Block (int type) {
      if (type < PARAGRAPH || type > PHOTO) throw new IllegalArgumentException("Unknown block");
      this.type = type;
    }

    public Block copy () {
      Block b = new Block(type);
      b.text = text; b.extra = extra; b.photoPath = photoPath;
      b.size = size; b.width = width; b.height = height; b.ordered = ordered;
      b.original = original; b.originalText = originalText; b.originalOrdered = originalOrdered;
      return b;
    }

    private boolean unchanged () { return original != null && text.equals(originalText); }

    private TdApi.RichText richText () {
      if (unchanged()) {
        TdApi.RichText rich = RichMessageUtils.blockText(original);
        if (rich != null) return rich;
      }
      return new TdApi.RichTextPlain(text);
    }

    private TdApi.Photo photo () {
      if (original instanceof TdApi.PageBlockPhoto && photoPath.isEmpty()) return ((TdApi.PageBlockPhoto) original).photo;
      java.io.File path = new java.io.File(photoPath);
      TdApi.File file = new TdApi.File(-Math.max(1, photoPath.hashCode() & 0x7fffffff), path.length(), path.length(),
        new TdApi.LocalFile(photoPath, false, false, false, true, 0, path.length(), path.length()),
        new TdApi.RemoteFile("", "", false, false, 0));
      return new TdApi.Photo(false, null, new TdApi.PhotoSize[] {new TdApi.PhotoSize("x", file, width, height, new int[0])});
    }

    public TdApi.PageBlock output () {
      if (unchanged() && (type == QUOTE || type == LIST && ordered == originalOrdered)) return original;
      if (unchanged() && type == LIST) {
        TdApi.PageBlockList old = (TdApi.PageBlockList) original;
        TdApi.PageBlockListItem[] items = new TdApi.PageBlockListItem[old.items.length];
        for (int i = 0; i < items.length; i++) {
          TdApi.PageBlockListItem item = old.items[i];
          items[i] = new TdApi.PageBlockListItem(ordered ? (i + 1) + "." : "", item.blocks, item.hasCheckbox, item.isChecked, ordered ? i + 1 : 0, ordered ? "1" : "");
        }
        return new TdApi.PageBlockList(items);
      }
      TdApi.RichText t = richText();
      return switch (type) {
        case PARAGRAPH -> new TdApi.PageBlockParagraph(t);
        case HEADING -> new TdApi.PageBlockSectionHeading(t, size);
        case CODE -> new TdApi.PageBlockPreformatted(t, extra);
        case QUOTE -> new TdApi.PageBlockBlockQuote(new TdApi.PageBlock[] {new TdApi.PageBlockParagraph(t)},
          original instanceof TdApi.PageBlockBlockQuote ? ((TdApi.PageBlockBlockQuote) original).credit : null);
        case LIST -> {
          String[] lines = text.split("\n", -1);
          TdApi.PageBlockListItem[] items = new TdApi.PageBlockListItem[lines.length];
          for (int i = 0; i < lines.length; i++) {
            items[i] = new TdApi.PageBlockListItem(ordered ? (i + 1) + "." : "", new TdApi.PageBlock[] {
              new TdApi.PageBlockParagraph(new TdApi.RichTextPlain(lines[i]))}, false, false, ordered ? i + 1 : 0, ordered ? "1" : "");
          }
          yield new TdApi.PageBlockList(items);
        }
        case PHOTO -> {
          TdApi.PageBlockPhoto old = original instanceof TdApi.PageBlockPhoto ? (TdApi.PageBlockPhoto) original : null;
          TdApi.PageBlockCaption caption = unchanged() && old != null && old.caption != null ? old.caption : new TdApi.PageBlockCaption(new TdApi.RichTextPlain(text), old != null && old.caption != null ? old.caption.credit : null);
          yield new TdApi.PageBlockPhoto(photo(), caption, old != null ? old.url : "", old != null && old.hasSpoiler);
        }
        default -> throw new IllegalStateException();
      };
    }
  }

  public RichMessageDocument copy () {
    RichMessageDocument d = new RichMessageDocument();
    d.isRtl = isRtl;
    for (Block b : blocks) d.blocks.add(b.copy());
    return d;
  }

  public void validate () {
    if (blocks.isEmpty() || blocks.size() > MAX_BLOCKS) throw new IllegalArgumentException("block_limit");
    int textLength = 0, mediaCount = 0, blockCount = 0;
    boolean hasContent = false;
    for (Block b : blocks) {
      textLength += b.text.length() + b.extra.length();
      blockCount++;
      if (b.type == LIST) blockCount += b.text.split("\n", -1).length * 2;
      if (b.type == QUOTE) blockCount++;
      if (b.size < 1 || b.size > 6) throw new IllegalArgumentException("heading_size");
      if (b.type == PHOTO) {
        mediaCount++;
        if (b.photoPath.isEmpty() && !(b.original instanceof TdApi.PageBlockPhoto)) throw new IllegalArgumentException("photo_missing");
      }
      hasContent |= b.type == PHOTO || !b.text.trim().isEmpty();
    }
    if (!hasContent) throw new IllegalArgumentException("empty");
    if (textLength > MAX_TEXT || blockCount > MAX_BLOCKS || mediaCount > MAX_MEDIA) throw new IllegalArgumentException("limit");
  }

  public TdApi.RichMessage output () {
    TdApi.PageBlock[] result = new TdApi.PageBlock[blocks.size()];
    for (int i = 0; i < result.length; i++) result[i] = blocks.get(i).output();
    return new TdApi.RichMessage(result, isRtl, true);
  }

  public TdApi.InputMessageRichMessage input () {
    validate();
    return new TdApi.InputMessageRichMessage(new TdApi.InputRichMessage(
      new TdApi.RichMessageSourceBlocks(toInput(output().blocks)), isRtl, true), false);
  }

  private static TdApi.InputPageBlock[] toInput (TdApi.PageBlock[] blocks) {
    return toInput(blocks, 0, new int[2]);
  }

  private static TdApi.InputPageBlock[] toInput (TdApi.PageBlock[] blocks, int depth, int[] counts) {
    if (blocks == null || depth > 32 || (counts[0] += blocks.length) > MAX_BLOCKS) throw new IllegalArgumentException("block_limit");
    TdApi.InputPageBlock[] result = new TdApi.InputPageBlock[blocks.length];
    for (int i = 0; i < blocks.length; i++) {
      TdApi.PageBlock block = blocks[i];
      if (block instanceof TdApi.PageBlockParagraph) result[i] = new TdApi.InputPageBlockParagraph(((TdApi.PageBlockParagraph) block).text);
      else if (block instanceof TdApi.PageBlockSectionHeading) {
        TdApi.PageBlockSectionHeading b = (TdApi.PageBlockSectionHeading) block;
        result[i] = new TdApi.InputPageBlockSectionHeading(b.text, b.size);
      } else if (block instanceof TdApi.PageBlockPreformatted) {
        TdApi.PageBlockPreformatted b = (TdApi.PageBlockPreformatted) block;
        result[i] = new TdApi.InputPageBlockPreformatted(b.text, b.language);
      } else if (block instanceof TdApi.PageBlockBlockQuote) {
        TdApi.PageBlockBlockQuote b = (TdApi.PageBlockBlockQuote) block;
        result[i] = new TdApi.InputPageBlockBlockQuote(toInput(b.blocks, depth + 1, counts), b.credit);
      } else if (block instanceof TdApi.PageBlockList) {
        TdApi.PageBlockList b = (TdApi.PageBlockList) block;
        if (b.items == null || (counts[0] += b.items.length) > MAX_BLOCKS) throw new IllegalArgumentException("block_limit");
        TdApi.InputPageBlockListItem[] items = new TdApi.InputPageBlockListItem[b.items.length];
        for (int j = 0; j < items.length; j++) {
          TdApi.PageBlockListItem item = b.items[j];
          if (item == null) throw new IllegalArgumentException("unsupported_block");
          items[j] = new TdApi.InputPageBlockListItem(toInput(item.blocks, depth + 1, counts), item.hasCheckbox, item.isChecked, item.value, item.type);
        }
        result[i] = new TdApi.InputPageBlockList(items);
      } else if (block instanceof TdApi.PageBlockPhoto) {
        if (++counts[1] > MAX_MEDIA) throw new IllegalArgumentException("media_limit");
        TdApi.PageBlockPhoto b = (TdApi.PageBlockPhoto) block;
        if (b.photo == null || b.photo.sizes == null || b.photo.sizes.length == 0) throw new IllegalArgumentException("photo_missing");
        TdApi.PhotoSize size = b.photo.sizes[0];
        for (TdApi.PhotoSize candidate : b.photo.sizes) if ((long) candidate.width * candidate.height > (long) size.width * size.height) size = candidate;
        TdApi.InputFile file = size.photo.id > 0 ? new TdApi.InputFileId(size.photo.id) : new TdApi.InputFileLocal(size.photo.local.path);
        result[i] = new TdApi.InputPageBlockPhoto(new TdApi.InputPhoto(file, null, null, new int[0], size.width, size.height), b.caption, b.hasSpoiler);
      } else throw new IllegalArgumentException("unsupported_block");
    }
    return result;
  }

  /** Reject unsupported layouts rather than dropping their blocks on save. */
  public static RichMessageDocument fromRichMessage (TdApi.RichMessage message) {
    if (!message.isFull || message.blocks == null || message.blocks.length > MAX_BLOCKS) throw new IllegalArgumentException("incomplete");
    RichMessageDocument document = new RichMessageDocument();
    document.isRtl = message.isRtl;
    toInput(message.blocks);
    for (TdApi.PageBlock source : message.blocks) {
      Block b;
      if (source instanceof TdApi.PageBlockParagraph) b = new Block(PARAGRAPH);
      else if (source instanceof TdApi.PageBlockSectionHeading) {
        b = new Block(HEADING); b.size = ((TdApi.PageBlockSectionHeading) source).size;
      } else if (source instanceof TdApi.PageBlockPreformatted) {
        b = new Block(CODE); b.extra = ((TdApi.PageBlockPreformatted) source).language;
      } else if (source instanceof TdApi.PageBlockList) {
        b = new Block(LIST);
        StringBuilder text = new StringBuilder();
        for (TdApi.PageBlockListItem item : ((TdApi.PageBlockList) source).items) {
          if (!text.isEmpty()) text.append('\n');
          text.append(RichMessageUtils.text(new TdApi.RichMessage(item.blocks, false, true)).text);
        }
        b.text = text.toString();
        b.ordered = ((TdApi.PageBlockList) source).items.length > 0 && ((TdApi.PageBlockList) source).items[0].value != 0;
      } else if (source instanceof TdApi.PageBlockBlockQuote) {
        b = new Block(QUOTE);
        b.text = RichMessageUtils.text(new TdApi.RichMessage(((TdApi.PageBlockBlockQuote) source).blocks, false, true)).text;
      } else if (source instanceof TdApi.PageBlockPhoto) b = new Block(PHOTO);
      else throw new IllegalArgumentException("unsupported_block");
      if (b.type != LIST && b.type != QUOTE) b.text = RichMessageUtils.plainText(RichMessageUtils.blockText(source));
      b.original = source; b.originalText = b.text; b.originalOrdered = b.ordered;
      document.blocks.add(b);
    }
    return document;
  }

  public void write (DataOutputStream out) throws IOException {
    validate();
    out.writeBoolean(isRtl); out.writeInt(blocks.size());
    for (Block b : blocks) {
      if (b.original != null) throw new IOException("Server blocks cannot be stored as local drafts");
      out.writeInt(b.type); writeString(out, b.text); writeString(out, b.extra); writeString(out, b.photoPath);
      out.writeInt(b.size); out.writeInt(b.width); out.writeInt(b.height); out.writeBoolean(b.ordered);
    }
  }

  public static RichMessageDocument read (DataInputStream in) throws IOException {
    RichMessageDocument d = new RichMessageDocument();
    d.isRtl = in.readBoolean();
    int count = in.readInt();
    if (count < 1 || count > MAX_BLOCKS) throw new IOException("Invalid block count");
    try {
      for (int i = 0; i < count; i++) {
        Block b = new Block(in.readInt());
        b.text = readString(in); b.extra = readString(in); b.photoPath = readString(in);
        b.size = in.readInt(); b.width = in.readInt(); b.height = in.readInt(); b.ordered = in.readBoolean();
        d.blocks.add(b);
      }
      d.validate();
    } catch (IllegalArgumentException e) { throw new IOException("Invalid rich message", e); }
    return d;
  }

  private static void writeString (DataOutputStream out, String value) throws IOException {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    out.writeInt(bytes.length); out.write(bytes);
  }

  private static String readString (DataInputStream in) throws IOException {
    int length = in.readInt();
    if (length < 0 || length > MAX_TEXT * 4) throw new IOException("Invalid string size");
    byte[] bytes = new byte[length]; in.readFully(bytes);
    return new String(bytes, StandardCharsets.UTF_8);
  }
}
