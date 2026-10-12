package moe.kirao.mgx;

import moe.kirao.mgx.utils.GhostModeUtils;
import moe.kirao.mgx.utils.VideoQualityPolicy;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MoexConfigTest {
  @Test public void drawerAndMediaMasksRemainStable() {
    assertEquals(MoexConfig.DRAWER_ITEMS_ALL,
      MoexConfig.DRAWER_ITEM_CONTACTS | MoexConfig.DRAWER_ITEM_ARCHIVE |
      MoexConfig.DRAWER_ITEM_CALLS | MoexConfig.DRAWER_ITEM_SAVED_MESSAGES |
      MoexConfig.DRAWER_ITEM_HELP | MoexConfig.DRAWER_ITEM_NIGHT_MODE |
      MoexConfig.DRAWER_ITEM_PROXY);
    assertEquals(MoexConfig.AUTO_PAUSE_MEDIA_ALL,
      MoexConfig.AUTO_PAUSE_MEDIA_VOICE | MoexConfig.AUTO_PAUSE_MEDIA_ROUND |
      MoexConfig.AUTO_PAUSE_MEDIA_VIDEO);
  }

  @Test public void ghostMaskChecksOnlyRequestedBits() {
    assertTrue(GhostModeUtils.isFlagEnabled(MoexConfig.GHOST_MODE_ALL, MoexConfig.GHOST_NO_READ));
    assertTrue(GhostModeUtils.isFlagEnabled(MoexConfig.GHOST_NO_TYPING, MoexConfig.GHOST_NO_TYPING));
    assertFalse(GhostModeUtils.isFlagEnabled(MoexConfig.GHOST_NO_TYPING, MoexConfig.GHOST_NO_READ));
  }

  @Test public void enablingGhostRestoresTheLastNonemptySelection() {
    assertEquals(MoexConfig.GHOST_NO_READ,
      GhostModeUtils.flagsForEnabling(0, MoexConfig.GHOST_NO_READ));
    assertEquals(MoexConfig.GHOST_NO_TYPING,
      GhostModeUtils.flagsForEnabling(MoexConfig.GHOST_NO_TYPING, MoexConfig.GHOST_NO_READ));
    assertEquals(MoexConfig.GHOST_MODE_ALL, GhostModeUtils.flagsForEnabling(0, 0));
    assertEquals(MoexConfig.GHOST_MODE_ALL, GhostModeUtils.flagsForEnabling(0, 1 << 20));
  }

  @Test public void videoQualityDefaultsRespectLegacyHqAndValidateImportedModes () {
    assertEquals(VideoQualityPolicy.ROUND_STANDARD, VideoQualityPolicy.defaultRoundQuality(true));
    assertEquals(VideoQualityPolicy.ROUND_RECOMMENDED, VideoQualityPolicy.defaultRoundQuality(false));
    assertEquals(VideoQualityPolicy.ROUND_STANDARD, VideoQualityPolicy.normalizeRoundQuality(-1, VideoQualityPolicy.ROUND_STANDARD));
    assertEquals(VideoQualityPolicy.ROUND_RECOMMENDED, VideoQualityPolicy.normalizeRoundQuality(10, VideoQualityPolicy.ROUND_RECOMMENDED));
    assertEquals(VideoQualityPolicy.ROUND_DATA_SAVER, VideoQualityPolicy.normalizeRoundQuality(0, VideoQualityPolicy.ROUND_RECOMMENDED));
    assertEquals(VideoQualityPolicy.BITRATE_AUTO, VideoQualityPolicy.normalizeBitrateMode(-1));
    assertEquals(VideoQualityPolicy.BITRATE_AUTO, VideoQualityPolicy.normalizeBitrateMode(10));
    assertEquals(VideoQualityPolicy.BITRATE_MAXIMUM, VideoQualityPolicy.normalizeBitrateMode(VideoQualityPolicy.BITRATE_MAXIMUM));
  }

}
