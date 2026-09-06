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

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.DoubleTextView;

import java.util.ArrayList;
import java.util.List;

public class ForumTopicsController extends RecyclerViewController<Long> implements View.OnClickListener {
  private SettingsAdapter adapter;

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

    tdlib.send(new TdApi.GetForumTopics(getChatId(), "", 0, 0, 0, 100), (forumTopics, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
      }
      setTopics(forumTopics);
      executeScheduledAnimation();
    }));
  }

  private CharSequence buildTopicStatus (TdApi.ForumTopic topic) {
    List<String> parts = new ArrayList<>(3);
    if (topic.unreadCount > 0) {
      parts.add(Lang.plural(R.string.xNewMessages, topic.unreadCount));
    }
    if (topic.isPinned) {
      parts.add(Lang.getString(R.string.ForumTopicPinned));
    }
    if (topic.info.isClosed) {
      parts.add(Lang.getString(R.string.ForumTopicClosed));
    }
    return parts.isEmpty() ? Lang.getString(R.string.ForumTopic) : android.text.TextUtils.join(" · ", parts);
  }

  private void setTopics (TdApi.ForumTopics forumTopics) {
    List<ListItem> items = new ArrayList<>();
    if (forumTopics != null) {
      boolean first = true;
      for (TdApi.ForumTopic topic : forumTopics.topics) {
        if (topic.info == null || topic.info.isHidden) {
          continue;
        }
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
      }
    }
    if (items.isEmpty()) {
      items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ForumTopicsEmpty));
    }
    adapter.setItems(items, false);
  }

  @Override
  public void onClick (View v) {
    ListItem item = (ListItem) v.getTag();
    if (v.getId() != R.id.btn_forumTopic || item == null) {
      return;
    }
    TdApi.ForumTopic topic = (TdApi.ForumTopic) item.getData();
    TdlibUi.ChatOpenParameters parameters = new TdlibUi.ChatOpenParameters().keepStack();
    parameters.messageTopicId = new TdApi.MessageTopicForum(topic.info.forumTopicId);
    tdlib.ui().openChat(this, getChatId(), parameters);
  }
}
