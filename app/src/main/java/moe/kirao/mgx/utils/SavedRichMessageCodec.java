package moe.kirao.mgx.utils;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A normal Telegram code block, interpreted as Markdown only in Saved Messages. */
public final class SavedRichMessageCodec {
  public static final String LANGUAGE = "ratogramx-rich-v1", LANGUAGE_RTL = LANGUAGE + "-rtl";
  public static final String FILE_PREFIX = "RatogramX-rich-";
  public static final int MAX_FILE_BYTES = RichMessageMarkdown.MAX_SOURCE * 4;
  private static final Pattern PHOTO = Pattern.compile("^!\\[[^\\r\\n]*]\\(attachment:([a-zA-Z0-9_-]+)\\)$");
  private static final Pattern FENCE = Pattern.compile("^(`{3,}|~{3,})([^`~]*)$");

  private SavedRichMessageCodec () { }

  public static boolean recognizes (TdApi.Message message, long userId) {
    return message != null && userId > 0 && message.chatId == userId &&
      message.senderId instanceof TdApi.MessageSenderUser && ((TdApi.MessageSenderUser) message.senderId).userId == userId &&
      recognizes(message.content);
  }

  public static boolean recognizes (TdApi.MessageContent content) {
    if (content instanceof TdApi.MessageText) {
      TdApi.FormattedText text = ((TdApi.MessageText) content).text;
      return language(text) != null && text.text.length() <= RichMessageMarkdown.MAX_SOURCE;
    }
    if (content instanceof TdApi.MessageDocument) {
      TdApi.MessageDocument document = (TdApi.MessageDocument) content;
      return language(document.caption) != null && document.document != null && document.document.fileName != null &&
        document.document.fileName.startsWith(FILE_PREFIX) && document.document.fileName.endsWith(".md") &&
        document.document.document != null && document.document.document.size <= MAX_FILE_BYTES && document.document.document.expectedSize <= MAX_FILE_BYTES;
    }
    return false;
  }

  private static @Nullable String language (@Nullable TdApi.FormattedText text) {
    if (text == null || text.text == null || text.text.length() == 0 || text.entities == null || text.entities.length != 1) return null;
    TdApi.TextEntity entity = text.entities[0];
    if (entity == null || entity.offset != 0 || entity.length != text.text.length() || !(entity.type instanceof TdApi.TextEntityTypePreCode)) return null;
    String language = ((TdApi.TextEntityTypePreCode) entity.type).language;
    return LANGUAGE.equals(language) || LANGUAGE_RTL.equals(language) ? language : null;
  }

  public static boolean isRtl (TdApi.MessageContent content) {
    return LANGUAGE_RTL.equals(language(content instanceof TdApi.MessageText ? ((TdApi.MessageText) content).text : ((TdApi.MessageDocument) content).caption));
  }

  public static TdApi.FormattedText marked (String source, boolean rtl) {
    if (source.length() == 0 || source.length() > RichMessageMarkdown.MAX_SOURCE) throw new IllegalArgumentException("source_limit");
    return new TdApi.FormattedText(source, new TdApi.TextEntity[] {
      new TdApi.TextEntity(0, source.length(), new TdApi.TextEntityTypePreCode(rtl ? LANGUAGE_RTL : LANGUAGE))
    });
  }

  public static String excerpt (String source, int limit) {
    int end = Math.min(source.length(), Math.max(1, limit));
    if (end < source.length() && Character.isHighSurrogate(source.charAt(end - 1))) end--;
    return source.substring(0, end);
  }

  public static Set<String> photoNames (String source) {
    Set<String> names = new LinkedHashSet<>();
    for (PhotoReference reference : photoReferences(source)) {
      names.add(reference.name);
      if (names.size() > RichMessageDocument.MAX_MEDIA) throw new IllegalArgumentException("media_limit");
    }
    return names;
  }

  public static String photoName (long messageId) {
    if (messageId <= 0) throw new IllegalArgumentException("photo_reference");
    return "tg_" + messageId;
  }

  public static long photoMessageId (String name) {
    if (!name.startsWith("tg_")) throw new IllegalArgumentException("photo_reference");
    long id = Long.parseLong(name.substring(3));
    if (!photoName(id).equals(name)) throw new IllegalArgumentException("photo_reference");
    return id;
  }

  public static String replacePhotos (String source, Map<String, String> names) {
    StringBuilder result = new StringBuilder();
    int position = 0;
    for (PhotoReference reference : photoReferences(source)) {
      result.append(source, position, reference.start);
      String name = names.get(reference.name);
      result.append(name != null ? name : reference.name);
      position = reference.start + reference.name.length();
    }
    result.append(source, position, source.length());
    if (result.length() > RichMessageMarkdown.MAX_SOURCE) throw new IllegalArgumentException("source_limit");
    return result.toString();
  }

  private static final class PhotoReference {
    final int start;
    final String name;
    PhotoReference (int start, String name) { this.start = start; this.name = name; }
  }

  private static List<PhotoReference> photoReferences (String source) {
    List<PhotoReference> references = new ArrayList<>();
    String delimiter = null;
    for (int start = 0; start < source.length();) {
      int end = source.indexOf('\n', start);
      if (end == -1) end = source.length();
      String line = source.substring(start, end);
      if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
      if (delimiter != null) {
        if (line.matches(Pattern.quote(delimiter.substring(0, 1)) + "{" + delimiter.length() + ",}\\s*")) delimiter = null;
      } else {
        Matcher fence = FENCE.matcher(line), photo = PHOTO.matcher(line);
        if (fence.matches()) delimiter = fence.group(1);
        else if (photo.matches()) references.add(new PhotoReference(start + photo.start(1), photo.group(1)));
      }
      start = end + 1;
    }
    return references;
  }
}
