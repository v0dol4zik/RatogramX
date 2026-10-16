package moe.kirao.mgx;

import moe.kirao.mgx.utils.PollMediaUtils;
import org.drinkless.tdlib.TdApi;
import org.junit.Test;
import static org.junit.Assert.*;

public class PollMediaUtilsTest {
  @Test public void galleryInputsKeepTheGeneratedFileAndDoNotAddCaptions () {
    TdApi.InputPhoto photo = new TdApi.InputPhoto();
    photo.photo = new TdApi.InputFileGenerated("original.jpg", "conversion", 123);
    TdApi.InputPollMediaPhoto result = (TdApi.InputPollMediaPhoto) PollMediaUtils.fromMessage(new TdApi.InputMessagePhoto(photo, new TdApi.FormattedText("caption", null), true, null, true), false);
    assertSame(photo, result.photo);
    TdApi.InputVideo video = new TdApi.InputVideo();
    assertSame(video, ((TdApi.InputPollMediaVideo) PollMediaUtils.fromMessage(new TdApi.InputMessageVideo(video, null, false, null, false), false)).video);
    TdApi.InputAnimation animation = new TdApi.InputAnimation();
    assertSame(animation, ((TdApi.InputPollMediaAnimation) PollMediaUtils.fromMessage(new TdApi.InputMessageAnimation(animation, null, false, false), false)).animation);
  }

  @Test public void documentsAndAudioAreRestrictedToQuestionAttachments () {
    TdApi.InputMessageDocument document = new TdApi.InputMessageDocument(new TdApi.InputDocument(), null);
    TdApi.InputMessageAudio audio = new TdApi.InputMessageAudio(new TdApi.InputAudio(), null);
    assertNull(PollMediaUtils.fromMessage(document, false));
    assertNull(PollMediaUtils.fromMessage(audio, false));
    assertTrue(PollMediaUtils.fromMessage(document, true) instanceof TdApi.InputPollMediaDocument);
    assertTrue(PollMediaUtils.fromMessage(audio, true) instanceof TdApi.InputPollMediaAudio);
    assertNull(PollMediaUtils.fromMessage(null, false));
  }

  @Test public void locationsVenuesAndStickersPreserveTheirPayload () {
    TdApi.Location location = new TdApi.Location(55, 37, 0);
    assertSame(location, ((TdApi.InputPollMediaLocation) PollMediaUtils.fromMessage(new TdApi.InputMessageLocation(location), false)).location);
    TdApi.Venue venue = new TdApi.Venue();
    assertSame(venue, ((TdApi.InputPollMediaVenue) PollMediaUtils.fromMessage(new TdApi.InputMessageVenue(venue), false)).venue);
    TdApi.InputSticker sticker = new TdApi.InputSticker();
    assertSame(sticker, ((TdApi.InputPollMediaSticker) PollMediaUtils.fromMessage(new TdApi.InputMessageSticker(sticker, ""), false)).sticker);
  }
}
