package moe.kirao.mgx.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Selection is restricted to stable Ratogram APKs in our repository. */
public final class GithubReleasePolicy {
  public static final String REPOSITORY = "https://github.com/v0dol4zik/RatogramX";
  public static final String RELEASES = REPOSITORY + "/releases";
  public static final String API = "https://api.github.com/repos/v0dol4zik/RatogramX/releases?per_page=20";
  private static final Pattern APK = Pattern.compile("^Ratogram-X-(\\d+\\.\\d+\\.\\d+\\.\\d+)(?:-(legacy|lollipop|marshmallow))?(?:-(arm64-v8a|armeabi-v7a|universal|x86_64|x86))?\\.apk$");
  private GithubReleasePolicy () { }

  public static final class Release {
    public final String version, fileName, url, releaseUrl, sha256;
    public final long size;
    private final int rank;
    private Release (String version, String fileName, String url, String releaseUrl, String sha256, long size, int rank) {
      this.version = version; this.fileName = fileName; this.url = url; this.releaseUrl = releaseUrl; this.sha256 = sha256; this.size = size; this.rank = rank;
    }
  }

  public static Release select (String json, String installedVersion, int sdk, String[] abis) {
    JsonArray releases = JsonParser.parseString(json).getAsJsonArray();
    Release best = null;
    for (JsonElement element : releases) {
      JsonObject release = element.getAsJsonObject();
      if (bool(release, "draft") || bool(release, "prerelease") || !release.has("assets")) continue;
      for (JsonElement assetElement : release.getAsJsonArray("assets")) {
        JsonObject asset = assetElement.getAsJsonObject();
        String name = string(asset, "name"); Matcher matcher = APK.matcher(name);
        if (!matcher.matches() || compareVersion(matcher.group(1), installedVersion) <= 0) continue;
        int minSdk = "legacy".equals(matcher.group(2)) ? 16 : "lollipop".equals(matcher.group(2)) ? 21 : "marshmallow".equals(matcher.group(2)) ? 23 : 24;
        if (sdk < minSdk) continue;
        String abi = matcher.group(3);
        int rank = -1;
        if (abi == null || abi.equals("universal")) rank = 1;
        else for (int i = 0; i < abis.length; i++) if (abi.equals(abis[i])) { rank = 100 - i; break; }
        if (rank < 0) continue;
        rank = rank * 100 + minSdk;
        String url = string(asset, "browser_download_url");
        if (!url.startsWith(REPOSITORY + "/releases/download/") || url.contains("..") || !url.toLowerCase(Locale.US).endsWith(".apk")) continue;
        long size = asset.has("size") ? asset.get("size").getAsLong() : 0;
        if (size <= 0 || size > 1024L * 1024 * 1024) continue;
        String digest = string(asset, "digest");
        String sha256 = digest.matches("sha256:[a-fA-F0-9]{64}") ? digest.substring(7).toLowerCase(Locale.US) : null;
        String releaseUrl = string(release, "html_url");
        if (!releaseUrl.startsWith(RELEASES + "/tag/")) releaseUrl = RELEASES;
        Release candidate = new Release(matcher.group(1), name, url, releaseUrl, sha256, size, rank);
        if (best == null || compareVersion(candidate.version, best.version) > 0 || compareVersion(candidate.version, best.version) == 0 && rank > best.rank) best = candidate;
      }
    }
    return best;
  }

  public static int compareVersion (String a, String b) {
    String[] left = a.replaceFirst("^v", "").split("[.-]"), right = b.replaceFirst("^v", "").split("[.-]");
    for (int i = 0; i < 4; i++) {
      long l = i < left.length && left[i].matches("\\d{1,9}") ? Long.parseLong(left[i]) : 0;
      long r = i < right.length && right[i].matches("\\d{1,9}") ? Long.parseLong(right[i]) : 0;
      if (l != r) return Long.compare(l, r);
    }
    return 0;
  }

  private static String string (JsonObject object, String name) {
    return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : "";
  }
  private static boolean bool (JsonObject object, String name) { return object.has(name) && object.get(name).getAsBoolean(); }
}
