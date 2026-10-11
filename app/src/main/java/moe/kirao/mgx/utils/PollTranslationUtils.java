package moe.kirao.mgx.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

import tgx.td.Td;

public final class PollTranslationUtils {
  private static final TdApi.FormattedText SEPARATOR = new TdApi.FormattedText("\n\n• ", new TdApi.TextEntity[0]);

  private PollTranslationUtils () { }

  public static int getQuestionIndex (@Nullable TdApi.FormattedText description) {
    return Td.isEmpty(description) ? 0 : 1;
  }

  @NonNull
  public static TdApi.FormattedText buildSource (@Nullable TdApi.FormattedText description, @NonNull TdApi.Poll poll) {
    TdApi.FormattedText text = escapeSeparator(poll.question);
    if (!Td.isEmpty(description)) {
      text = Td.concat(escapeSeparator(description), SEPARATOR, text);
    }
    for (TdApi.PollOption option : poll.options) {
      text = Td.concat(text, SEPARATOR, escapeSeparator(option.text));
    }
    return text;
  }

  @Nullable
  public static TdApi.FormattedText[] splitResult (@Nullable TdApi.FormattedText text, @Nullable TdApi.FormattedText description, int optionCount) {
    if (text == null) {
      return null;
    }
    TdApi.FormattedText[] parts = Td.split(text, "•");
    if (parts.length != optionCount + getQuestionIndex(description) + 1) {
      return null;
    }
    for (int i = 0; i < parts.length; i++) {
      parts[i] = Td.trim(parts[i]);
    }
    return parts;
  }

  private static TdApi.FormattedText escapeSeparator (@Nullable TdApi.FormattedText text) {
    // Replace one UTF-16 unit with one unit so entity offsets stay valid.
    return Td.isEmpty(text) ? Td.emptyFormattedText() : new TdApi.FormattedText(text.text.replace('•', ' '), text.entities);
  }
}
