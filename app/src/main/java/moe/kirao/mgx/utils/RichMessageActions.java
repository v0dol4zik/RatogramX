/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package moe.kirao.mgx.utils;

import android.widget.Toast;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.UI;

public final class RichMessageActions {
  private RichMessageActions () { }

  public static boolean onButtonClick (ViewController<?> controller, TdApi.InlineButton button, TdlibUi.UrlOpenParameters openParameters, boolean canCopy) {
    if (controller.isDestroyed() || !controller.isFocused()) return true;
    if (button.type != null) {
      switch (button.type.getConstructor()) {
        case TdApi.InlineKeyboardButtonTypeUrl.CONSTRUCTOR:
          controller.tdlib().ui().openUrl(controller, ((TdApi.InlineKeyboardButtonTypeUrl) button.type).url,
            new TdlibUi.UrlOpenParameters(openParameters).requireOpenPrompt(true));
          return true;
        case TdApi.InlineKeyboardButtonTypeUser.CONSTRUCTOR:
          controller.tdlib().ui().openPrivateProfile(controller, ((TdApi.InlineKeyboardButtonTypeUser) button.type).userId, openParameters);
          return true;
        case TdApi.InlineKeyboardButtonTypeCopyText.CONSTRUCTOR:
          if (canCopy) UI.copyText(((TdApi.InlineKeyboardButtonTypeCopyText) button.type).text, R.string.CopiedText);
          else UI.showToast(R.string.ChatContentProtected, Toast.LENGTH_SHORT);
          return true;
        case TdApi.InlineKeyboardButtonTypeDisabled.CONSTRUCTOR:
          return true;
      }
    }
    UI.showToast(R.string.RichMessageButtonUnsupported, Toast.LENGTH_SHORT);
    return true;
  }
}
