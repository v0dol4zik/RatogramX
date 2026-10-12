package moe.kirao.mgx.utils;

import androidx.annotation.Nullable;

public final class VideoQualityPolicy {
  private VideoQualityPolicy () { }

  public static final int ROUND_DATA_SAVER = 0;
  public static final int ROUND_STANDARD = 1;
  public static final int ROUND_RECOMMENDED = 2;
  public static final int ROUND_MAXIMUM = 3;
  public static final int BITRATE_AUTO = 0;
  public static final int BITRATE_HIGH = 1;
  public static final int BITRATE_MAXIMUM = 2;

  public static final class RoundPreset {
    public final int resolution, videoBitrate, audioBitrate, cameraResolution;

    private RoundPreset (int resolution, int videoBitrate, int audioBitrate, int cameraResolution) {
      this.resolution = resolution;
      this.videoBitrate = videoBitrate;
      this.audioBitrate = audioBitrate;
      this.cameraResolution = cameraResolution;
    }
  }

  private static final RoundPreset[] ROUND_PRESETS = {
    new RoundPreset(240, 400_000, 32_000, 280),
    new RoundPreset(320, 600_000, 32_000, 480),
    new RoundPreset(384, 1_000_000, 64_000, 640),
    new RoundPreset(480, 1_800_000, 64_000, 720)
  };

  public static int defaultRoundQuality (boolean legacyHq) {
    return legacyHq ? ROUND_STANDARD : ROUND_RECOMMENDED;
  }

  public static int normalizeRoundQuality (int value, int fallback) {
    return value >= ROUND_DATA_SAVER && value <= ROUND_MAXIMUM ? value : fallback;
  }

  public static int normalizeBitrateMode (int value) {
    return value >= BITRATE_AUTO && value <= BITRATE_MAXIMUM ? value : BITRATE_AUTO;
  }

  public static RoundPreset roundPreset (int quality) {
    return ROUND_PRESETS[normalizeRoundQuality(quality, ROUND_RECOMMENDED)];
  }

  public static long outputBitrate (int width, int height, int fps, long requestedBitrate, long inputBitrate, int mode, boolean preserveOriginal) {
    double scale = mode == BITRATE_HIGH ? 0.15 : mode == BITRATE_MAXIMUM ? 0.2 : 0.109;
    long target = preserveOriginal && inputBitrate > 0 ? inputBitrate : requestedBitrate > 0 ? requestedBitrate :
      Math.round((double) width * height * fps * scale);
    if (inputBitrate > 0) target = Math.min(target, inputBitrate);
    return Math.max(1, Math.min(Integer.MAX_VALUE, target));
  }

  /** Returns even major/minor dimensions shared by the preview and encoder. */
  public static @Nullable int[] outputSize (int width, int height, int majorLimit, int minorLimit) {
    if (width <= 0 || height <= 0 || majorLimit <= 0 || minorLimit <= 0) return null;
    int major = Math.max(width, height), minor = Math.min(width, height);
    float ratio = Math.min((float) majorLimit / major, (float) minorLimit / minor);
    if (ratio >= 1f) return null;
    major = Math.max(2, (int) (major * ratio));
    minor = Math.max(2, (int) (minor * ratio));
    return new int[] {major - major % 2, minor - minor % 2};
  }
}
