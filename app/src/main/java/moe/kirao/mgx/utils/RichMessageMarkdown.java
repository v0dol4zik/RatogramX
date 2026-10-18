package moe.kirao.mgx.utils;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Telegram's human-friendly inline Markdown inside editable rich-message blocks. */
public final class RichMessageMarkdown {
  public static final int MAX_SOURCE = 65536;
  private static final Pattern HEADING = Pattern.compile("^(#{1,6})[ \\t]+(.*)$");
  private static final Pattern LIST = Pattern.compile("^( {0,32})([-+*]|[1-9][0-9]{0,7}[.)])[ \\t]+(.*)$");
  private static final Pattern PHOTO = Pattern.compile("^!\\[(.*)]\\(attachment:([a-zA-Z0-9_-]+)\\)$");
  private static final Pattern FENCE = Pattern.compile("^(`{3,}|~{3,})([^`~]*)$");
  private static final String ESCAPABLE = "\\*_~|`[]()!#>+-.";
  private RichMessageMarkdown () { }

  public interface InlineParser { TdApi.RichText parse (String source); }

  public static final class Source {
    public final Map<String, RichMessageDocument.Block> photos = new LinkedHashMap<>();
    public final String text;
    private final List<String> originalSources = new ArrayList<>();
    private final List<RichMessageDocument.Block> originals = new ArrayList<>();

    public Source (String text) {
      if (text.length() > MAX_SOURCE) throw new IllegalArgumentException("source_limit");
      this.text = text;
    }

    public String addPhoto (RichMessageDocument.Block photo) {
      int number = 1;
      while (photos.containsKey("photo-" + number)) number++;
      String name = "photo-" + number;
      RichMessageDocument.Block copy = photo.copy();
      copy.extra = name;
      photos.put(name, copy);
      return "![](attachment:" + name + ")";
    }
  }

  public static Source source (RichMessageDocument document) {
    Source result = new Source(document.markdownSource != null ? document.markdownSource : "");
    if (document.markdownSource != null) {
      for (RichMessageDocument.Block block : document.blocks) if (block.type == RichMessageDocument.PHOTO && !block.extra.isEmpty()) result.photos.put(block.extra, block.copy());
      return result;
    }
    StringBuilder text = new StringBuilder();
    for (RichMessageDocument.Block block : document.blocks) {
      String value;
      if (block.type == RichMessageDocument.PHOTO) {
        String reference = result.addPhoto(block);
        TdApi.PageBlockPhoto photo = (TdApi.PageBlockPhoto) block.output();
        String caption = rich(photo.caption != null ? photo.caption.text : null, 0);
        if (caption.indexOf('\n') != -1 || caption.indexOf('\r') != -1) throw new IllegalArgumentException("multiline_caption");
        value = reference.replace("![]", "![" + caption + "]");
      } else value = serialize(block.output(), 0);
      if (text.length() != 0) text.append("\n\n");
      text.append(value);
      result.originalSources.add(value.strip());
      result.originals.add(block.copy());
    }
    Source source = new Source(text.toString());
    source.photos.putAll(result.photos);
    source.originalSources.addAll(result.originalSources);
    source.originals.addAll(result.originals);
    return source;
  }

  public static RichMessageDocument parse (String text, Source source, boolean isRtl) {
    return parse(text, source, isRtl, RichMessageMarkdown::inline);
  }

  public static RichMessageDocument parse (String text, Source source, boolean isRtl, InlineParser inline) {
    if (text.length() > MAX_SOURCE) throw new IllegalArgumentException("source_limit");
    String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
    Parser parser = new Parser(normalized.split("\n", -1), source, inline, new int[2]);
    List<TdApi.PageBlock> output = parser.blocks(0);
    RichMessageDocument document = RichMessageDocument.fromRichMessage(new TdApi.RichMessage(output.toArray(new TdApi.PageBlock[0]), isRtl, true));
    boolean[] used = new boolean[source.originals.size()];
    for (int i = 0; i < document.blocks.size(); i++) {
      for (int j = 0; j < used.length; j++) if (!used[j] && parser.ranges.get(i).equals(source.originalSources.get(j))) {
        RichMessageDocument.Block original = source.originals.get(j);
        if (original.type == document.blocks.get(i).type) {
          document.blocks.set(i, original.copy()); used[j] = true; break;
        }
      }
      RichMessageDocument.Block block = document.blocks.get(i);
      if (block.type == RichMessageDocument.PHOTO) {
        RichMessageDocument.Block photo = source.photos.get(parser.photoNames.get(i));
        block.photoPath = photo.photoPath; block.width = photo.width; block.height = photo.height;
        block.extra = parser.photoNames.get(i);
      }
    }
    document.markdownSource = text;
    document.validate();
    return document;
  }

  private static final class Parser {
    final String[] lines;
    final Source source;
    final InlineParser inline;
    final int[] count;
    final List<String> ranges = new ArrayList<>(), photoNames = new ArrayList<>();
    int position;

    Parser (String[] lines, Source source, InlineParser inline, int[] count) {
      this.lines = lines; this.source = source; this.inline = inline; this.count = count;
    }

    private void count () { if (++count[0] > RichMessageDocument.MAX_BLOCKS) throw new IllegalArgumentException("block_limit"); }

    private TdApi.RichText text (String value) {
      TdApi.RichText result = inline.parse(value);
      countText(textLength(result, 0));
      return result;
    }

    private void countText (int length) {
      if ((count[1] += length) > RichMessageDocument.MAX_TEXT) throw new IllegalArgumentException("text_limit");
    }

    List<TdApi.PageBlock> blocks (int depth) {
      if (depth > 16) throw new IllegalArgumentException("block_depth");
      List<TdApi.PageBlock> blocks = new ArrayList<>();
      while (position < lines.length) {
        if (lines[position].isBlank()) { position++; continue; }
        count();
        int start = position;
        String line = lines[position];
        Matcher heading = HEADING.matcher(line), fence = FENCE.matcher(line), photo = PHOTO.matcher(line), list = LIST.matcher(line);
        String photoName = "";
        TdApi.PageBlock block;
        if (fence.matches()) {
          String delimiter = fence.group(1), language = fence.group(2).trim();
          if (!language.matches("[a-zA-Z0-9_+.#-]{0,64}")) throw new IllegalArgumentException("code_language");
          StringBuilder code = new StringBuilder(); position++;
          boolean first = true;
          while (position < lines.length && !lines[position].matches(Pattern.quote(delimiter.substring(0, 1)) + "{" + delimiter.length() + ",}\\s*")) {
            if (!first) code.append('\n');
            first = false;
            code.append(lines[position++]);
          }
          if (position < lines.length) position++;
          countText(code.length());
          block = new TdApi.PageBlockPreformatted(new TdApi.RichTextPlain(code.toString()), language);
        } else if (heading.matches()) {
          block = new TdApi.PageBlockSectionHeading(text(heading.group(2)), heading.group(1).length()); position++;
        } else if (line.startsWith(">")) {
          List<String> quote = new ArrayList<>();
          while (position < lines.length && lines[position].startsWith(">")) {
            String value = lines[position++].substring(1);
            quote.add(value.startsWith(" ") ? value.substring(1) : value);
          }
          block = new TdApi.PageBlockBlockQuote(new Parser(quote.toArray(new String[0]), source, inline, count).blocks(depth + 1).toArray(new TdApi.PageBlock[0]), null);
        } else if (list.matches()) {
          int indent = list.group(1).length();
          boolean ordered = Character.isDigit(list.group(2).charAt(0));
          List<TdApi.PageBlockListItem> items = new ArrayList<>();
          while (position < lines.length) {
            Matcher item = LIST.matcher(lines[position]);
            if (!item.matches() || item.group(1).length() != indent || Character.isDigit(item.group(2).charAt(0)) != ordered) break;
            count();
            String body = item.group(3);
            boolean checkbox = body.matches("^\\[[ xX]] .*"), checked = checkbox && body.charAt(1) != ' ';
            if (checkbox) body = body.substring(4);
            int value = ordered ? Integer.parseInt(item.group(2).substring(0, item.group(2).length() - 1)) : 0;
            List<String> content = new ArrayList<>(); content.add(body); position++;
            while (position < lines.length) {
              String next = lines[position];
              if (next.startsWith(" ".repeat(indent + 2))) { content.add(next.substring(indent + 2)); position++; }
              else if (next.isBlank() && position + 1 < lines.length && lines[position + 1].startsWith(" ".repeat(indent + 2))) { content.add(""); position++; }
              else break;
            }
            TdApi.PageBlock[] children = new Parser(content.toArray(new String[0]), source, inline, count).blocks(depth + 1).toArray(new TdApi.PageBlock[0]);
            if (children.length == 0) children = new TdApi.PageBlock[] {new TdApi.PageBlockParagraph(new TdApi.RichTextPlain(""))};
            String label = checkbox ? (ordered ? value + ". " : "") + (checked ? "☑" : "☐") : ordered ? value + "." : "•";
            items.add(new TdApi.PageBlockListItem(label, children, checkbox, checked, value, ordered ? "1" : ""));
          }
          block = new TdApi.PageBlockList(items.toArray(new TdApi.PageBlockListItem[0]));
        } else if (photo.matches()) {
          if (depth != 0) throw new IllegalArgumentException("nested_photo");
          photoName = photo.group(2);
          RichMessageDocument.Block asset = source.photos.get(photoName);
          if (asset == null) throw new IllegalArgumentException("photo_reference");
          TdApi.PageBlockPhoto original = (TdApi.PageBlockPhoto) asset.output();
          block = new TdApi.PageBlockPhoto(original.photo, new TdApi.PageBlockCaption(text(photo.group(1)), original.caption != null ? original.caption.credit : null), original.url, original.hasSpoiler);
          position++;
        } else {
          StringBuilder paragraph = new StringBuilder(lines[position++]);
          while (position < lines.length && !lines[position].isBlank() && !startsBlock(lines[position])) paragraph.append('\n').append(lines[position++]);
          block = new TdApi.PageBlockParagraph(text(paragraph.toString()));
        }
        blocks.add(block);
        ranges.add(String.join("\n", java.util.Arrays.copyOfRange(lines, start, position)).strip());
        photoNames.add(photoName);
      }
      return blocks;
    }
  }

  private static int textLength (TdApi.RichText text, int depth) {
    if (depth > 32) throw new IllegalArgumentException("text_depth");
    int length;
    if (text instanceof TdApi.RichTextPlain) length = ((TdApi.RichTextPlain) text).text.length();
    else if (text instanceof TdApi.RichTexts) {
      length = 0;
      for (TdApi.RichText part : ((TdApi.RichTexts) text).texts) {
        length += textLength(part, depth + 1);
        if (length > RichMessageDocument.MAX_TEXT) throw new IllegalArgumentException("text_limit");
      }
    } else {
      TdApi.RichText child = styleChild(text);
      if (child == null && text instanceof TdApi.RichTextUrl) child = ((TdApi.RichTextUrl) text).text;
      if (child == null && text instanceof TdApi.RichTextUnderline) child = ((TdApi.RichTextUnderline) text).text;
      if (child == null && text instanceof TdApi.RichTextMentionName) child = ((TdApi.RichTextMentionName) text).text;
      if (child == null) throw new IllegalArgumentException("unsupported_text");
      length = textLength(child, depth + 1);
    }
    if (length > RichMessageDocument.MAX_TEXT) throw new IllegalArgumentException("text_limit");
    return length;
  }

  private static boolean startsBlock (String line) {
    return HEADING.matcher(line).matches() || FENCE.matcher(line).matches() || PHOTO.matcher(line).matches() || LIST.matcher(line).matches() || line.startsWith(">");
  }

  private static String serialize (TdApi.PageBlock block, int depth) {
    if (depth > 16) throw new IllegalArgumentException("block_depth");
    if (block instanceof TdApi.PageBlockParagraph) {
      String text = rich(((TdApi.PageBlockParagraph) block).text, 0);
      return startsBlock(text) ? "\\" + text : text;
    }
    if (block instanceof TdApi.PageBlockSectionHeading) {
      TdApi.PageBlockSectionHeading heading = (TdApi.PageBlockSectionHeading) block;
      return "#".repeat(heading.size) + " " + rich(heading.text, 0);
    }
    if (block instanceof TdApi.PageBlockPreformatted) {
      TdApi.PageBlockPreformatted code = (TdApi.PageBlockPreformatted) block;
      String text = RichMessageUtils.plainText(code.text), fence = "```";
      while (text.contains(fence)) fence += '`';
      return fence + code.language + "\n" + text + "\n" + fence;
    }
    if (block instanceof TdApi.PageBlockPhoto) throw new IllegalArgumentException("nested_photo");
    if (block instanceof TdApi.PageBlockBlockQuote) {
      TdApi.PageBlockBlockQuote quote = (TdApi.PageBlockBlockQuote) block;
      if (!RichMessageUtils.plainText(quote.credit).isEmpty()) throw new IllegalArgumentException("quote_credit");
      List<String> children = new ArrayList<>();
      for (TdApi.PageBlock child : quote.blocks) children.add(serialize(child, depth + 1));
      return "> " + String.join("\n\n", children).replace("\n", "\n> ");
    }
    if (block instanceof TdApi.PageBlockList) {
      List<String> items = new ArrayList<>();
      for (TdApi.PageBlockListItem item : ((TdApi.PageBlockList) block).items) {
        List<String> children = new ArrayList<>();
        for (TdApi.PageBlock child : item.blocks) children.add(serialize(child, depth + 1));
        String prefix = item.value != 0 ? item.value + ". " : "- ";
        if (item.hasCheckbox) prefix += item.isChecked ? "[x] " : "[ ] ";
        items.add(prefix + String.join("\n\n", children).replace("\n", "\n  "));
      }
      return String.join("\n", items);
    }
    throw new IllegalArgumentException("unsupported_block");
  }

  private static String escape (String text) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (ESCAPABLE.indexOf(c) != -1) result.append('\\');
      result.append(c);
    }
    return result.toString();
  }

  private static String rich (TdApi.RichText text, int depth) {
    if (text == null) return "";
    if (depth > 32) throw new IllegalArgumentException("text_depth");
    if (text instanceof TdApi.RichTextPlain) return escape(((TdApi.RichTextPlain) text).text);
    if (text instanceof TdApi.RichTexts) {
      StringBuilder result = new StringBuilder();
      TdApi.RichText[] parts = ((TdApi.RichTexts) text).texts;
      for (int i = 0; i < parts.length; i++) {
        TdApi.RichText child = parts[i], inner = styleChild(child);
        if (inner != null) {
          List<TdApi.RichText> run = new ArrayList<>(); run.add(inner);
          while (i + 1 < parts.length && parts[i + 1].getConstructor() == child.getConstructor()) run.add(styleChild(parts[++i]));
          if (run.size() > 1) child = withStyle(child, new TdApi.RichTexts(run.toArray(new TdApi.RichText[0])));
        }
        result.append(rich(child, depth + 1));
      }
      return result.toString();
    }
    if (text instanceof TdApi.RichTextBold) return "**" + rich(((TdApi.RichTextBold) text).text, depth + 1) + "**";
    if (text instanceof TdApi.RichTextItalic) return "__" + rich(((TdApi.RichTextItalic) text).text, depth + 1) + "__";
    if (text instanceof TdApi.RichTextStrikethrough) return "~~" + rich(((TdApi.RichTextStrikethrough) text).text, depth + 1) + "~~";
    if (text instanceof TdApi.RichTextSpoiler) return "||" + rich(((TdApi.RichTextSpoiler) text).text, depth + 1) + "||";
    if (text instanceof TdApi.RichTextFixed) {
      String code = RichMessageUtils.plainText(((TdApi.RichTextFixed) text).text);
      if (code.contains("`")) throw new IllegalArgumentException("inline_code");
      return '`' + code + '`';
    }
    if (text instanceof TdApi.RichTextUrl) {
      TdApi.RichTextUrl link = (TdApi.RichTextUrl) text;
      return "[" + rich(link.text, depth + 1) + "](" + link.url.replace("(", "%28").replace(")", "%29").replace(" ", "%20") + ")";
    }
    throw new IllegalArgumentException("unsupported_text");
  }

  private static TdApi.RichText styleChild (TdApi.RichText text) {
    if (text instanceof TdApi.RichTextBold) return ((TdApi.RichTextBold) text).text;
    if (text instanceof TdApi.RichTextItalic) return ((TdApi.RichTextItalic) text).text;
    if (text instanceof TdApi.RichTextStrikethrough) return ((TdApi.RichTextStrikethrough) text).text;
    if (text instanceof TdApi.RichTextSpoiler) return ((TdApi.RichTextSpoiler) text).text;
    if (text instanceof TdApi.RichTextFixed) return ((TdApi.RichTextFixed) text).text;
    return null;
  }

  private static TdApi.RichText withStyle (TdApi.RichText style, TdApi.RichText text) {
    if (style instanceof TdApi.RichTextBold) return new TdApi.RichTextBold(text);
    if (style instanceof TdApi.RichTextItalic) return new TdApi.RichTextItalic(text);
    if (style instanceof TdApi.RichTextStrikethrough) return new TdApi.RichTextStrikethrough(text);
    if (style instanceof TdApi.RichTextSpoiler) return new TdApi.RichTextSpoiler(text);
    return new TdApi.RichTextFixed(text);
  }

  public static TdApi.RichText inline (String source) {
    // Protect escaped punctuation without changing UTF-16 entity offsets.
    int base = 0xe000;
    while (base < 0xf800) {
      boolean available = true;
      for (int i = 0; i < source.length(); i++) if (source.charAt(i) >= base && source.charAt(i) < base + 128) { available = false; break; }
      if (available) break;
      base += 128;
    }
    if (base == 0xf800) throw new IllegalArgumentException("escape_limit");
    StringBuilder protectedText = new StringBuilder();
    for (int i = 0; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c == '\\' && i + 1 < source.length() && ESCAPABLE.indexOf(source.charAt(i + 1)) != -1) protectedText.append((char) (base + source.charAt(++i)));
      else if (c == '`') {
        int end = i + 1;
        while (end < source.length() && source.charAt(end) == '`') end++;
        String delimiter = source.substring(i, end);
        int close = source.indexOf(delimiter, end);
        if (close != -1) {
          protectedText.append(source, i, close + delimiter.length());
          i = close + delimiter.length() - 1;
        } else protectedText.append(c);
      }
      else protectedText.append(c);
    }
    TdApi.FormattedText parsed;
    try { parsed = Client.execute(new TdApi.ParseMarkdown(new TdApi.FormattedText(protectedText.toString(), new TdApi.TextEntity[0]))); }
    catch (Client.ExecutionException e) { throw new IllegalArgumentException("markdown", e); }
    char[] value = parsed.text.toCharArray();
    for (int i = 0; i < value.length; i++) if (value[i] >= base && value[i] < base + 128) value[i] -= base;
    parsed.text = new String(value);
    return fromFormattedText(parsed);
  }

  public static TdApi.RichText fromFormattedText (TdApi.FormattedText text) {
    TdApi.TextEntity[] entities = text.entities != null ? text.entities : new TdApi.TextEntity[0];
    if (entities.length > 1024) throw new IllegalArgumentException("entity_limit");
    TreeSet<Integer> boundaries = new TreeSet<>(); boundaries.add(0); boundaries.add(text.text.length());
    for (TdApi.TextEntity entity : entities) {
      if (entity.offset < 0 || entity.length < 0 || entity.offset > text.text.length() - entity.length) throw new IllegalArgumentException("entity_range");
      boundaries.add(entity.offset); boundaries.add(entity.offset + entity.length);
    }
    List<TdApi.RichText> parts = new ArrayList<>(); int start = 0;
    for (int end : boundaries) {
      if (end == start) continue;
      TdApi.RichText part = new TdApi.RichTextPlain(text.text.substring(start, end));
      for (int i = entities.length - 1; i >= 0; i--) if (entities[i].offset <= start && entities[i].offset + entities[i].length >= end) {
        TdApi.TextEntityType type = entities[i].type;
        if (type instanceof TdApi.TextEntityTypeBold) part = new TdApi.RichTextBold(part);
        else if (type instanceof TdApi.TextEntityTypeItalic) part = new TdApi.RichTextItalic(part);
        else if (type instanceof TdApi.TextEntityTypeUnderline) part = new TdApi.RichTextUnderline(part);
        else if (type instanceof TdApi.TextEntityTypeStrikethrough) part = new TdApi.RichTextStrikethrough(part);
        else if (type instanceof TdApi.TextEntityTypeSpoiler) part = new TdApi.RichTextSpoiler(part);
        else if (type instanceof TdApi.TextEntityTypeCode || type instanceof TdApi.TextEntityTypePre || type instanceof TdApi.TextEntityTypePreCode) part = new TdApi.RichTextFixed(part);
        else if (type instanceof TdApi.TextEntityTypeTextUrl) part = new TdApi.RichTextUrl(part, ((TdApi.TextEntityTypeTextUrl) type).url, false);
        else if (type instanceof TdApi.TextEntityTypeMentionName) part = new TdApi.RichTextMentionName(part, ((TdApi.TextEntityTypeMentionName) type).userId);
      }
      parts.add(part); start = end;
    }
    return parts.size() == 1 ? parts.get(0) : new TdApi.RichTexts(parts.toArray(new TdApi.RichText[0]));
  }
}
