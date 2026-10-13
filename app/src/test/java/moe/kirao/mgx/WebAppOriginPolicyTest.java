package moe.kirao.mgx;

import org.junit.Test;

import moe.kirao.mgx.utils.WebAppOriginPolicy;

import static org.junit.Assert.*;

public class WebAppOriginPolicyTest {
  @Test public void normalizesHttpsOrigins () {
    assertEquals("https://example.org", WebAppOriginPolicy.origin("HTTPS://EXAMPLE.org:443/app?token=example#start"));
    assertEquals("https://example.org:8443", WebAppOriginPolicy.origin("https://example.org:8443/app"));
    assertEquals("https://[::1]", WebAppOriginPolicy.origin("https://[::1]/app"));
  }

  @Test public void rejectsUnsafeUrls () {
    for (String url : new String[] {null, "http://example.org", "file:///app.html", "javascript:alert(1)", "https://user:password@example.org", "https://example.org:0", "https://example.org:65536", "https://example.org\\@evil.org", "https://", "//example.org/app"}) {
      assertNull(url, WebAppOriginPolicy.origin(url));
    }
  }

  @Test public void rejectsOtherOriginsAndFrames () {
    String origin = "https://example.org";
    assertTrue(WebAppOriginPolicy.acceptsEvent(origin, "https://example.org/app", true));
    assertFalse(WebAppOriginPolicy.acceptsEvent(origin, "https://example.org/app", false));
    assertFalse(WebAppOriginPolicy.acceptsEvent(origin, "https://example.org.evil.org", true));
    assertFalse(WebAppOriginPolicy.acceptsEvent(origin, "https://example.org:8443", true));
    assertFalse(WebAppOriginPolicy.acceptsEvent(origin, "https://evil.org", true));
    assertFalse(WebAppOriginPolicy.acceptsEvent(null, "https://example.org", true));
  }

  @Test public void limitsDataByUtf8Bytes () {
    assertTrue(WebAppOriginPolicy.validData("a".repeat(4096)));
    assertFalse(WebAppOriginPolicy.validData("a".repeat(4097)));
    assertTrue(WebAppOriginPolicy.validData("я".repeat(2048)));
    assertFalse(WebAppOriginPolicy.validData("я".repeat(2049)));
    assertFalse(WebAppOriginPolicy.validData(""));
    assertFalse(WebAppOriginPolicy.validData(null));
  }
}
