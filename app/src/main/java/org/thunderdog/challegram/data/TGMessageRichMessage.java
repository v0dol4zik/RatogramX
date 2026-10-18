/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MediaPreview;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.InstantViewController;
import org.thunderdog.challegram.util.text.FormattedText;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextPart;
import org.thunderdog.challegram.util.text.TextWrapper;
import org.thunderdog.challegram.widget.ProgressComponent;

import moe.kirao.mgx.utils.RichMessageUtils;
import moe.kirao.mgx.utils.RichMessageActions;
import moe.kirao.mgx.utils.SavedRichMessageCodec;

public final class TGMessageRichMessage extends TGMessage implements TGInlineKeyboard.ClickListener {
  private TdApi.RichMessage richMessage;
  private TextWrapper text, thinkingText, mediaCountText;
  private MediaPreview mediaPreview;
  private TGInlineKeyboard openButton;
  private ProgressComponent thinkingProgress;
  private int contentWidth, contentHeight, mediaY, buttonY, thinkingY;
  private int loadGeneration;
  private boolean loading, fullRequestRunning;
  private boolean savedMarkdown, savedAttempted, savedReady;

  public TGMessageRichMessage (MessagesManager manager, TdApi.Message message, TdApi.MessageRichMessage content) {
    super(manager, message);
    richMessage = content.message;
  }

  public TGMessageRichMessage (MessagesManager manager, TdApi.Message message) {
    super(manager, message);
    savedMarkdown = true;
    String source = message.content instanceof TdApi.MessageText ? ((TdApi.MessageText) message.content).text.text : ((TdApi.MessageDocument) message.content).caption.text;
    richMessage = new TdApi.RichMessage(new TdApi.PageBlock[] {
      new TdApi.PageBlockPreformatted(new TdApi.RichTextPlain(SavedRichMessageCodec.excerpt(source, 512)), "markdown")
    }, SavedRichMessageCodec.isRtl(message.content), true);
  }

  public boolean isSavedMarkdown () { return savedMarkdown; }

  private void loadSaved (TdApi.Message message) {
    savedAttempted = true;
    loading = true;
    int generation = ++loadGeneration;
    tdlib.savedRichMessages().load(message, (document, error) -> {
      if (isDestroyed() || generation != loadGeneration) return;
      loading = false;
      savedReady = error == null;
      if (document != null) richMessage = document.output();
      refreshContent();
    });
  }

  @Override protected void buildContent (int maxWidth) {
    if (savedMarkdown && !savedAttempted) loadSaved(msg);
    if (text != null) text.performDestroy();
    if (thinkingText != null) thinkingText.performDestroy();
    if (mediaCountText != null) mediaCountText.performDestroy();
    TdApi.RichText preview = RichMessageUtils.previewRichText(richMessage);
    if (isLocalRichMessage()) preview = new TdApi.RichTexts(new TdApi.RichText[] {
      new TdApi.RichTextBold(new TdApi.RichTextPlain(Lang.getString(R.string.RichMessageLocal) + "\n")), preview
    });
    FormattedText formatted = FormattedText.parseRichText(controller(), preview,
      new TdlibUi.UrlOpenParameters().sourceMessage(this));
    boolean thinking = RichMessageUtils.isThinking(richMessage);
    if (formatted.isEmpty() && !thinking) formatted = new FormattedText(Lang.getString(R.string.RichMessageLabel), null);
    text = new TextWrapper(formatted.text, getTextStyleProvider(), getTextColorSet())
      .setEntities(formatted.entities, (wrapper, updated, media) -> invalidateTextMediaReceiver())
      .setMaxLines(10).setViewProvider(currentViews).setClickCallback(new Text.ClickCallback() {
        @Override public boolean allowLongPress (View view, Text text, TextPart part) { return canBeSaved(); }
        @Override public boolean onButtonClick (View view, TdApi.InlineButton button, @NonNull TdlibUi.UrlOpenParameters openParameters) {
          return RichMessageActions.onButtonClick(controller(), button, openParameters, canBeSaved());
        }
      });
    text.prepare(maxWidth);
    contentWidth = Math.min(maxWidth, Math.max(Screen.dp(220f), text.getWidth()));
    contentHeight = formatted.isEmpty() ? 0 : text.getHeight();

    thinkingText = null;
    if (!thinking && thinkingProgress != null) {
      thinkingProgress.setViewProvider(null);
      thinkingProgress.performDestroy();
      thinkingProgress = null;
    }
    if (thinking) {
      thinkingY = contentHeight + Screen.dp(contentHeight > 0 ? 6f : 0f);
      thinkingText = new TextWrapper(Lang.getString(R.string.RichMessageThinking), getTextStyleProvider(), getTextColorSet()).setViewProvider(currentViews);
      thinkingText.prepare(Math.max(1, contentWidth - Screen.dp(26f)));
      if (thinkingProgress == null) {
        thinkingProgress = new ProgressComponent(context(), Screen.dp(6f));
        thinkingProgress.setViewProvider(currentViews);
      }
      contentHeight = thinkingY + Math.max(Screen.dp(20f), thinkingText.getHeight());
    }

    mediaPreview = MediaPreview.valueOfRichMessage(tdlib, richMessage, Screen.dp(72f), Screen.dp(6f));
    mediaCountText = null;
    int mediaCount = RichMessageUtils.mediaCount(richMessage);
    if (mediaCount > 0) {
      mediaY = contentHeight + Screen.dp(8f);
      mediaCountText = new TextWrapper(Lang.getString(R.string.RichMessageMediaCount, mediaCount),
        getTextStyleProvider(), getTextColorSet()).setViewProvider(currentViews);
      mediaCountText.prepare(Math.max(1, contentWidth - (mediaPreview != null ? Screen.dp(84f) : 0)));
      contentHeight = mediaY + Math.max(mediaPreview != null ? Screen.dp(72f) : 0, mediaCountText.getHeight());
    }
    buttonY = contentHeight + Screen.dp(8f);
    if (openButton == null) {
      openButton = new TGInlineKeyboard(this, false);
      openButton.setViewProvider(currentViews);
    }
    openButton.setCustom(0, Lang.getString(loading ? R.string.LoadingMessage : R.string.RichMessageOpen), contentWidth, false, this);
    contentHeight = buttonY + TGInlineKeyboard.getButtonHeight();
  }

  @Override public boolean needComplexReceiver () { return mediaPreview != null; }
  @Override public boolean canBeSaved () {
    TdApi.Chat chat = tdlib.chat(getChatId());
    return super.canBeSaved() && (chat == null || !chat.hasProtectedContent);
  }
  @Override protected int getContentWidth () { return contentWidth; }
  @Override protected int getContentHeight () { return contentHeight; }

  @Override public void requestTextMedia (ComplexReceiver receiver) {
    if (text != null) text.requestMedia(receiver);
  }

  @Override public void requestMediaContent (ComplexReceiver receiver, boolean invalidate, int arg) {
    if (mediaPreview != null) mediaPreview.requestFiles(receiver, invalidate);
    else receiver.clear();
  }

  private void drawTextAndButton (MessageView view, Canvas canvas, int x, int y) {
    if (text != null) text.draw(canvas, x, x + contentWidth, 0, y, null, 1f, view.getTextMediaReceiver());
    if (thinkingText != null) {
      thinkingProgress.setBounds(x, y + thinkingY, x + Screen.dp(20f), y + thinkingY + Screen.dp(20f));
      thinkingProgress.draw(canvas);
      thinkingText.draw(canvas, x + Screen.dp(26f), x + contentWidth, 0, y + thinkingY, null, 1f);
    }
    if (mediaCountText != null) mediaCountText.draw(canvas, x + (mediaPreview != null ? Screen.dp(84f) : 0), x + contentWidth, 0, y + mediaY, null, 1f);
    openButton.draw(view, canvas, x, y + buttonY);
  }

  @Override protected void drawContent (MessageView view, Canvas canvas, int x, int y, int maxWidth) {
    drawTextAndButton(view, canvas, x, y);
  }

  @Override protected void drawContent (MessageView view, Canvas canvas, int x, int y, int maxWidth, ComplexReceiver receiver) {
    drawTextAndButton(view, canvas, x, y);
    if (mediaPreview != null) mediaPreview.draw(view, canvas, receiver, x, y + mediaY, 1f);
  }

  @Override public boolean onTouchEvent (MessageView view, MotionEvent event) {
    return (openButton != null && openButton.onTouchEvent(view, event)) ||
      (text != null && text.onTouchEvent(view, event)) || super.onTouchEvent(view, event);
  }

  @Override public boolean performLongPress (View view, float x, float y) {
    if (savedMarkdown) return messagesController().showSavedRichMessageOptions(this);
    if (isLocalRichMessage()) return messagesController().showLocalRichMessageOptions(this);
    return (canBeSaved() && text != null && text.performLongPress(view)) || super.performLongPress(view, x, y);
  }

  @Override public boolean onMessageClick (MessageView view, org.thunderdog.challegram.ui.MessagesController controller) {
    if (savedMarkdown) return controller.showSavedRichMessageOptions(this);
    return isLocalRichMessage() ? controller.showLocalRichMessageOptions(this) : super.onMessageClick(view, controller);
  }

  @Override public void onClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    if (loading || isDestroyed() || !controller().isFocused()) return;
    if (savedMarkdown) {
      if (savedReady) openFull(richMessage);
      else { loadSaved(msg); refreshContent(); }
      return;
    }
    if (richMessage.isFull) { openFull(richMessage); return; }
    loading = true;
    refreshContent();
    requestFull();
  }

  private void requestFull () {
    if (!loading || fullRequestRunning || isDestroyed()) return;
    if (richMessage.isFull) {
      loading = false;
      refreshContent();
      if (controller().isFocused()) openFull(richMessage);
      return;
    }
    fullRequestRunning = true;
    int generation = loadGeneration;
    long messageId = getId();
    tdlib.send(new TdApi.GetFullRichMessage(getChatId(), messageId), (result, error) -> UI.post(() -> {
      if (isDestroyed() || messageId != getId()) return;
      fullRequestRunning = false;
      if (!loading) return;
      if (generation != loadGeneration) { requestFull(); return; }
      loading = false;
      if (error != null) UI.showError(error);
      else richMessage = result;
      refreshContent();
      if (error == null && controller().isFocused()) openFull(result);
    }));
  }

  private void refreshContent () {
    rebuildContent();
    invalidateContentReceiver();
    invalidateTextMediaReceiver();
    invalidate();
  }

  private void openFull (TdApi.RichMessage richMessage) {
    InstantViewController viewer = new InstantViewController(context(), tdlib);
    viewer.setArguments(InstantViewController.Args.forRichMessage(msg, richMessage, canBeForwarded()));
    controller().navigateTo(viewer);
  }

  @Override public boolean onLongClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) { return false; }

  @Override protected boolean onMessageContentChanged (TdApi.Message message, TdApi.MessageContent oldContent, TdApi.MessageContent newContent, boolean bottom) {
    return updateMessageContent(message, newContent, bottom);
  }

  @Override protected boolean updateMessageContent (TdApi.Message message, TdApi.MessageContent newContent, boolean bottom) {
    if (savedMarkdown) {
      TdApi.Message updated = tgx.td.Td.copyOf(message);
      updated.content = newContent;
      savedReady = false;
      loadSaved(updated);
      refreshContent();
      return true;
    }
    if (!(newContent instanceof TdApi.MessageRichMessage)) return false;
    richMessage = ((TdApi.MessageRichMessage) newContent).message;
    loadGeneration++;
    refreshContent();
    if (loading && richMessage.isFull) {
      loading = false;
      refreshContent();
      if (controller().isFocused()) openFull(richMessage);
    } else requestFull();
    return true;
  }

  @Override protected void onMessageContainerDestroyed () {
    loadGeneration++;
    if (text != null) text.performDestroy();
    if (thinkingText != null) thinkingText.performDestroy();
    if (mediaCountText != null) mediaCountText.performDestroy();
    if (thinkingProgress != null) {
      thinkingProgress.setViewProvider(null);
      thinkingProgress.performDestroy();
    }
  }
}
