package moe.kirao.mgx.utils;

import androidx.annotation.Nullable;
import org.drinkless.tdlib.TdApi;

public final class PollMediaUtils {
  private PollMediaUtils () { }

  @Nullable public static TdApi.InputPollMedia fromMessage (TdApi.InputMessageContent content, boolean question) {
    if (content instanceof TdApi.InputMessagePhoto) return new TdApi.InputPollMediaPhoto(((TdApi.InputMessagePhoto) content).photo);
    if (content instanceof TdApi.InputMessageVideo) return new TdApi.InputPollMediaVideo(((TdApi.InputMessageVideo) content).video);
    if (content instanceof TdApi.InputMessageAnimation) return new TdApi.InputPollMediaAnimation(((TdApi.InputMessageAnimation) content).animation);
    if (content instanceof TdApi.InputMessageSticker) return new TdApi.InputPollMediaSticker(((TdApi.InputMessageSticker) content).sticker);
    if (content instanceof TdApi.InputMessageLocation) return new TdApi.InputPollMediaLocation(((TdApi.InputMessageLocation) content).location);
    if (content instanceof TdApi.InputMessageVenue) return new TdApi.InputPollMediaVenue(((TdApi.InputMessageVenue) content).venue);
    if (question && content instanceof TdApi.InputMessageAudio) return new TdApi.InputPollMediaAudio(((TdApi.InputMessageAudio) content).audio);
    if (question && content instanceof TdApi.InputMessageDocument) return new TdApi.InputPollMediaDocument(((TdApi.InputMessageDocument) content).document);
    return null;
  }
}
