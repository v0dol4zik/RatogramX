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
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.BetterChatView;

import java.util.ArrayList;
import java.util.List;

/** A single, discoverable entry point for the profile fields supported by TDLib. */
public class EditProfileController extends RecyclerViewController<Void> implements View.OnClickListener, TdlibCache.UserDataChangeListener {
  private SettingsAdapter adapter;
  private long myUserId;
  private TdApi.UserFullInfo userFull;

  public EditProfileController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.EditProfile);
  }

  @Override
  public int getId () {
    return R.id.controller_editProfile;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    myUserId = tdlib.myUserId(true);
    userFull = tdlib.myUserFull();

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setChatData (ListItem item, int position, BetterChatView chatView) {
        chatView.setChat((TGFoundChat) item.getData());
      }

      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_birthdate) {
          TdApi.Birthdate birthdate = userFull != null ? userFull.birthdate : null;
          view.setData(birthdate != null ? Lang.getBirthdate(birthdate, true, true) : Lang.getString(R.string.BirthdateEmpty));
        } else if (item.getId() == R.id.btn_profileChannel) {
          long personalChatId = userFull != null ? userFull.personalChatId : 0;
          view.setData(personalChatId != 0 ? tdlib.chatTitle(personalChatId) : Lang.getString(R.string.ProfileChannelNotSelected));
        }
      }
    };

    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));

    TdApi.User myUser = tdlib.myUser();
    if (myUser != null) {
      items.add(new ListItem(ListItem.TYPE_CHAT_BETTER, R.id.btn_editProfileName)
        .setData(new TGFoundChat(tdlib, myUser, null, true).setForceUsername()));
    }

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.OnYourProfile));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_birthdate, R.drawable.baseline_cake_variant_24, R.string.Birthdate));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_profileChannel, R.drawable.baseline_bullhorn_24, R.string.ProfileChannel));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ProfileMusicPlayerHint));

    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);

    if (myUserId != 0) {
      tdlib.cache().addUserDataListener(myUserId, this);
    }
  }

  @Override
  public void onFocus () {
    super.onFocus();
    TdApi.UserFullInfo latest = tdlib.myUserFull();
    if (latest != null) {
      userFull = latest;
    }
    if (adapter != null) {
      adapter.updateValuedSettingById(R.id.btn_birthdate);
      adapter.updateValuedSettingById(R.id.btn_profileChannel);
    }
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_editProfileName) {
      EditNameController controller = new EditNameController(context, tdlib);
      controller.setMode(EditNameController.Mode.RENAME_SELF);
      navigateTo(controller);
    } else if (v.getId() == R.id.btn_birthdate) {
      tdlib.ui().openBirthdateEditor(this, v, TdlibUi.BirthdateOpenOrigin.PROFILE);
    } else if (v.getId() == R.id.btn_profileChannel) {
      navigateTo(new ProfileChannelController(context, tdlib));
    }
  }

  @Override
  public void onUserUpdated (TdApi.User user) {
    if (user.id != myUserId) {
      return;
    }
    runOnUiThreadOptional(() -> {
      ListItem item = adapter != null ? adapter.findItemById(R.id.btn_editProfileName) : null;
      if (item != null) {
        item.setData(new TGFoundChat(tdlib, user, null, true).setForceUsername());
        adapter.notifyItemChanged(adapter.indexOfViewById(R.id.btn_editProfileName));
      }
    });
  }

  @Override
  public void onUserFullUpdated (long userId, TdApi.UserFullInfo userFull) {
    if (userId != myUserId) {
      return;
    }
    runOnUiThreadOptional(() -> {
      this.userFull = userFull;
      if (adapter != null) {
        adapter.updateValuedSettingById(R.id.btn_birthdate);
        adapter.updateValuedSettingById(R.id.btn_profileChannel);
      }
    });
  }

  @Override
  public void destroy () {
    super.destroy();
    if (myUserId != 0) {
      tdlib.cache().removeUserDataListener(myUserId, this);
    }
  }
}
