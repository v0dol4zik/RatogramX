package moe.kirao.mgx.ui;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessageText;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Text;

import me.vkryl.android.widget.FrameLayoutFix;
import moe.kirao.mgx.utils.QuoteSelectionPolicy;
import tgx.td.Td;

/** A temporary overlay which owns selection gestures, without changing message gestures. */
public final class MessageTextSelection extends FrameLayoutFix {
  public interface Callback {
    void onQuote (int start, int end);
    void onClosed (MessageTextSelection selection);
  }

  private final MessageView target;
  private final TGMessageText message;
  private final Text text;
  private final TdApi.FormattedText source;
  private final Callback callback;
  private final LinearLayout toolbar;
  private final RectF startEdge = new RectF(), endEdge = new RectF(), toolbarBounds = new RectF();
  private final int[] targetLocation = new int[2], overlayLocation = new int[2];
  private int start, end, dragging;
  private int wordStart, wordEnd;
  private float offsetX, offsetY, dragOffsetX, dragOffsetY;
  private boolean closed, pendingClose;

  public MessageTextSelection (MessageView target, TGMessageText message, Text text, TdApi.FormattedText source, Callback callback) {
    super(target.getContext());
    this.target = target;
    this.message = message;
    this.text = text;
    this.source = source;
    this.callback = callback;
    setWillNotDraw(false);
    int[] word = QuoteSelectionPolicy.wordRange(source.text, target.getTouchedTextOffset(text));
    int[] range = text.expandSelectionRange(word[0], word[1]);
    start = range[0];
    end = range[1];

    toolbar = new LinearLayout(getContext());
    toolbar.setOrientation(LinearLayout.HORIZONTAL);
    toolbar.setGravity(Gravity.CENTER_VERTICAL);
    toolbar.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(320), Screen.dp(48)));
    if (message.canBeSaved()) addAction(R.string.Copy, this::copy);
    addAction(R.string.TextFormatQuote, () -> {
      int selectedStart = start, selectedEnd = end;
      close();
      callback.onQuote(selectedStart, selectedEnd);
    });
    addAction(R.string.SelectAll, () -> {
      start = 0;
      end = source.text.length();
      updateSelection();
    });
    ImageView cancel = new ImageView(getContext());
    cancel.setImageResource(R.drawable.baseline_close_24);
    cancel.setScaleType(ImageView.ScaleType.CENTER);
    cancel.setContentDescription(Lang.getString(R.string.Cancel));
    cancel.setOnClickListener(view -> close());
    RippleSupport.setTransparentSelector(cancel);
    toolbar.addView(cancel, new LinearLayout.LayoutParams(Screen.dp(40), ViewGroup.LayoutParams.MATCH_PARENT));
    addView(toolbar);
    target.setTextSelection(this);
    target.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
  }

  private void addAction (int label, Runnable action) {
    TextView view = new TextView(getContext());
    view.setText(Lang.getString(label));
    view.setContentDescription(Lang.getString(label));
    view.setTextSize(13f);
    view.setTypeface(Fonts.getRobotoMedium());
    view.setGravity(Gravity.CENTER);
    view.setMaxLines(1);
    view.setEllipsize(TextUtils.TruncateAt.END);
    view.setPadding(Screen.dp(6), 0, Screen.dp(6), 0);
    view.setOnClickListener(ignored -> {
      if (isValid()) action.run();
      else close();
    });
    RippleSupport.setTransparentSelector(view);
    toolbar.addView(view, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
  }

  private boolean isValid () {
    return !closed && Settings.instance().isPartialQuotesExperimentEnabled() && target.getMessage() == message &&
      message.getSelectableText() == text && source.text.equals(text.getText());
  }

  public void drawHighlight (Canvas c, @Nullable Text rendered, int startX, int endX, int endXBottomPadding, int startY) {
    if (rendered == text && isValid()) text.drawSelection(c, start, end, startX, endX, endXBottomPadding, startY);
  }

  private boolean updateGeometry () {
    if (!isValid() || !text.getSelectionEdge(start, false, startEdge) || !text.getSelectionEdge(end, true, endEdge)) {
      if (!pendingClose) {
        pendingClose = true;
        post(this::close);
      }
      return false;
    }
    target.getLocationInWindow(targetLocation);
    getLocationInWindow(overlayLocation);
    offsetX = targetLocation[0] - overlayLocation[0];
    offsetY = targetLocation[1] - overlayLocation[1];
    startEdge.offset(offsetX, offsetY);
    endEdge.offset(offsetX, offsetY);
    return true;
  }

  @Override protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    toolbar.getLayoutParams().width = Math.min(Screen.dp(320), Math.max(0, View.MeasureSpec.getSize(widthMeasureSpec) - Screen.dp(16)));
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  @Override protected void dispatchDraw (Canvas c) {
    if (!updateGeometry()) return;
    float width = toolbar.getMeasuredWidth(), height = toolbar.getMeasuredHeight();
    float x = Math.max(Screen.dp(8), Math.min((startEdge.left + endEdge.left - width) / 2f, getWidth() - width - Screen.dp(8)));
    float y = Math.max(Screen.dp(8), Math.min(startEdge.top - height - Screen.dp(12), getHeight() - height - Screen.dp(8)));
    if (startEdge.top < height + Screen.dp(20)) {
      y = Math.min(Math.max(endEdge.bottom + Screen.dp(24), Screen.dp(8)), getHeight() - height - Screen.dp(8));
    }
    toolbar.setTranslationX(x);
    toolbar.setTranslationY(y);
    toolbarBounds.set(x, y, x + width, y + height);
    c.drawRoundRect(toolbarBounds, Screen.dp(6), Screen.dp(6), Paints.fillingPaint(Theme.fillingColor()));
    c.drawRoundRect(toolbarBounds, Screen.dp(6), Screen.dp(6), Paints.strokeSmallPaint(Theme.getColor(ColorId.separator)));
    for (int i = 0; i < toolbar.getChildCount(); i++) {
      View child = toolbar.getChildAt(i);
      if (child instanceof TextView) ((TextView) child).setTextColor(Theme.textAccentColor());
      else if (child instanceof ImageView) ((ImageView) child).setColorFilter(Theme.getColor(ColorId.icon));
    }
    if (dragging == 0) super.dispatchDraw(c);
    drawHandle(c, startEdge);
    drawHandle(c, endEdge);
  }

  private void drawHandle (Canvas c, RectF edge) {
    int color = Theme.getColor(ColorId.textLink);
    c.drawRect(edge.left - Screen.dp(1), edge.top, edge.left + Screen.dp(1), edge.bottom + Screen.dp(7), Paints.fillingPaint(color));
    c.drawCircle(edge.left, edge.bottom + Screen.dp(7), Screen.dp(6), Paints.fillingPaint(color));
  }

  private boolean hitsHandle (RectF edge, float x, float y) {
    return Math.abs(x - edge.left) <= Screen.dp(24) && Math.abs(y - edge.bottom - Screen.dp(7)) <= Screen.dp(24);
  }

  private int hitHandle (float x, float y) {
    boolean hitsStart = hitsHandle(startEdge, x, y), hitsEnd = hitsHandle(endEdge, x, y);
    if (!hitsStart) return hitsEnd ? 2 : 0;
    if (!hitsEnd) return 1;
    double startDistance = Math.pow(x - startEdge.left, 2) + Math.pow(y - startEdge.bottom - Screen.dp(7), 2);
    double endDistance = Math.pow(x - endEdge.left, 2) + Math.pow(y - endEdge.bottom - Screen.dp(7), 2);
    return startDistance <= endDistance ? 1 : 2;
  }

  @Override public boolean onInterceptTouchEvent (MotionEvent event) {
    return dragging != 0 || (event.getActionMasked() == MotionEvent.ACTION_DOWN && updateGeometry() &&
      (hitsHandle(startEdge, event.getX(), event.getY()) || hitsHandle(endEdge, event.getX(), event.getY())));
  }

  @Override public boolean onTouchEvent (MotionEvent event) {
    if (!updateGeometry()) return true;
    float x = event.getX(), y = event.getY();
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        int handle = hitHandle(x, y);
        if (handle != 0) {
          dragging = handle;
          RectF edge = dragging == 1 ? startEdge : endEdge;
          dragOffsetX = x - edge.left;
          dragOffsetY = y - edge.centerY();
        } else {
          int offset = text.getSelectionOffset(x - offsetX, y - offsetY, false, true);
          if (offset < 0) {
            close();
            return true;
          }
          int[] word = QuoteSelectionPolicy.wordRange(source.text, offset);
          int[] range = text.expandSelectionRange(word[0], word[1]);
          start = wordStart = range[0];
          end = wordEnd = range[1];
          dragging = 3;
          dragOffsetX = dragOffsetY = 0f;
        }
        getParent().requestDisallowInterceptTouchEvent(true);
        updateSelection();
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (dragging == 0) return true;
        int offset = text.getSelectionOffset(x - offsetX - dragOffsetX, y - offsetY - dragOffsetY, true, dragging == 3);
        if (offset < 0) return true;
        if (dragging == 1) {
          start = QuoteSelectionPolicy.boundary(source.text, Math.min(offset, end - 1), false);
        } else if (dragging == 2) {
          end = QuoteSelectionPolicy.boundary(source.text, Math.max(offset, start + 1), true);
        } else {
          int[] word = QuoteSelectionPolicy.wordRange(source.text, offset);
          start = Math.min(wordStart, word[0]);
          end = Math.max(wordEnd, word[1]);
        }
        int[] range = text.expandSelectionRange(start, end);
        start = range[0];
        end = range[1];
        updateSelection();
        return true;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        dragging = 0;
        getParent().requestDisallowInterceptTouchEvent(false);
        invalidate();
        return true;
      default:
        return true;
    }
  }

  private void updateSelection () {
    target.invalidate();
    invalidate();
  }

  private void copy () {
    if (isValid() && message.canBeSaved()) UI.copyText(TD.toCopyText(Td.substring(source, start, end)), R.string.CopiedText);
    close();
  }

  public void close () {
    if (closed) return;
    closed = true;
    target.setTextSelection(null);
    if (getParent() instanceof ViewGroup) {
      getParent().requestDisallowInterceptTouchEvent(false);
      ((ViewGroup) getParent()).removeView(this);
    }
    callback.onClosed(this);
  }
}
