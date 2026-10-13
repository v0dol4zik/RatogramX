package moe.kirao.mgx.utils;

import android.widget.Toast;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.WebAppController;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import me.vkryl.core.StringUtils;
import tgx.td.ChatId;

public final class BotWebAppLauncher {
  private static final Set<ViewController<?>> pendingLaunches = Collections.newSetFromMap(new WeakHashMap<>());
  private BotWebAppLauncher () { }

  public static void open (ViewController<?> owner, long botUserId, String url,
                           @Nullable TdApi.MessageTopic topicId, @Nullable TdApi.InputMessageReplyTo replyTo) {
    final long chatId = owner.getChatId();
    owner.tdlib().send(new TdApi.GetInternalLinkType(url), (link, error) -> owner.runOnUiThreadOptional(() -> {
      if (!owner.isFocused() || owner.getChatId() != chatId) return;
      if (link instanceof TdApi.InternalLinkTypeWebApp || link instanceof TdApi.InternalLinkTypeMainWebApp) {
        owner.tdlib().ui().openUrl(owner, url, null);
      } else {
        prepare(owner, botUserId, () -> request(owner, botUserId,
          new TdApi.OpenWebApp(chatId, botUserId, url, topicId, replyTo, parameters(null)), null));
      }
    }));
  }

  public static void openKeyboard (ViewController<?> owner, long botUserId, String url, @Nullable String buttonText) {
    prepare(owner, botUserId, () -> request(owner, botUserId,
      new TdApi.GetWebAppUrl(botUserId, url, parameters(null)), buttonText));
  }

  public static void openLink (ViewController<?> owner, String botUsername, @Nullable String shortName,
                               String startParameter, @Nullable TdApi.WebAppOpenMode mode) {
    final long chatId = owner.getChatId();
    owner.tdlib().send(new TdApi.SearchPublicChat(botUsername), (chat, error) -> owner.runOnUiThreadOptional(() -> {
      if (!owner.isFocused() || owner.getChatId() != chatId) return;
      if (error != null) {
        UI.showError(error);
      } else if (!owner.tdlib().isBotChat(chat)) {
        UI.showToast(Lang.getString(R.string.BotNotFound, botUsername), Toast.LENGTH_SHORT);
      } else {
        long botUserId = owner.tdlib().chatUserId(chat);
        prepare(owner, botUserId, true, () -> {
          if (shortName == null) {
            request(owner, botUserId, new TdApi.GetMainWebApp(chatId, botUserId, startParameter, parameters(mode)), null);
          } else {
            owner.tdlib().send(new TdApi.SearchWebApp(botUserId, shortName), (found, appError) -> owner.runOnUiThreadOptional(() -> {
              if (appError != null) {
                pendingLaunches.remove(owner);
                UI.showError(appError);
              } else {
                // Write access is a separate permission; merely opening an app never grants it.
                request(owner, botUserId, new TdApi.GetWebAppLinkUrl(chatId, botUserId, shortName, startParameter, false, parameters(mode)), null);
              }
            }));
          }
        });
      }
    }));
  }

  private static TdApi.WebAppOpenParameters parameters (@Nullable TdApi.WebAppOpenMode mode) {
    return new TdApi.WebAppOpenParameters(WebAppController.themeParameters(), "ratogramx", mode);
  }

  private static void prepare (ViewController<?> owner, long botUserId, Runnable launch) {
    prepare(owner, botUserId, false, launch);
  }

  private static void prepare (ViewController<?> owner, long botUserId, boolean confirmLink, Runnable launch) {
    if (owner.isDestroyed() || !owner.isFocused() || pendingLaunches.contains(owner)) return;
    if (ChatId.isSecret(owner.getChatId())) {
      UI.showToast(R.string.BotWebAppSecretChat, Toast.LENGTH_SHORT);
      return;
    }
    if (!WebAppController.isSupported()) {
      UI.showToast(R.string.BotWebAppUnsupported, Toast.LENGTH_LONG);
      return;
    }
    pendingLaunches.add(owner);
    final long sourceChatId = owner.getChatId();
    final Tdlib tdlib = owner.tdlib();
    tdlib.send(new TdApi.GetUser(botUserId), (bot, error) -> owner.runOnUiThreadOptional(() -> {
      if (!owner.isFocused() || owner.getChatId() != sourceChatId) {
        pendingLaunches.remove(owner);
      } else if (error != null) {
        pendingLaunches.remove(owner);
        UI.showError(error);
      } else if (bot.type.getConstructor() != TdApi.UserTypeBot.CONSTRUCTOR) {
        pendingLaunches.remove(owner);
        UI.showToast(Lang.getString(R.string.BotNotFound, Long.toString(botUserId)), Toast.LENGTH_SHORT);
      } else if (bot.restrictionInfo != null && !StringUtils.isEmpty(bot.restrictionInfo.restrictionReason)) {
        pendingLaunches.remove(owner);
        owner.showConfirm(bot.restrictionInfo.restrictionReason, Lang.getString(R.string.OK), () -> { });
      } else if (!confirmLink && tdlib.settings().hasWebAppConsent(botUserId)) {
        launch.run();
      } else {
        final boolean[] confirmed = {false};
        PopupLayout popup = owner.showConfirm(Lang.getString(R.string.BotWebAppConsent, tdlib.cache().userName(botUserId)), Lang.getString(R.string.BotWebAppOpen), () -> {
          if (!owner.isDestroyed() && owner.isFocused() && owner.getChatId() == sourceChatId) {
            confirmed[0] = true;
            tdlib.settings().setWebAppConsent(botUserId);
            launch.run();
          }
        });
        if (popup == null) {
          pendingLaunches.remove(owner);
        } else {
          popup.setDismissListener(ignored -> {
            if (!confirmed[0]) pendingLaunches.remove(owner);
          });
        }
      }
    }));
  }

  private static void request (ViewController<?> owner, long botUserId, TdApi.Function<?> request, @Nullable String buttonText) {
    final long sourceChatId = owner.getChatId();
    owner.tdlib().client().send(request, result -> {
      final long launchId;
      final TdApi.WebAppUrl url;
      if (result instanceof TdApi.WebAppInfo) {
        launchId = ((TdApi.WebAppInfo) result).launchId;
        url = ((TdApi.WebAppInfo) result).url;
      } else if (result instanceof TdApi.MainWebApp) {
        launchId = 0;
        url = ((TdApi.MainWebApp) result).url;
      } else if (result instanceof TdApi.WebAppUrl) {
        launchId = 0;
        url = (TdApi.WebAppUrl) result;
      } else {
        owner.tdlib().ui().post(() -> {
          pendingLaunches.remove(owner);
          if (!owner.isDestroyed()) UI.showError(result);
        });
        return;
      }
      owner.tdlib().ui().post(() -> {
        pendingLaunches.remove(owner);
        if (owner.isDestroyed() || !owner.isFocused() || owner.getChatId() != sourceChatId) {
          closeLaunch(owner.tdlib(), launchId);
          return;
        }
        if (url == null || WebAppOriginPolicy.origin(url.url) == null) {
          closeLaunch(owner.tdlib(), launchId);
          UI.showToast(R.string.BotWebAppInvalidUrl, Toast.LENGTH_LONG);
          return;
        }
        WebAppController controller = new WebAppController(owner.context(), owner.tdlib());
        controller.setArguments(new WebAppController.Args(botUserId, sourceChatId, url, launchId, buttonText));
        if (owner instanceof MessagesController) {
          ((MessagesController) owner).hideAllKeyboards();
        }
        owner.navigateTo(controller);
      });
    });
  }

  private static void closeLaunch (Tdlib tdlib, long launchId) {
    if (launchId != 0) tdlib.send(new TdApi.CloseWebApp(launchId), tdlib.typedOkHandler());
  }
}
