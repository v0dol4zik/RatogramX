/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.DoubleTextView;
import moe.kirao.mgx.utils.ForumPaginationPolicy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Displays a forum's topics without mixing them with the chat message stream. */
public class ForumTopicsController extends RecyclerViewController<Long> implements View.OnClickListener, ChatListener {
  private static final int PAGE_SIZE = 100;
  private static final int PREFETCH_DISTANCE = 6;
  private static final long REFRESH_DEBOUNCE_MS = 250L;

  private SettingsAdapter adapter;
  private CustomRecyclerView topicsRecyclerView;
  private final Map<Integer, TdApi.ForumTopic> topics = new LinkedHashMap<>();
  private int nextOffsetDate;
  private long nextOffsetMessageId;
  private int nextOffsetForumTopicId;
  private boolean hasMore;
  private boolean loading;
  private boolean hasLoadedInitialPage;
  private boolean refreshPending;
  private int requestGeneration;
  private long lastRefreshAt;

  private final Runnable refreshRunnable = () -> {
    if (isDestroyed()) return;
    refreshPending = false;
    if (loading) {
      refreshPending = true;
    } else {
      loadPage(true);
    }
  };

  public ForumTopicsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_forumTopics;
  }

  @Override
  public long getChatId () {
    return getArgumentsStrict();
  }

  @Override
  public CharSequence getName () {
    return tdlib.chatTitle(getChatId());
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return true;
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    super.saveInstanceState(outState, keyPrefix);
    outState.putLong(keyPrefix + "chatId", getChatId());
    return true;
  }

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    super.restoreInstanceState(in, keyPrefix);
    setArguments(in.getLong(keyPrefix + "chatId"));
    return true;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    topicsRecyclerView = recyclerView;
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setDoubleText (ListItem item, int position, DoubleTextView textView, boolean isUpdate) {
        TdApi.ForumTopic topic = (TdApi.ForumTopic) item.getData();
        textView.setText(topic.info.name, buildTopicStatus(topic));
        textView.setForumTopicIcon(tdlib, topic.info.icon);
      }
    };
    adapter.setItems(new ListItem[] {new ListItem(ListItem.TYPE_PROGRESS)}, false);
    recyclerView.setAdapter(adapter);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView view, int dx, int dy) {
        if (dy <= 0 || loading || !hasMore) return;
        RecyclerView.LayoutManager rawManager = view.getLayoutManager();
        if (!(rawManager instanceof LinearLayoutManager)) return;
        LinearLayoutManager manager = (LinearLayoutManager) rawManager;
        int last = manager.findLastVisibleItemPosition();
        if (last != RecyclerView.NO_POSITION && last >= adapter.getItemCount() - PREFETCH_DISTANCE) {
          loadPage(false);
        }
      }
    });
    tdlib.listeners().subscribeToChatUpdates(getChatId(), this);
    loadPage(true);
  }

  private CharSequence buildTopicStatus (@NonNull TdApi.ForumTopic topic) {
    StringBuilder status = new StringBuilder();
    if (topic.unreadCount > 0) {
      status.append(Lang.plural(R.string.xNewMessages, topic.unreadCount));
    }
    if (topic.isPinned) {
      appendStatus(status, Lang.getString(R.string.ForumTopicPinned));
    }
    if (topic.info.isClosed) {
      appendStatus(status, Lang.getString(R.string.ForumTopicClosed));
    }
    return status.length() == 0 ? Lang.getString(R.string.ForumTopic) : status.toString();
  }

  private static void appendStatus (@NonNull StringBuilder builder, @NonNull CharSequence value) {
    if (builder.length() > 0) builder.append(" · ");
    builder.append(value);
  }

  private void loadPage (boolean reset) {
    if (isDestroyed() || loading) return;
    if (!reset && !hasMore) return;
    if (reset) {
      lastRefreshAt = System.currentTimeMillis();
      requestGeneration++;
      topics.clear();
      nextOffsetDate = 0;
      nextOffsetMessageId = 0L;
      nextOffsetForumTopicId = 0;
      hasMore = false;
      hasLoadedInitialPage = false;
      setProgressItems();
    }
    final int generation = requestGeneration;
    final int offsetDate = nextOffsetDate;
    final long offsetMessageId = nextOffsetMessageId;
    final int offsetTopicId = nextOffsetForumTopicId;
    loading = true;
    tdlib.send(new TdApi.GetForumTopics(getChatId(), "", offsetDate, offsetMessageId,
      offsetTopicId, PAGE_SIZE), (forumTopics, error) -> runOnUiThreadOptional(() -> {
        if (generation != requestGeneration) return;
        loading = false;
        if (error != null || forumTopics == null) {
          if (!hasLoadedInitialPage) {
            if (error != null) UI.showError(error);
            topics.clear();
            hasMore = false;
            setTopics();
          }
        } else {
          int previousDate = nextOffsetDate;
          long previousMessageId = nextOffsetMessageId;
          int previousTopicId = nextOffsetForumTopicId;
          if (forumTopics.topics != null) {
            for (TdApi.ForumTopic topic : forumTopics.topics) {
              if (topic != null && topic.info != null && !topic.info.isHidden) {
                topics.put(topic.info.forumTopicId, topic);
              }
            }
          }
          nextOffsetDate = forumTopics.nextOffsetDate;
          nextOffsetMessageId = forumTopics.nextOffsetMessageId;
          nextOffsetForumTopicId = forumTopics.nextOffsetForumTopicId;
          hasMore = ForumPaginationPolicy.hasNextPage(forumTopics, previousDate, previousMessageId, previousTopicId);
          hasLoadedInitialPage = true;
          setTopics();
          executeScheduledAnimation();
        }
        if (refreshPending) {
          refreshPending = false;
          // Defer a follow-up refresh through the same debounce gate.  TDLib can
          // emit topic updates while GetForumTopics is completing; restarting
          // immediately here creates a request/update loop and visible flicker.
          scheduleRefresh();
        }
      }));
  }

  private void setProgressItems () {
    if (adapter != null) adapter.setItems(new ListItem[] {new ListItem(ListItem.TYPE_PROGRESS)}, false);
  }

  private void setTopics () {
    List<ListItem> items = new ArrayList<>(topics.size() * 2 + 3);
    boolean first = true;
    for (TdApi.ForumTopic topic : topics.values()) {
      if (topic.info == null || topic.info.isHidden) continue;
      if (first) {
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        first = false;
      } else {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      items.add(new ListItem(ListItem.TYPE_DOUBLE_TEXTVIEW, R.id.btn_forumTopic, 0, 0).setData(topic));
    }
    if (!first) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      if (loading) items.add(new ListItem(ListItem.TYPE_PROGRESS));
    } else if (!loading) {
      items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ForumTopicsEmpty));
    }
    adapter.setItems(items, false);
  }

  @Override
  public void onForumTopicInfoChanged (@NonNull TdApi.ForumTopicInfo info) {
    if (isDestroyed()) return;
    if (info.chatId != getChatId()) return;
    runOnUiThreadOptional(() -> {
      TdApi.ForumTopic topic = topics.get(info.forumTopicId);
      if (topic != null) {
        // TDLib sends this update for every topic during initial synchronisation.
        // Update the row in place instead of restarting the whole request/list,
        // which used to cause an endless spinner and visible flicker.
        topic.info = info;
        if (info.isHidden) {
          topics.remove(info.forumTopicId);
          setTopics();
        } else {
          updateTopicRow(info.forumTopicId);
        }
      } else if (hasLoadedInitialPage) {
        // A genuinely new topic is not in our snapshot; refresh once to discover it.
        scheduleRefresh();
      }
    });
  }

  @Override
  public void onForumTopicUpdated (long chatId, long messageThreadId, boolean isPinned,
                                   long lastReadInboxMessageId, long lastReadOutboxMessageId,
                                   @Nullable TdApi.ChatNotificationSettings notificationSettings) {
    if (isDestroyed()) return;
    if (chatId != getChatId()) return;
    runOnUiThreadOptional(() -> {
      TdApi.ForumTopic topic = topics.get((int) messageThreadId);
      if (topic != null) {
        topic.isPinned = isPinned;
        topic.lastReadInboxMessageId = lastReadInboxMessageId;
        topic.lastReadOutboxMessageId = lastReadOutboxMessageId;
        topic.notificationSettings = notificationSettings;
        updateTopicRow((int) messageThreadId);
      } else if (hasLoadedInitialPage) {
        scheduleRefresh();
      }
    });
  }

  private void updateTopicRow (int topicId) {
    if (adapter == null) return;
    for (int i = 0; i < adapter.getItemCount(); i++) {
      ListItem item = adapter.getItem(i);
      if (item != null && item.getData() instanceof TdApi.ForumTopic) {
        TdApi.ForumTopic topic = (TdApi.ForumTopic) item.getData();
        if (topic.info != null && topic.info.forumTopicId == topicId) {
          adapter.updateItem(i);
          return;
        }
      }
    }
  }

  private void scheduleRefresh () {
    if (isDestroyed()) return;
    if (loading) {
      refreshPending = true;
      return;
    }
    // Do not let a burst of TDLib updates repeatedly clear and rebuild the list.
    // One refresh per short interval is enough to discover newly-created topics.
    long now = System.currentTimeMillis();
    if (now - lastRefreshAt < REFRESH_DEBOUNCE_MS) return;
    removeCallbacks(refreshRunnable);
    runOnUiThread(refreshRunnable, REFRESH_DEBOUNCE_MS);
  }

  @Override
  public void onClick (View v) {
    ListItem item = (ListItem) v.getTag();
    if (v.getId() != R.id.btn_forumTopic || item == null) return;
    TdApi.ForumTopic topic = (TdApi.ForumTopic) item.getData();
    if (topic == null || topic.info == null) return;
    TdlibUi.ChatOpenParameters parameters = new TdlibUi.ChatOpenParameters().keepStack();
    parameters.messageTopicId = new TdApi.MessageTopicForum(topic.info.forumTopicId);
    tdlib.ui().openChat(this, getChatId(), parameters);
  }

  @Override
  public void destroy () {
    removeCallbacks(refreshRunnable);
    requestGeneration++;
    tdlib.listeners().unsubscribeFromChatUpdates(getChatId(), this);
    super.destroy();
  }
}
