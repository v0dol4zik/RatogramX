package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.drinkless.tdlib.TdApi;
import org.json.JSONException;
import org.json.JSONObject;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.WebAppListener;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import java.util.Collections;
import java.util.Locale;

import me.vkryl.android.widget.FrameLayoutFix;
import moe.kirao.mgx.utils.WebAppOriginPolicy;

public class WebAppController extends WebkitController<WebAppController.Args> implements WebAppListener {
  public static final class Args {
    final long botUserId, chatId, launchId;
    final TdApi.WebAppUrl url;
    final @Nullable String buttonText;

    public Args (long botUserId, long chatId, TdApi.WebAppUrl url, long launchId, @Nullable String buttonText) {
      this.botUserId = botUserId;
      this.chatId = chatId;
      this.url = url;
      this.launchId = launchId;
      this.buttonText = buttonText;
    }
  }

  private static final String BRIDGE = "RatogramWebAppBridge";
  private static final String BRIDGE_SCRIPT =
    "if(window===window.top){window.TelegramWebviewProxy={postEvent:function(type,data){" +
    "window." + BRIDGE + ".postMessage(JSON.stringify({type:String(type),data:data||'{}'}));}};}";

  private @Nullable WebView appView;
  private @Nullable DoubleHeaderView appHeader;
  private @Nullable FrameLayoutFix appRoot;
  private @Nullable ScriptHandler bridgeScript;
  private @Nullable TextView mainButton;
  private @Nullable JSONObject mainButtonState;
  private String trustedOrigin;
  private boolean backButtonVisible, closingConfirmation, sendingData, sentMessage, launchClosed;
  private int headerColorId = ColorId.headerBackground;

  public WebAppController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override public int getId () {
    return R.id.controller_webApp;
  }

  @Override public long getChatId () {
    return getArguments() != null ? getArgumentsStrict().chatId : 0;
  }

  public static boolean isSupported () {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false;
    try {
      return WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  public static TdApi.ThemeParameters themeParameters () {
    return new TdApi.ThemeParameters(rgb(ColorId.filling), rgb(ColorId.background), rgb(ColorId.headerBackground),
      rgb(ColorId.filling), rgb(ColorId.filling), rgb(ColorId.separator), rgb(ColorId.text), rgb(ColorId.textLink),
      rgb(ColorId.textLink), rgb(ColorId.textLight), rgb(ColorId.textNegative), rgb(ColorId.textPlaceholder),
      rgb(ColorId.textLink), rgb(ColorId.fillingPositive), rgb(ColorId.fillingPositiveContent));
  }

  private static int rgb (int colorId) {
    return Theme.getColor(colorId) & 0xffffff;
  }

  @Override protected void onCreateWebView (DoubleHeaderView header, WebView webView) {
    Args args = getArgumentsStrict();
    trustedOrigin = WebAppOriginPolicy.origin(args.url.url);
    appView = webView;
    appHeader = header;
    header.setTitle(tdlib.cache().userName(args.botUserId));
    header.setSubtitle(Lang.getString(R.string.BotWebApp));
    webView.setBackgroundColor(Theme.getColor(ColorId.filling));
    WebSettings settings = webView.getSettings();
    settings.setAllowFileAccess(false);
    settings.setAllowContentAccess(false);
    settings.setGeolocationEnabled(false);
    settings.setSupportMultipleWindows(false);
    settings.setJavaScriptCanOpenWindowsAutomatically(false);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
      CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
    }
    webView.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading (WebView view, String url) {
        return processNavigation(url);
      }

      @Override public boolean shouldOverrideUrlLoading (WebView view, WebResourceRequest request) {
        if (!request.isForMainFrame()) return false;
        return processNavigation(request.getUrl().toString());
      }

      @Override public void onPageStarted (WebView view, String url, android.graphics.Bitmap favicon) {
        backButtonVisible = closingConfirmation = false;
        mainButtonState = null;
        updateMainButton();
        context.notifyBackPressAvailabilityChanged();
        String origin = WebAppOriginPolicy.origin(url);
        if (origin == null || (args.url.requireSameOrigin && !trustedOrigin.equals(origin))) {
          view.stopLoading();
          UI.showToast(R.string.BotWebAppInvalidUrl, Toast.LENGTH_SHORT);
        }
      }

      @Override public void onPageFinished (WebView view, String url) {
        sendTheme();
        sendViewport();
      }
    });
    WebViewCompat.addWebMessageListener(webView, BRIDGE, Collections.singleton(trustedOrigin),
      (view, message, sourceOrigin, isMainFrame, replyProxy) -> {
        if (!isDestroyed() && WebAppOriginPolicy.acceptsEvent(trustedOrigin, sourceOrigin.toString(), isMainFrame) && isTrustedPage()) {
          handleEvent(message.getData());
        }
      });
    bridgeScript = WebViewCompat.addDocumentStartJavaScript(webView, BRIDGE_SCRIPT, Collections.singleton(trustedOrigin));
    tdlib.listeners().addWebAppListener(this);
    webView.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) sendViewport();
    });
    webView.loadUrl(args.url.url);
  }

  @Override protected void onCreateWebContent (FrameLayoutFix contentView, WebView webView) {
    appRoot = contentView;
    mainButton = new TextView(context());
    mainButton.setGravity(Gravity.CENTER);
    mainButton.setTypeface(Fonts.getRobotoMedium());
    mainButton.setTextSize(16f);
    mainButton.setVisibility(View.GONE);
    mainButton.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48), Gravity.BOTTOM));
    mainButton.setOnClickListener(view -> sendEvent("main_button_pressed", new JSONObject()));
    contentView.addView(mainButton);
    updateMainButton();
  }

  @Override public View getViewForApplyingOffsets () {
    return appRoot != null ? appRoot : super.getViewForApplyingOffsets();
  }

  private boolean isTrustedPage () {
    return appView != null && WebAppOriginPolicy.acceptsEvent(trustedOrigin, appView.getUrl(), true);
  }

  private boolean processNavigation (String url) {
    String origin = WebAppOriginPolicy.origin(url);
    if (trustedOrigin.equals(origin)) return false;
    if (origin != null && !getArgumentsStrict().url.requireSameOrigin) return false;
    openLink(url, false);
    return true;
  }

  private void openLink (String url, boolean telegramOnly) {
    if (!canInteract()) return;
    Uri uri = Uri.parse(url);
    String scheme = uri.getScheme(), host = uri.getHost();
    boolean telegram = "tg".equalsIgnoreCase(scheme) ||
      ("https".equalsIgnoreCase(scheme) && ("t.me".equalsIgnoreCase(host) || "telegram.me".equalsIgnoreCase(host)));
    if (telegram) {
      tdlib.ui().openUrl(this, url, null);
    } else if (!telegramOnly && ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
      Intents.openInAppBrowser(context, uri, true);
    }
  }

  private void handleEvent (@Nullable String message) {
    if (message == null || message.length() > 32768) return;
    try {
      JSONObject event = new JSONObject(message);
      Object rawData = event.opt("data");
      JSONObject data = rawData instanceof JSONObject ? (JSONObject) rawData :
        rawData instanceof String && !((String) rawData).isEmpty() ? new JSONObject((String) rawData) : new JSONObject();
      switch (event.optString("type")) {
        case "web_app_ready":
          onPageProgress(1f);
          sendTheme();
          sendViewport();
          break;
        case "web_app_request_theme": sendTheme(); break;
        case "web_app_request_viewport":
        case "web_app_expand": sendViewport(); break;
        case "web_app_request_safe_area":
          sendEvent("safe_area_changed", emptyInsets());
          break;
        case "web_app_request_content_safe_area":
          sendEvent("content_safe_area_changed", emptyInsets());
          break;
        case "web_app_close": closeApp(); break;
        case "web_app_setup_closing_behavior":
          closingConfirmation = data.optBoolean("need_confirmation");
          break;
        case "web_app_open_link": openLink(data.optString("url"), false); break;
        case "web_app_open_tg_link": {
          String path = data.optString("path_full");
          if (path.startsWith("/") && !path.startsWith("//")) openLink("https://t.me" + path, true);
          break;
        }
        case "web_app_setup_back_button":
          backButtonVisible = data.optBoolean("is_visible");
          context.notifyBackPressAvailabilityChanged();
          break;
        case "web_app_setup_main_button":
          mainButtonState = data;
          updateMainButton();
          break;
        case "web_app_set_header_color": {
          String key = data.optString("color_key");
          if ("bg_color".equals(key) || "secondary_bg_color".equals(key)) {
            headerColorId = "bg_color".equals(key) ? ColorId.filling : ColorId.background;
            if (appHeader != null) appHeader.setTextColor(Theme.getColor(getHeaderTextColorId()));
            if (isFocused() && navigationController() != null) navigationController().getHeaderView().resetColors(this, null);
          }
          break;
        }
        case "web_app_data_send": sendData(data.optString("data")); break;
      }
    } catch (JSONException ignored) {
      // App payloads are untrusted. Never log them or the signed launch URL.
    }
  }

  private void sendData (String data) {
    Args args = getArgumentsStrict();
    if (sendingData || !canInteract() || args.buttonText == null || !WebAppOriginPolicy.validData(data)) return;
    sendingData = true;
    tdlib.send(new TdApi.SendWebAppData(args.botUserId, args.buttonText, data), (ok, error) -> runOnUiThreadOptional(() -> {
      sendingData = false;
      if (error != null) UI.showError(error);
      else closeApp();
    }));
  }

  private void updateMainButton () {
    if (mainButton == null || appView == null) return;
    JSONObject data = mainButtonState;
    boolean visible = data != null && data.optBoolean("is_visible") && !data.optString("text").trim().isEmpty();
    mainButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    if (visible) {
      mainButton.setText(data.optString("text"));
      mainButton.setEnabled(data.optBoolean("is_active", true) && !data.optBoolean("is_progress_visible"));
      mainButton.setAlpha(mainButton.isEnabled() ? 1f : .6f);
      mainButton.setBackgroundColor(appColor(data.optString("color"), Theme.getColor(ColorId.fillingPositive)));
      mainButton.setTextColor(appColor(data.optString("text_color"), Theme.getColor(ColorId.fillingPositiveContent)));
    }
    FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) appView.getLayoutParams();
    params.bottomMargin = visible ? Screen.dp(48) : 0;
    appView.setLayoutParams(params);
  }

  private boolean canInteract () {
    return isFocused() && context.getActivityState() == UI.State.RESUMED;
  }

  private static int appColor (String color, int fallback) {
    if (!color.matches("#[0-9a-fA-F]{6}")) return fallback;
    return Color.parseColor(color);
  }

  private static String hex (int color) {
    return String.format(Locale.ROOT, "#%06x", color & 0xffffff);
  }

  private void sendTheme () {
    if (appView == null) return;
    if (appHeader != null) appHeader.setTextColor(Theme.getColor(getHeaderTextColorId()));
    appView.setBackgroundColor(Theme.getColor(ColorId.filling));
    updateMainButton();
    TdApi.ThemeParameters theme = themeParameters();
    JSONObject colors = new JSONObject(), data = new JSONObject();
    try {
      colors.put("bg_color", hex(theme.backgroundColor));
      colors.put("secondary_bg_color", hex(theme.secondaryBackgroundColor));
      colors.put("header_bg_color", hex(theme.headerBackgroundColor));
      colors.put("bottom_bar_bg_color", hex(theme.bottomBarBackgroundColor));
      colors.put("section_bg_color", hex(theme.sectionBackgroundColor));
      colors.put("section_separator_color", hex(theme.sectionSeparatorColor));
      colors.put("text_color", hex(theme.textColor));
      colors.put("accent_text_color", hex(theme.accentTextColor));
      colors.put("section_header_text_color", hex(theme.sectionHeaderTextColor));
      colors.put("subtitle_text_color", hex(theme.subtitleTextColor));
      colors.put("destructive_text_color", hex(theme.destructiveTextColor));
      colors.put("hint_color", hex(theme.hintColor));
      colors.put("link_color", hex(theme.linkColor));
      colors.put("button_color", hex(theme.buttonColor));
      colors.put("button_text_color", hex(theme.buttonTextColor));
      data.put("theme_params", colors);
      sendEvent("theme_changed", data);
    } catch (JSONException ignored) { }
  }

  private static JSONObject emptyInsets () throws JSONException {
    return new JSONObject().put("top", 0).put("bottom", 0).put("left", 0).put("right", 0);
  }

  private void sendViewport () {
    if (appView == null || appView.getHeight() == 0) return;
    try {
      sendEvent("viewport_changed", new JSONObject().put("height", appView.getHeight() / Screen.density())
        .put("is_state_stable", true).put("is_expanded", true));
    } catch (JSONException ignored) { }
  }

  private void sendEvent (String type, JSONObject data) {
    if (isDestroyed() || !isTrustedPage()) return;
    appView.evaluateJavascript("if(window.Telegram&&Telegram.WebView){Telegram.WebView.receiveEvent(" +
      JSONObject.quote(type) + "," + data + ");}", null);
  }

  @Override protected int getHeaderColorId () {
    return headerColorId;
  }

  @Override protected int getHeaderTextColorId () {
    return headerColorId == ColorId.headerBackground ? ColorId.headerText : ColorId.text;
  }

  @Override protected int getHeaderIconColorId () {
    return headerColorId == ColorId.headerBackground ? ColorId.headerIcon : ColorId.icon;
  }

  @Override public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (backButtonVisible) {
      if (commit) sendEvent("back_button_pressed", new JSONObject());
      return true;
    }
    if (closingConfirmation) {
      if (commit) showConfirm(Lang.getString(R.string.BotWebAppCloseConfirm), Lang.getString(R.string.BotWebAppClose), this::closeApp);
      return true;
    }
    return super.performOnBackPressed(fromTop, commit);
  }

  @Override public boolean canSlideBackFrom (NavigationController navigation, float x, float y) {
    return !backButtonVisible && !closingConfirmation && x <= Screen.dp(15);
  }

  @Override public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    sendTheme();
  }

  @Override public void onThemeChanged (ThemeDelegate from, ThemeDelegate to) {
    super.onThemeChanged(from, to);
    sendTheme();
  }

  @Override public void onWebAppMessageSent (long launchId) {
    if (launchId != 0 && launchId == getArgumentsStrict().launchId) {
      runOnUiThreadOptional(() -> {
        sentMessage = true;
        if (isFocused()) closeApp();
      });
    }
  }

  @Override protected void onFocusStateChanged () {
    super.onFocusStateChanged();
    if (isFocused() && sentMessage) runOnUiThreadOptional(this::closeApp);
  }

  private void closeApp () {
    sentMessage = true;
    if (isFocused() && !isDestroyed()) navigateBack();
  }

  @Override public void destroy () {
    tdlib.listeners().removeWebAppListener(this);
    if (!launchClosed && getArguments() != null && getArgumentsStrict().launchId != 0) {
      launchClosed = true;
      tdlib.send(new TdApi.CloseWebApp(getArgumentsStrict().launchId), tdlib.typedOkHandler());
    }
    if (bridgeScript != null) {
      bridgeScript.remove();
      bridgeScript = null;
    }
    if (appView != null) {
      WebViewCompat.removeWebMessageListener(appView, BRIDGE);
      appView.stopLoading();
      appView = null;
    }
    super.destroy();
  }
}
