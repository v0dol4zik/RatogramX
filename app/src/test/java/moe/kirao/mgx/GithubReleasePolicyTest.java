package moe.kirao.mgx;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;
import moe.kirao.mgx.utils.GithubReleasePolicy;
import static org.junit.Assert.*;

public class GithubReleasePolicyTest {
  private JsonObject release (String... names) {
    JsonObject release = new JsonObject(); release.addProperty("draft", false); release.addProperty("prerelease", false);
    release.addProperty("html_url", GithubReleasePolicy.RELEASES + "/tag/v0.29.2.1818");
    JsonArray assets = new JsonArray();
    for (String name : names) {
      JsonObject asset = new JsonObject(); asset.addProperty("name", name); asset.addProperty("size", 1000);
      asset.addProperty("browser_download_url", GithubReleasePolicy.REPOSITORY + "/releases/download/v0.29.2.1818/" + name);
      asset.addProperty("digest", "sha256:" + "a".repeat(64)); assets.add(asset);
    }
    release.add("assets", assets); return release;
  }
  private GithubReleasePolicy.Release select (int sdk, String[] abis, JsonObject... releases) {
    JsonArray array = new JsonArray(); for (JsonObject r : releases) array.add(r);
    return GithubReleasePolicy.select(array.toString(), "0.29.1.1817", sdk, abis);
  }

  @Test public void numericVersionsDoNotCompareLexicographically () {
    assertTrue(GithubReleasePolicy.compareVersion("v0.30.0.1820", "0.29.9.1819-arm64-v8a") > 0);
    assertTrue(GithubReleasePolicy.compareVersion("0.29.10.1820", "0.29.9.1819") > 0);
    assertEquals(0, GithubReleasePolicy.compareVersion("0.29.1.1817", "0.29.1.1817-debug"));
  }
  @Test public void nativeAbiPreferredWithUniversalFallback () {
    JsonObject release = release("Ratogram-X-0.29.2.1818.apk", "Ratogram-X-0.29.2.1818-armeabi-v7a.apk", "Ratogram-X-0.29.2.1818-arm64-v8a.apk");
    assertTrue(select(35, new String[] {"arm64-v8a", "armeabi-v7a"}, release).fileName.endsWith("-arm64-v8a.apk"));
    assertEquals("Ratogram-X-0.29.2.1818.apk", select(35, new String[] {"x86_64"}, release).fileName);
    assertTrue(select(35, new String[] {"armeabi-v7a"}, release).fileName.endsWith("-armeabi-v7a.apk"));
  }
  @Test public void legacyAndroidGetsCompatibleSdkFlavor () {
    JsonObject release = release("Ratogram-X-0.29.2.1818-armeabi-v7a.apk", "Ratogram-X-0.29.2.1818-lollipop-armeabi-v7a.apk", "Ratogram-X-0.29.2.1818-legacy-armeabi-v7a.apk");
    assertTrue(select(21, new String[] {"armeabi-v7a"}, release).fileName.contains("-lollipop-"));
    assertTrue(select(19, new String[] {"armeabi-v7a"}, release).fileName.contains("-legacy-"));
    assertNull(select(15, new String[] {"armeabi-v7a"}, release));
  }
  @Test public void latestCompatibleStableReleaseIsSelected () {
    JsonObject old = release("Ratogram-X-0.29.2.1818-arm64-v8a.apk"), newer = release("Ratogram-X-0.29.3.1819-arm64-v8a.apk");
    assertEquals("0.29.3.1819", select(35, new String[] {"arm64-v8a"}, old, newer).version);
    newer.addProperty("prerelease", true);
    assertEquals("0.29.2.1818", select(35, new String[] {"arm64-v8a"}, newer, old).version);
    old.addProperty("draft", true);
    assertNull(select(35, new String[] {"arm64-v8a"}, old, newer));
  }
  @Test public void oldEqualDebugAndUpstreamApksAreIgnored () {
    assertNull(select(35, new String[] {"arm64-v8a"}, release("Ratogram-X-0.29.1.1817-arm64-v8a.apk", "Ratogram-X-0.29.0.1807-arm64-v8a.apk",
      "Ratogram-X-0.29.2.1818-arm64-v8a-debug.apk", "Telegram-X-0.29.2.1818-arm64-v8a.apk")));
  }
  @Test public void untrustedDownloadLocationIsRejected () {
    JsonObject release = release("Ratogram-X-0.29.2.1818-arm64-v8a.apk");
    release.getAsJsonArray("assets").get(0).getAsJsonObject().addProperty("browser_download_url", "https://github.com/TGX-Android/Telegram-X/releases/download/test/app.apk");
    assertNull(select(35, new String[] {"arm64-v8a"}, release));
  }
  @Test public void checksumAndReleaseNotesComeFromSelectedAsset () {
    GithubReleasePolicy.Release result = select(35, new String[] {"arm64-v8a"}, release("Ratogram-X-0.29.2.1818-arm64-v8a.apk"));
    assertEquals("a".repeat(64), result.sha256);
    assertTrue(result.releaseUrl.startsWith(GithubReleasePolicy.RELEASES));
    assertEquals(1000, result.size);
  }
}
