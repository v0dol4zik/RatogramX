package moe.kirao.mgx;

import org.junit.Test;
import org.thunderdog.challegram.util.LowEndPerformanceProfile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LowEndPerformanceProfileTest {
  @Test public void lowRamAlwaysQualifies() {
    assertTrue(LowEndPerformanceProfile.evaluate(true, 512, 8L * 1024 * 1024 * 1024, 8));
  }

  @Test public void smallMemoryClassQualifies() {
    assertTrue(LowEndPerformanceProfile.evaluate(false, 192, 8L * 1024 * 1024 * 1024, 8));
  }

  @Test public void limitedMemoryOrCpuQualifies() {
    assertTrue(LowEndPerformanceProfile.evaluate(false, 256, 3L * 1024 * 1024 * 1024, 8));
    assertTrue(LowEndPerformanceProfile.evaluate(false, 256, 4L * 1024 * 1024 * 1024, 4));
  }

  @Test public void modernDeviceDoesNotQualify() {
    assertFalse(LowEndPerformanceProfile.evaluate(false, 512, 8L * 1024 * 1024 * 1024, 8));
  }
}
