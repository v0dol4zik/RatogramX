package moe.kirao.mgx.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class WebAppOriginPolicy {
  private WebAppOriginPolicy () { }

  public static String origin (String url) {
    if (url == null) return null;
    try {
      URI uri = new URI(url);
      String host = uri.getHost();
      int port = uri.getPort();
      if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getRawUserInfo() != null || port == 0 || port > 65535) {
        return null;
      }
      return "https://" + host.toLowerCase(Locale.ROOT) + (port == -1 || port == 443 ? "" : ":" + port);
    } catch (URISyntaxException ignored) {
      return null;
    }
  }

  public static boolean acceptsEvent (String trustedOrigin, String sourceUrl, boolean isMainFrame) {
    return isMainFrame && trustedOrigin != null && trustedOrigin.equals(origin(sourceUrl));
  }

  public static boolean validData (String data) {
    return data != null && !data.isEmpty() && data.getBytes(StandardCharsets.UTF_8).length <= 4096;
  }
}
