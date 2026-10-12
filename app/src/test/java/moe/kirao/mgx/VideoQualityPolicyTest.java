package moe.kirao.mgx;

import org.junit.Test;

import moe.kirao.mgx.utils.VideoQualityPolicy;

import static org.junit.Assert.*;

public class VideoQualityPolicyTest {
  @Test public void presetsKeepCameraCaptureAndEncodedDimensionsConsistent () {
    int previousResolution = 0, previousBitrate = 0;
    for (int i = VideoQualityPolicy.ROUND_DATA_SAVER; i <= VideoQualityPolicy.ROUND_MAXIMUM; i++) {
      VideoQualityPolicy.RoundPreset preset = VideoQualityPolicy.roundPreset(i);
      assertTrue(preset.resolution > previousResolution);
      assertTrue(preset.videoBitrate > previousBitrate);
      assertTrue(preset.cameraResolution >= preset.resolution);
      assertEquals(0, preset.resolution % 16);
      previousResolution = preset.resolution;
      previousBitrate = preset.videoBitrate;
    }
    assertEquals(384, VideoQualityPolicy.roundPreset(VideoQualityPolicy.ROUND_RECOMMENDED).resolution);
    assertEquals(1_000_000, VideoQualityPolicy.roundPreset(VideoQualityPolicy.ROUND_RECOMMENDED).videoBitrate);
  }

  @Test public void compressedVideoBitratePreservesMoreDetailWithoutUpscalingTheSource () {
    long automatic = VideoQualityPolicy.outputBitrate(1280, 720, 30, -1, 10_000_000, VideoQualityPolicy.BITRATE_AUTO, false);
    long high = VideoQualityPolicy.outputBitrate(1280, 720, 30, -1, 10_000_000, VideoQualityPolicy.BITRATE_HIGH, false);
    long maximum = VideoQualityPolicy.outputBitrate(1280, 720, 30, -1, 10_000_000, VideoQualityPolicy.BITRATE_MAXIMUM, false);
    assertTrue(high > automatic);
    assertTrue(maximum > high);
    assertEquals(400_000, VideoQualityPolicy.outputBitrate(1280, 720, 30, -1, 400_000, VideoQualityPolicy.BITRATE_MAXIMUM, false));
    assertEquals(900_000, VideoQualityPolicy.outputBitrate(1280, 720, 30, 900_000, 10_000_000, VideoQualityPolicy.BITRATE_AUTO, false));
  }

  @Test public void originalQualityTrimsDoNotReduceTheRecordedRoundBitrate () {
    for (int mode = VideoQualityPolicy.BITRATE_AUTO; mode <= VideoQualityPolicy.BITRATE_MAXIMUM; mode++) {
      assertEquals(1_000_000, VideoQualityPolicy.outputBitrate(384, 384, 30, -1, 1_000_000, mode, true));
    }
  }

  @Test public void missingMetadataAndLargeFramesProduceAValidEncoderBitrate () {
    assertTrue(VideoQualityPolicy.outputBitrate(3840, 2160, 60, -1, 0, VideoQualityPolicy.BITRATE_MAXIMUM, false) > 0);
    assertTrue(VideoQualityPolicy.outputBitrate(7680, 4320, 120, -1, -1, VideoQualityPolicy.BITRATE_MAXIMUM, false) > 0);
    assertEquals(Integer.MAX_VALUE, VideoQualityPolicy.outputBitrate(100_000, 100_000, 120, -1, 0, VideoQualityPolicy.BITRATE_MAXIMUM, false));
  }

  @Test public void landscapeAndRotatedVideoFitBothPreviewLimits () {
    assertArrayEquals(new int[] {854, 480}, VideoQualityPolicy.outputSize(1920, 1080, 854, 854));
    assertArrayEquals(new int[] {854, 480}, VideoQualityPolicy.outputSize(1080, 1920, 854, 854));
    assertArrayEquals(new int[] {852, 480}, VideoQualityPolicy.outputSize(1920, 1080, 854, 480));
    assertNull(VideoQualityPolicy.outputSize(640, 360, 854, 480));
    assertNull(VideoQualityPolicy.outputSize(1920, 1080, -1, -1));
  }
}
