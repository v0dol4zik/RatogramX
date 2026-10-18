/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.util;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.ConnectionListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibContext;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AppInstallationUtil;
import me.vkryl.core.reference.ReferenceList;
import moe.kirao.mgx.utils.GithubReleasePolicy;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Ratogram updates come exclusively from the public Ratogram X GitHub releases. */
public final class AppUpdater implements ConnectionListener {
  public interface Listener {
    void onAppUpdateStateChanged (@State int state, @State int oldState, boolean isApk);
    default void onAppUpdateDownloadProgress (long bytesDownloaded, long totalBytesToDownload) { }
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({State.NONE, State.CHECKING, State.AVAILABLE, State.DOWNLOADING, State.READY_TO_INSTALL, State.INSTALLING})
  public @interface State { int NONE = 0, CHECKING = 1, AVAILABLE = 2, DOWNLOADING = 3, READY_TO_INSTALL = 4, INSTALLING = 5; }
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({FlowType.NONE, FlowType.GITHUB})
  public @interface FlowType { int NONE = 0, GITHUB = 1; }

  private final BaseActivity context;
  private final ReferenceList<Listener> listeners = new ReferenceList<>();
  private final OkHttpClient client = new OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS).callTimeout(10, TimeUnit.MINUTES).build();
  private @State int state = State.NONE;
  private GithubReleasePolicy.Release release;
  private File downloadedApk;
  private long bytesDownloaded, totalBytesToDownload;
  private boolean updateOffered, checkFailed, destroyed;
  private Tdlib wifiTdlib;

  public AppUpdater (BaseActivity context) { this.context = context; }
  public @State int state () { return state; }
  public @FlowType int flowType () { return release == null ? FlowType.NONE : FlowType.GITHUB; }
  public long totalBytesToDownload () { return totalBytesToDownload; }
  public long bytesDownloaded () { return bytesDownloaded; }
  public @Nullable String displayVersion () { return release != null ? release.version : null; }
  public @Nullable String commit () { return null; }
  public boolean checkFailed () { return checkFailed; }
  public void addListener (Listener listener) { listeners.add(listener); }
  public void removeListener (Listener listener) { listeners.remove(listener); }

  public static AppInstallationUtil.PublicMarketUrls publicMarketUrls () {
    return new AppInstallationUtil.PublicMarketUrls(GithubReleasePolicy.RELEASES, null, null, null, null);
  }
  public static AppInstallationUtil.DownloadUrl getDownloadUrl (@Nullable String ignoredServerSuggestion) {
    return new AppInstallationUtil.DownloadUrl(GithubReleasePolicy.RELEASES);
  }

  private void setState (@State int value) {
    if (destroyed) return;
    int old = state;
    if (old == value) return;
    state = value;
    for (Listener listener : listeners) listener.onAppUpdateStateChanged(value, old, release != null);
  }

  public void checkForUpdates () {
    checkForUpdates(true);
  }

  public void checkForUpdates (boolean userInitiated) {
    if (destroyed) return;
    if (state == State.READY_TO_INSTALL) { if (userInitiated) installUpdate(); return; }
    if (state == State.AVAILABLE) { if (userInitiated) updateOffered = false; offerUpdate(userInitiated); return; }
    if (state != State.NONE) return;
    long now = System.currentTimeMillis(), last = Settings.instance().getLong("ratogram_update_last_check", 0);
    if (!userInitiated && now >= last && now - last < TimeUnit.MINUTES.toMillis(30)) return;
    Settings.instance().putLong("ratogram_update_last_check", now);
    checkFailed = false;
    setState(State.CHECKING);
    Request request = new Request.Builder().url(GithubReleasePolicy.API).header("Accept", "application/vnd.github+json")
      .header("X-GitHub-Api-Version", "2022-11-28").header("User-Agent", "RatogramX/" + BuildConfig.ORIGINAL_VERSION_NAME).build();
    client.newCall(request).enqueue(new Callback() {
      @Override public void onFailure (Call call, IOException error) { UI.post(() -> failCheck(userInitiated)); }
      @Override public void onResponse (Call call, Response response) {
        try (response) {
          if (!response.isSuccessful() || response.body() == null) throw new IOException("Release request failed");
          response.body().source().request(2 * 1024 * 1024 + 1L);
          if (response.body().source().getBuffer().size() > 2 * 1024 * 1024) throw new IOException("Release response too large");
          String json = response.body().string();
          String[] abis = Build.VERSION.SDK_INT >= 21 ? Build.SUPPORTED_ABIS : new String[] {Build.CPU_ABI, Build.CPU_ABI2};
          GithubReleasePolicy.Release result = GithubReleasePolicy.select(json, BuildConfig.ORIGINAL_VERSION_NAME, Build.VERSION.SDK_INT, abis);
          UI.post(() -> {
            if (destroyed) return;
            release = result;
            bytesDownloaded = 0;
            totalBytesToDownload = result != null ? result.size : 0;
            updateOffered = false;
            setState(result != null ? State.AVAILABLE : State.NONE);
            if (result != null) offerUpdate(userInitiated);
            else if (userInitiated) UI.showToast(R.string.RatogramUpdateNotAvailable, Toast.LENGTH_SHORT);
          });
        } catch (Exception e) { UI.post(() -> failCheck(userInitiated)); }
      }
    });
  }

  private void failCheck (boolean userInitiated) {
    if (destroyed) return;
    checkFailed = true;
    setState(State.NONE);
    if (userInitiated) UI.showToast(R.string.RatogramUpdateCheckFailed, Toast.LENGTH_LONG);
  }

  public void offerUpdate () {
    offerUpdate(false);
  }

  private void offerUpdate (boolean forcePrompt) {
    if (destroyed || release == null || state != State.AVAILABLE || updateOffered) return;
    int mode = forcePrompt ? Settings.AUTO_UPDATE_MODE_PROMPT : Settings.instance().getAutoUpdateMode();
    if (mode == Settings.AUTO_UPDATE_MODE_ALWAYS || mode == Settings.AUTO_UPDATE_MODE_WIFI_ONLY && isWifi()) {
      downloadUpdate(); return;
    }
    if (mode == Settings.AUTO_UPDATE_MODE_WIFI_ONLY && context.hasTdlib() && wifiTdlib == null) {
      wifiTdlib = context.currentTdlib(); wifiTdlib.listeners().subscribeToConnectivityUpdates(this);
    }
    if (mode != Settings.AUTO_UPDATE_MODE_PROMPT) return;
    ViewController<?> controller = context.navigation().getCurrentStackItem();
    if (controller == null || !controller.isFocused()) return;
    GithubReleasePolicy.Release offered = release;
    updateOffered = true;
    controller.showOptions(new ViewController.Options.Builder()
      .info(Lang.getString(R.string.RatogramUpdateAvailable, offered.version, Strings.buildSize(offered.size)))
      .item(new ViewController.OptionItem(R.id.btn_update, Lang.getString(R.string.DownloadUpdate), ViewController.OptionColor.NORMAL, R.drawable.baseline_system_update_24))
      .item(new ViewController.OptionItem(R.id.btn_sourceCode, Lang.getString(R.string.RatogramReleaseNotes), ViewController.OptionColor.NORMAL, R.drawable.baseline_github_24))
      .cancelItem().build(), (view, id) -> {
        if (id == R.id.btn_update && release == offered) downloadUpdate();
        else if (id == R.id.btn_sourceCode) UI.openUrl(offered.releaseUrl);
        return true;
      });
  }

  @SuppressWarnings("deprecation")
  private boolean isWifi () {
    ConnectivityManager manager = (ConnectivityManager) context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
    NetworkInfo info = manager != null ? manager.getActiveNetworkInfo() : null;
    return info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI;
  }

  public void downloadUpdate () {
    if (destroyed || state != State.AVAILABLE || release == null) return;
    stopWaitingForWifi();
    GithubReleasePolicy.Release target = release;
    bytesDownloaded = 0;
    setState(State.DOWNLOADING);
    client.newCall(new Request.Builder().url(target.url).build()).enqueue(new Callback() {
      @Override public void onFailure (Call call, IOException error) { downloadFailed(); }
      @Override public void onResponse (Call call, Response response) {
        String name = "ratogram-update-" + UUID.randomUUID();
        File partial = new File(context.getCacheDir(), name + ".part");
        File apk = new File(context.getCacheDir(), name + ".apk");
        try (response) {
          if (!response.isSuccessful() || response.body() == null) throw new IOException("APK request failed");
          MessageDigest digest = MessageDigest.getInstance("SHA-256");
          long total = 0, lastProgress = 0;
          try (InputStream input = response.body().byteStream(); FileOutputStream output = new FileOutputStream(partial)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = input.read(buffer)) != -1) {
              total += count;
              if (total > target.size) throw new IOException("Invalid APK size");
              output.write(buffer, 0, count); digest.update(buffer, 0, count);
              long now = SystemClock.uptimeMillis();
              if (now - lastProgress >= 250) { long current = total; UI.post(() -> progress(current, target.size)); lastProgress = now; }
            }
            output.getFD().sync();
          }
          if (total != target.size) throw new IOException("Incomplete APK");
          StringBuilder hash = new StringBuilder();
          for (byte b : digest.digest()) hash.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
          if (target.sha256 != null && !target.sha256.equals(hash.toString())) throw new IOException("APK checksum mismatch");
          verifyApk(partial);
          if (apk.exists() && !apk.delete() || !partial.renameTo(apk)) throw new IOException("Cannot store APK");
          UI.post(() -> {
            if (destroyed) { apk.delete(); return; }
            downloadedApk = apk; progress(target.size, target.size); setState(State.READY_TO_INSTALL);
          });
        } catch (Exception e) { partial.delete(); downloadFailed(); }
      }
    });
  }

  @SuppressWarnings("deprecation")
  private void verifyApk (File apk) throws Exception {
    PackageManager pm = context.getPackageManager();
    int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
    PackageInfo candidate = pm.getPackageArchiveInfo(apk.getPath(), flags);
    PackageInfo installed = pm.getPackageInfo(context.getPackageName(), flags);
    if (candidate == null || !context.getPackageName().equals(candidate.packageName) || candidate.versionCode <= installed.versionCode ||
      candidate.applicationInfo == null || Build.VERSION.SDK_INT >= 24 && candidate.applicationInfo.minSdkVersion > Build.VERSION.SDK_INT) throw new IOException("Incompatible APK");
    Signature[] expected = Build.VERSION.SDK_INT >= 28 && installed.signingInfo != null ? installed.signingInfo.getApkContentsSigners() : installed.signatures;
    Signature[] actual = Build.VERSION.SDK_INT >= 28 && candidate.signingInfo != null ? candidate.signingInfo.getApkContentsSigners() : candidate.signatures;
    if (expected == null || actual == null || expected.length == 0 || !Arrays.equals(expected, actual)) throw new IOException("APK signer mismatch");
  }

  private void downloadFailed () {
    UI.post(() -> { if (destroyed) return; bytesDownloaded = 0; setState(State.AVAILABLE); UI.showToast(R.string.RatogramUpdateDownloadFailed, Toast.LENGTH_LONG); });
  }
  private void progress (long bytes, long total) {
    if (destroyed) return;
    bytesDownloaded = bytes; totalBytesToDownload = total;
    for (Listener listener : listeners) listener.onAppUpdateDownloadProgress(bytes, total);
  }

  public void installUpdate () {
    if (destroyed || state != State.READY_TO_INSTALL || downloadedApk == null) return;
    File apk = downloadedApk;
    // Revalidate the private cache file before handing it to Android's package installer.
    client.dispatcher().executorService().execute(() -> {
      try {
        verifyApk(apk);
        UI.post(() -> { if (!destroyed) UI.openFile(new TdlibContext(context, context.hasTdlib() ? context.currentTdlib() : null),
          release.fileName, apk, "application/vnd.android.package-archive", 0); });
      } catch (Exception e) { UI.post(() -> { downloadedApk = null; setState(State.AVAILABLE); UI.showToast(R.string.RatogramUpdateDownloadFailed, Toast.LENGTH_LONG); }); }
    });
  }

  // Activity result compatibility; Ratogram never starts the Google Play updater.
  public void onGooglePlayFlowActivityResult (int resultCode, Intent data) { }

  private void stopWaitingForWifi () {
    if (wifiTdlib != null) { wifiTdlib.listeners().unsubscribeFromConnectivityUpdates(this); wifiTdlib = null; }
  }
  @Override public void onConnectionTypeChanged (org.drinkless.tdlib.TdApi.NetworkType type) {
    if (type instanceof org.drinkless.tdlib.TdApi.NetworkTypeWiFi) UI.post(() -> { stopWaitingForWifi(); offerUpdate(); });
  }
  public void destroy () { destroyed = true; stopWaitingForWifi(); client.dispatcher().cancelAll(); }
}
