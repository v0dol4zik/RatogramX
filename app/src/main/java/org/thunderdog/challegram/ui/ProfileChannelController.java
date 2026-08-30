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
import android.view.View;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Picker for TDLib's suitable personal channels, including an explicit unset state. */
public class ProfileChannelController extends RecyclerViewController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;
  private long selectedChatId;

  public ProfileChannelController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.ProfileChannel);
  }

  @Override
  public int getId () {
    return R.id.controller_profileChannel;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    TdApi.UserFullInfo userFull = tdlib.myUserFull();
    selectedChatId = userFull != null ? userFull.personalChatId : 0;

    adapter = new SettingsAdapter(this);
    adapter.setItems(new ListItem[] {new ListItem(ListItem.TYPE_PROGRESS)}, false);
    recyclerView.setAdapter(adapter);

    tdlib.send(new TdApi.GetSuitablePersonalChats(), (suitableChats, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        navigateBack();
        return;
      }
      buildChannelList(suitableChats);
    }));
  }

  private void buildChannelList (TdApi.Chats suitableChats) {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ProfileChannelPickerHint));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_profileChannelNone, 0, R.string.ProfileChannelNotSelected, R.id.btn_profileChannelChoice, selectedChatId == 0));

    boolean selectedChatIncluded = selectedChatId == 0;
    if (suitableChats != null) {
      for (long chatId : suitableChats.chatIds) {
        TdApi.Chat chat = tdlib.chat(chatId);
        if (chat == null) {
          continue;
        }
        selectedChatIncluded |= chatId == selectedChatId;
        DoubleTextWrapper wrapper = new DoubleTextWrapper(tdlib, chat, false, false);
        int memberCount = tdlib.chatMemberCount(chatId);
        if (memberCount > 0) {
          wrapper.setForcedSubtitle(Lang.pluralBold(R.string.xSubscribers, memberCount));
        }
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_CHAT_SMALL_SELECTABLE, R.id.btn_profileChannelCandidate)
          .setData(wrapper)
          .setLongId(chatId)
          .setSelected(chatId == selectedChatId));
      }
    }

    if (!selectedChatIncluded) {
      TdApi.Chat selectedChat = tdlib.chat(selectedChatId);
      if (selectedChat != null) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_CHAT_SMALL_SELECTABLE, R.id.btn_profileChannelCandidate)
          .setData(new DoubleTextWrapper(tdlib, selectedChat, true, false))
          .setLongId(selectedChatId)
          .setSelected(true));
      }
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    if (items.size() == 5) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.NoChannels));
    }
    adapter.setItems(items, false);
  }

  @Override
  public void onClick (View v) {
    ListItem item = (ListItem) v.getTag();
    long chatId;
    if (v.getId() == R.id.btn_profileChannelNone) {
      chatId = 0;
    } else if (v.getId() == R.id.btn_profileChannelCandidate && item != null) {
      chatId = item.getLongId();
    } else {
      return;
    }

    if (chatId == selectedChatId) {
      navigateBack();
      return;
    }

    v.setEnabled(false);
    tdlib.send(new TdApi.SetPersonalChat(chatId), (ok, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        v.setEnabled(true);
        UI.showError(error);
      } else {
        selectedChatId = chatId;
        navigateBack();
      }
    }));
  }
}
