package moe.kirao.mgx;

import moe.kirao.mgx.utils.GhostModeUtils;
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

}
