package moe.kirao.mgx.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Toast;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ActivityResultHandler;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.ui.TextController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

import moe.kirao.mgx.MoexConfig;
import moe.kirao.mgx.utils.MessageHistoryStore;

public class SettingsMoexController extends RecyclerViewController<SettingsMoexController.Args> implements View.OnClickListener, View.OnLongClickListener, ActivityResultHandler, org.thunderdog.challegram.util.UserPickerMultiDelegate, MoexConfig.SettingsChangeListener {
  private static final int REQUEST_EXPORT_SETTINGS = 0x4d01;
  private static final int REQUEST_IMPORT_SETTINGS = 0x4d02;
  public SettingsMoexController (Context context, Tdlib tdlib) {
    super(context, tdlib);
    MoexConfig.instance().addSettingsListener(this);
  }

  @Override public int getId () {
    return R.id.controller_moexSettings;
  }

  public static final int CATEGORY_GENERAL = 1;
  public static final int CATEGORY_INTERFACE = 2;
  public static final int CATEGORY_CHATS = 3;
  public static final int CATEGORY_MISC = 4;
  public static final int CATEGORY_EXPERIMENTAL = 5;

  private int category;

  public static class Args {
    private final int category;

    public Args (int category) {
      this.category = category;
    }
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.category = args.category;
  }

  @Override public CharSequence getName () {
    return category == CATEGORY_GENERAL
      ? Lang.getString(R.string.GeneralMoexSettings) : category == CATEGORY_CHATS
      ? Lang.getString(R.string.Chats) : category == CATEGORY_INTERFACE
      ? Lang.getString(R.string.InterfaceMoexSettings) : category == CATEGORY_MISC
      ? Lang.getString(R.string.Other) : category == CATEGORY_EXPERIMENTAL
      ? Lang.getString(R.string.ExperimentalOptions) : Lang.getString(R.string.MoexSettings);
  }

  private SettingsAdapter adapter;

  @Override public void onClick (View v) {
    int viewId = v.getId();
    SettingsMoexController c = new SettingsMoexController(context, tdlib);

    if (viewId == R.id.btn_GeneralMoexSettings) {
      c.setArguments(new SettingsMoexController.Args(SettingsMoexController.CATEGORY_GENERAL));
      navigateTo(c);
    } else if (viewId == R.id.btn_InterfaceMoexSettings) {
      c.setArguments(new SettingsMoexController.Args(SettingsMoexController.CATEGORY_INTERFACE));
      navigateTo(c);
    } else if (viewId == R.id.btn_ChatsMoexSettings) {
      c.setArguments(new SettingsMoexController.Args(SettingsMoexController.CATEGORY_CHATS));
      navigateTo(c);
    } else if (viewId == R.id.btn_MiscMoexSettings) {
      c.setArguments(new SettingsMoexController.Args(SettingsMoexController.CATEGORY_MISC));
      navigateTo(c);
    } else if (viewId == R.id.btn_exportSettings) {
      Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
      intent.setType("application/json");
      intent.putExtra(Intent.EXTRA_TITLE, "ratogramx-settings.json");
      context().putActivityResultHandler(REQUEST_EXPORT_SETTINGS, this);
      context().startActivityForResult(intent, REQUEST_EXPORT_SETTINGS);
    } else if (viewId == R.id.btn_importSettings) {
      Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
      intent.setType("application/json");
      intent.addCategory(Intent.CATEGORY_OPENABLE);
      context().putActivityResultHandler(REQUEST_IMPORT_SETTINGS, this);
      context().startActivityForResult(intent, REQUEST_IMPORT_SETTINGS);
    } else if (viewId == R.id.btn_sendCrashReport) {
      Log.getLogFiles(result -> UI.post(() -> {
        File latestCrash = null;
        if (result != null) {
          for (File file : result.files) {
            if (file.getName().startsWith(Log.CRASH_PREFIX)) {
              latestCrash = file;
              break;
            }
          }
        }
        if (latestCrash == null) {
          UI.showToast(R.string.NoCrashReport, Toast.LENGTH_SHORT);
        } else if (!isDestroyed()) {
          TextController controller = new TextController(context, tdlib);
          controller.setArguments(TextController.Arguments.fromFile(Lang.getString(R.string.SendCrashReport), latestCrash.getPath(), "text/plain"));
          navigateTo(controller);
        }
      }));
    } else if (viewId == R.id.btn_ExperimentalMoexSettings) {
      c.setArguments(new SettingsMoexController.Args(SettingsMoexController.CATEGORY_EXPERIMENTAL));
      navigateTo(c);
    } else if (viewId == R.id.btn_moexCrowdinLink) {
      tdlib.ui().openUrl(this, Lang.getString(R.string.MoexCrowdinLink), new TdlibUi.UrlOpenParameters());
    } else if (viewId == R.id.btn_moexChatLink) {
      tdlib.ui().openUrl(this, Lang.getString(R.string.MoexChatLink), new TdlibUi.UrlOpenParameters().forceInstantView());
    } else if (viewId == R.id.btn_moexChannelLink) {
      tdlib.ui().openUrl(this, Lang.getString(R.string.MoexChannelLink), new TdlibUi.UrlOpenParameters().forceInstantView());
    } else if (viewId == R.id.btn_moexSourceLink) {
      tdlib.ui().openUrl(this, Lang.getString(R.string.MoexSourceLink), new TdlibUi.UrlOpenParameters());
    } else if (viewId == R.id.btn_build) {
      UI.showToast(R.string.cuteToast, Toast.LENGTH_SHORT);
    } else if (viewId == R.id.btn_hidePhone) {
      MoexConfig.instance().toggleHidePhoneNumber();
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_hideProfileMusic) {
      MoexConfig.instance().toggleHideProfileMusic();
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_hideProfileBirthdate) {
      MoexConfig.instance().toggleHideProfileBirthdate();
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_hideProfileChannel) {
      MoexConfig.instance().toggleHideProfileChannel();
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_hideSponsoredMessages) {
      MoexConfig.instance().toggleHideSponsoredMessages();
      adapter.updateValuedSettingById(viewId);
      UI.showToast(R.string.SponsoredMessagesSettingApplied, Toast.LENGTH_SHORT);
    } else if (viewId == R.id.btn_channelBottomButton) {
      showChannelBottomButtonOptions();
    } else if (viewId == R.id.btn_showSearchTabs) {
      MoexConfig.instance().toggleShowSearchTabs();
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_enableFeaturesButton) {
      MoexConfig.instance().toggleEnableFeaturesButton();
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_showIdProfile) {
      Settings.instance().setExperimentEnabled(Settings.EXPERIMENT_FLAG_SHOW_PEER_IDS, adapter.toggleView(v));
    } else if (viewId == R.id.btn_experimentalAllow) {
      boolean hadLocalPremium = Settings.instance().isLocalPremiumExperimentEnabled();
      Settings.instance().setExperimentEnabled(Settings.EXPERIMENT_FLAG_ALLOW_EXPERIMENTS, adapter.toggleView(v));
      adapter.updateValuedSettingById(viewId);
      adapter.updateValuedSettingById(R.id.btn_experimentalForums);
      adapter.updateValuedSettingById(R.id.btn_experimentalQuotes);
      adapter.updateValuedSettingById(R.id.btn_experimentalLocalPremium);
      adapter.updateValuedSettingById(R.id.btn_experimentalMessageHistory);
      adapter.updateValuedSettingById(R.id.btn_showIdProfile);
      updateGhostModeSettings();
      if (hadLocalPremium != Settings.instance().isLocalPremiumExperimentEnabled()) {
        scheduleActivityRestart();
      }
    } else if (viewId == R.id.btn_experimentalForums) {
      Settings.instance().setForumTopicsExperimentEnabled(adapter.toggleView(v));
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_experimentalQuotes) {
      Settings.instance().setPartialQuotesExperimentEnabled(adapter.toggleView(v));
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_experimentalLocalPremium) {
      Settings.instance().setLocalPremiumExperimentEnabled(adapter.toggleView(v));
      adapter.updateValuedSettingById(viewId);
      // Premium-dependent controllers cache their capability state; rebuild them immediately.
      scheduleActivityRestart();
    } else if (viewId == R.id.btn_experimentalMessageHistory) {
      Settings.instance().setMessageHistoryExperimentEnabled(adapter.toggleView(v));
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_messageHistoryView) {
      // Reading the history database can involve thousands of entries. Keep it
      // off the UI thread and only navigate after the snapshot is ready.
      MessageHistoryStore.instance().dumpAsync(dump -> {
        if (isDestroyed()) return;
        TextController controller = new TextController(context, tdlib);
        controller.setArguments(TextController.Arguments.fromRawText(
          Lang.getString(R.string.MessageEditHistory), dump, "text/plain"));
        navigateTo(controller);
      });
    } else if (viewId == R.id.btn_ghostMode) {
      MoexConfig.instance().toggleGhostMode();
      updateGhostModeSettings();
      UI.showToast(MoexConfig.ghostMode ? R.string.GhostModeEnabledToast : R.string.GhostModeDisabledToast, Toast.LENGTH_SHORT);
    } else if (viewId == R.id.btn_ghostOnlineExceptions) {
      org.thunderdog.challegram.ui.ContactsController picker = new org.thunderdog.challegram.ui.ContactsController(context, tdlib);
      picker.setArguments(new org.thunderdog.challegram.ui.ContactsController.Args(this).useGlobalSearch(
        org.thunderdog.challegram.component.dialogs.SearchManager.FLAG_NEED_TOP_CHATS |
        org.thunderdog.challegram.component.dialogs.SearchManager.FLAG_NEED_GLOBAL_SEARCH |
        org.thunderdog.challegram.component.dialogs.SearchManager.FLAG_ONLY_USERS |
        org.thunderdog.challegram.component.dialogs.SearchManager.FLAG_NO_BOTS |
        org.thunderdog.challegram.component.dialogs.SearchManager.FLAG_NO_SELF |
        org.thunderdog.challegram.component.dialogs.SearchManager.FLAG_NO_SECRET));
      navigateTo(picker);
    } else if (viewId == R.id.btn_ghostOnlineRetry) {
      tdlib.refreshOnlineStatus();
    } else if (viewId == R.id.btn_ghostNoRead) {
      MoexConfig.instance().toggleGhostModeFlag(MoexConfig.GHOST_NO_READ);
      updateGhostModeSettings();
    } else if (viewId == R.id.btn_ghostNoTyping) {
      MoexConfig.instance().toggleGhostModeFlag(MoexConfig.GHOST_NO_TYPING);
      updateGhostModeSettings();
    } else if (viewId == R.id.btn_ghostOffline) {
      MoexConfig.instance().toggleGhostModeFlag(MoexConfig.GHOST_OFFLINE);
      updateGhostModeSettings();
    } else if (viewId == R.id.btn_folderTabsStyle) {
      showFolderTabsStyleOptions();
    } else if (viewId == R.id.btn_secureWindow) {
      MoexConfig.instance().toggleSecureWindow();
      adapter.updateValuedSettingById(viewId);
      context().checkDisallowScreenshots();
    } else if (viewId == R.id.btn_secureOnlySecretChats) {
      MoexConfig.instance().toggleSecureOnlySecretChats();
      adapter.updateValuedSettingById(viewId);
      context().checkDisallowScreenshots();
    } else if (viewId == R.id.btn_confirmCalls) {
      MoexConfig.instance().toggleConfirmCalls();
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_confirmVoice) {
      MoexConfig.instance().toggleConfirmVoice();
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_hideMessagesBadge) {
      MoexConfig.instance().toggleHideMessagesBadge();
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_changeSizeLimit) {
      showChangeSizeLimit();
    } else if (viewId == R.id.btn_squareAvatar) {
      MoexConfig.instance().toggleSquareAvatar();
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_blurDrawer) {
      MoexConfig.instance().toggleBlurDrawer();
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_drawerItems) {
      showDrawerItemsOptions();
    } else if (viewId == R.id.btn_autoPauseMedia) {
      showAutoPauseTypeOptions();
    } else if (viewId == R.id.btn_headerText) {
      showHeaderTextOptions();
    } else if (viewId == R.id.btn_disableReactions) {
      MoexConfig.instance().toggleDisableReactions();
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_hideMessagePanelButtons) {
      showHideMessagePanelOptions();
    } else if (viewId == R.id.btn_hideBottomBar) {
      MoexConfig.instance().toggleHideBottomBar();
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_disableStickerTimestamp) {
      MoexConfig.instance().toggleDisableStickerTimestamp();
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_roundedStickers) {
      MoexConfig.instance().toggleRoundedStickers();
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_IncreaseRecents) {
      MoexConfig.instance().toggleIncreaseRecents();
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_rememberOptions) {
      MoexConfig.instance().toggleRememberSendOptions();
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_typingInstead) {
      MoexConfig.instance().toggleTypingInsteadChoosing();
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_darkenDrawer) {
      MoexConfig.instance().toggleDarkenDrawer();
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_silent) {
      MoexConfig.instance().toggleSilentMessage();
      adapter.updateValuedSettingById(viewId);
    } else if (viewId == R.id.btn_rearRounds) {
      showRoundVideoCameraOptions();
    } else if (viewId == R.id.btn_roundVideoQuality) {
      VideoQualityUi.showRoundOptions(this, () -> adapter.updateValuedSettingById(R.id.btn_roundVideoQuality));
    } else if (viewId == R.id.btn_videoBitrate) {
      VideoQualityUi.showBitrateOptions(this, () -> adapter.updateValuedSettingById(R.id.btn_videoBitrate));
    } else if (viewId == R.id.btn_toggleEdgeAnimSide) {
      handleSettingClick(v, adapter);
    }
  }

  private void updateGhostModeSettings () {
    if (adapter == null) return;
    adapter.updateValuedSettingById(R.id.btn_ghostMode);
    adapter.updateValuedSettingById(R.id.btn_ghostNoRead);
    adapter.updateValuedSettingById(R.id.btn_ghostNoTyping);
    adapter.updateValuedSettingById(R.id.btn_ghostOffline);
    adapter.updateValuedSettingById(R.id.btn_ghostOnlineExceptions);
    adapter.updateValuedSettingById(R.id.btn_ghostOnlineRetry);
  }

  @Override public long[] getAlreadySelectedChatIds () {
    long[] ids = tdlib.ghostOnline().exceptions();
    for (int i = 0; i < ids.length; i++) ids[i] = tgx.td.ChatId.fromUserId(ids[i]);
    return ids;
  }

  @Override public int provideMultiUserPickerHint () { return R.string.GhostOnlineExceptions; }

  @Override public void onAlreadyPickedChatsChanged (java.util.List<org.thunderdog.challegram.component.user.BubbleView.Entry> entries) {
    java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
    for (org.thunderdog.challegram.component.user.BubbleView.Entry entry : entries) {
      if (entry.senderId instanceof org.drinkless.tdlib.TdApi.MessageSenderUser) ids.add(((org.drinkless.tdlib.TdApi.MessageSenderUser) entry.senderId).userId);
    }
    long[] selected = new long[ids.size()];
    for (int i = 0; i < selected.length; i++) selected[i] = ids.get(i);
    tdlib.ghostOnline().setExceptions(selected);
    updateGhostModeSettings();
  }

  @Override public void onSettingsChanged (String key, Object value, Object old) {
    if (moe.kirao.mgx.utils.GhostOnlineManager.KEY_CHANGED.equals(key) || MoexConfig.KEY_GHOST_MODE.equals(key) || MoexConfig.KEY_GHOST_MODE_FLAGS.equals(key) || "experiments".equals(key)) runOnUiThreadOptional(this::updateGhostModeSettings);
  }

  @Override public void destroy () {
    MoexConfig.instance().removeSettingsListener(this);
    super.destroy();
  }

  @Override
  public boolean onLongClick (View v) {
    if (v.getId() == R.id.btn_build) {
      UI.copyText(String.join("\n", Lang.getStringSecure(R.string.MoexVer), U.getUsefulMetadata(tdlib)), R.string.CopiedText);
    }
    return false;
  }

  private void showChangeSizeLimit () {
    int sizeLimitOption = MoexConfig.instance().getSizeLimit();
    showSettings(new SettingsWrapBuilder(R.id.btn_changeSizeLimit).setRawItems(new ListItem[] {
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_sizeLimit800, 0, R.string.px800, R.id.btn_changeSizeLimit, sizeLimitOption == MoexConfig.SIZE_LIMIT_800),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_sizeLimit1280, 0, R.string.px1280, R.id.btn_changeSizeLimit, sizeLimitOption == MoexConfig.SIZE_LIMIT_1280),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_sizeLimit2560, 0, R.string.px2560, R.id.btn_changeSizeLimit, sizeLimitOption == MoexConfig.SIZE_LIMIT_2560),
    }).setAllowResize(false).addHeaderItem(Lang.getMarkdownString(this, R.string.SizeLimitDesc)).setIntDelegate((id, result) -> {
      int sizeLimit = result.get(R.id.btn_changeSizeLimit);
      int sizeOption;
      if (sizeLimit == R.id.btn_sizeLimit800) {
        sizeOption = MoexConfig.SIZE_LIMIT_800;
      } else if (sizeLimit == R.id.btn_sizeLimit1280) {
        sizeOption = MoexConfig.SIZE_LIMIT_1280;
      } else {
        sizeOption = MoexConfig.SIZE_LIMIT_2560;
      }

      MoexConfig.instance().setSizeLimit(sizeOption);
      adapter.updateValuedSettingById(R.id.btn_changeSizeLimit);
    }));
  }

  private void showHideMessagePanelOptions () {
    showSettings(new SettingsWrapBuilder(R.id.btn_hideMessagePanelButtons).addHeaderItem(
      new ListItem(ListItem.TYPE_INFO, R.id.text_title, 0, R.string.HideCameraButtonInfo, false)).setRawItems(
      new ListItem[] {
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_disableCameraButton, 0, R.string.DisableCameraButton, MoexConfig.disableCameraButton),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_disableCommandsButton, 0, R.string.DisableCommandsButton, MoexConfig.disableCommandsButton),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_disableRecordButton, 0, R.string.DisableRecordButton, MoexConfig.disableRecordButton),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_disableSendAsButton, 0, R.string.DisableSendAsButton, MoexConfig.disableSendAsButton)
      }).setIntDelegate((id, result) -> {
      if (MoexConfig.disableCameraButton == (result.get(R.id.btn_disableCameraButton) == 0)) {
        MoexConfig.instance().toggleDisableCameraButton();
      }
      if (MoexConfig.disableCommandsButton == (result.get(R.id.btn_disableCommandsButton) == 0)) {
        MoexConfig.instance().toggleDisableCommandsButton();
      }
      if (MoexConfig.disableRecordButton == (result.get(R.id.btn_disableRecordButton) == 0)) {
        MoexConfig.instance().toggleDisableRecordButton();
      }
      if (MoexConfig.disableSendAsButton == (result.get(R.id.btn_disableSendAsButton) == 0)) {
        MoexConfig.instance().toggleDisableSendAsButton();
      }

      adapter.updateValuedSettingById(R.id.btn_hideMessagePanelButtons);
    }));
  }

  private void showDrawerItemsOptions () {
    int items = MoexConfig.instance().getDrawerItems();
    showSettings(new SettingsWrapBuilder(R.id.btn_drawerItems).setRawItems(new ListItem[] {
      new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_contacts, 0, R.string.Contacts, R.id.btn_contacts, (items & MoexConfig.DRAWER_ITEM_CONTACTS) != 0),
      new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_archive, 0, R.string.OpenArchive, R.id.btn_archive, (items & MoexConfig.DRAWER_ITEM_ARCHIVE) != 0),
      new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_calls, 0, R.string.Calls, R.id.btn_calls, (items & MoexConfig.DRAWER_ITEM_CALLS) != 0),
      new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_savedMessages, 0, R.string.SavedMessages, R.id.btn_savedMessages, (items & MoexConfig.DRAWER_ITEM_SAVED_MESSAGES) != 0),
      new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_help, 0, R.string.Help, R.id.btn_help, (items & MoexConfig.DRAWER_ITEM_HELP) != 0),
      new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_night, 0, R.string.NightMode, R.id.btn_night, (items & MoexConfig.DRAWER_ITEM_NIGHT_MODE) != 0),
      new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_proxy, 0, R.string.Proxy, R.id.btn_proxy, (items & MoexConfig.DRAWER_ITEM_PROXY) != 0),
      new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_ghostMode, 0, R.string.GhostMode, R.id.btn_ghostMode, (items & MoexConfig.DRAWER_ITEM_GHOST) != 0)
    }).setIntDelegate((id, result) -> {
      int newItems = 0;
      if (result.get(R.id.btn_contacts) == R.id.btn_contacts) newItems |= MoexConfig.DRAWER_ITEM_CONTACTS;
      if (result.get(R.id.btn_archive) == R.id.btn_archive) newItems |= MoexConfig.DRAWER_ITEM_ARCHIVE;
      if (result.get(R.id.btn_calls) == R.id.btn_calls) newItems |= MoexConfig.DRAWER_ITEM_CALLS;
      if (result.get(R.id.btn_savedMessages) == R.id.btn_savedMessages) newItems |= MoexConfig.DRAWER_ITEM_SAVED_MESSAGES;
      if (result.get(R.id.btn_help) == R.id.btn_help) newItems |= MoexConfig.DRAWER_ITEM_HELP;
      if (result.get(R.id.btn_night) == R.id.btn_night) newItems |= MoexConfig.DRAWER_ITEM_NIGHT_MODE;
      if (result.get(R.id.btn_proxy) == R.id.btn_proxy) newItems |= MoexConfig.DRAWER_ITEM_PROXY;
      if (result.get(R.id.btn_ghostMode) == R.id.btn_ghostMode) newItems |= MoexConfig.DRAWER_ITEM_GHOST;
      MoexConfig.instance().setDrawerItems(newItems);
      adapter.updateValuedSettingById(R.id.btn_drawerItems);
    }));
  }

  private void showHeaderTextOptions () {
    int headerTextOption = MoexConfig.instance().getHeaderText();
    showSettings(new SettingsWrapBuilder(R.id.btn_headerText).setRawItems(new ListItem[] {
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_headerTextChats, 0, R.string.Chats, R.id.btn_headerText, headerTextOption == MoexConfig.HEADER_TEXT_CHATS),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_headerTextMoex, 0, R.string.moexHeaderClient, R.id.btn_headerText, headerTextOption == MoexConfig.HEADER_TEXT_MOEX),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_headerTextUsername, 0, R.string.Username, R.id.btn_headerText, headerTextOption == MoexConfig.HEADER_TEXT_USERNAME),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_headerTextName, 0, R.string.login_FirstName, R.id.btn_headerText, headerTextOption == MoexConfig.HEADER_TEXT_NAME),
    }).setIntDelegate((id, result) -> {
      int headerText = result.get(R.id.btn_headerText);
      int defaultOption;
      if (headerText == R.id.btn_headerTextChats) {
        defaultOption = MoexConfig.HEADER_TEXT_CHATS;
      } else if (headerText == R.id.btn_headerTextName) {
        defaultOption = MoexConfig.HEADER_TEXT_NAME;
      } else if (headerText == R.id.btn_headerTextUsername) {
        defaultOption = MoexConfig.HEADER_TEXT_USERNAME;
      } else {
        defaultOption = MoexConfig.HEADER_TEXT_MOEX;
      }

      MoexConfig.instance().setHeaderText(defaultOption);
      adapter.updateValuedSettingById(R.id.btn_headerText);
    }));
  }

  private void showAutoPauseTypeOptions () {
    int flags = MoexConfig.instance().getAutoPauseMediaTypes();
    showSettings(new SettingsWrapBuilder(R.id.btn_autoPauseMedia).setRawItems(new ListItem[] {
      new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_autoPauseResume, 0, R.string.AutoPauseMediaResume, R.id.btn_autoPauseResume, MoexConfig.autoPauseResumeSystemPlayback),
      new ListItem(ListItem.TYPE_SHADOW_BOTTOM).setTextColorId(ColorId.background),
      new ListItem(ListItem.TYPE_SHADOW_TOP).setTextColorId(ColorId.background),
      new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.autoPauseTypeVoice, 0, R.string.VoiceMessages, R.id.autoPauseTypeVoice, (flags & MoexConfig.AUTO_PAUSE_MEDIA_VOICE) != 0),
      new ListItem(ListItem.TYPE_SEPARATOR_FULL),
      new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.autoPauseTypeRound, 0, R.string.VideoMessages, R.id.autoPauseTypeRound, (flags & MoexConfig.AUTO_PAUSE_MEDIA_ROUND) != 0),
      new ListItem(ListItem.TYPE_SEPARATOR_FULL),
      new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.autoPauseTypeVideo, 0, R.string.Videos, R.id.autoPauseTypeVideo, (flags & MoexConfig.AUTO_PAUSE_MEDIA_VIDEO) != 0),
    }).setNeedSeparators(false).setIntDelegate((id, result) -> {
      int newFlags = 0;
      if (result.get(R.id.autoPauseTypeVoice) == R.id.autoPauseTypeVoice) newFlags |= MoexConfig.AUTO_PAUSE_MEDIA_VOICE;
      if (result.get(R.id.autoPauseTypeRound) == R.id.autoPauseTypeRound) newFlags |= MoexConfig.AUTO_PAUSE_MEDIA_ROUND;
      if (result.get(R.id.autoPauseTypeVideo) == R.id.autoPauseTypeVideo) newFlags |= MoexConfig.AUTO_PAUSE_MEDIA_VIDEO;
      MoexConfig.instance().setAutoPauseMediaTypes(newFlags);
      MoexConfig.instance().setAutoPauseResumeSystemPlayback(result.get(R.id.btn_autoPauseResume) == R.id.btn_autoPauseResume);
      adapter.updateValuedSettingById(R.id.btn_autoPauseMedia);
    }));
  }

  private void showRoundVideoCameraOptions () {
    int selected = MoexConfig.instance().getRoundVideos();
    showSettings(new SettingsWrapBuilder(R.id.btn_rearRounds).setRawItems(new ListItem[] {
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.frontRoundVideo, 0, R.string.frontCamera, R.id.btn_rearRounds, selected == MoexConfig.START_WITH_FRONT),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.rearRoundVideo, 0, R.string.RearCamera, R.id.btn_rearRounds, selected == MoexConfig.START_WITH_REAR),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.askRoundVideo, 0, R.string.Ask, R.id.btn_rearRounds, selected == MoexConfig.START_WITH_ASK),
    }).setIntDelegate((id, result) -> {
      int newMode = selected;
      final int resultId = result.get(R.id.btn_rearRounds);
      if (resultId == R.id.frontRoundVideo) {
        newMode = MoexConfig.START_WITH_FRONT;
      } else if (resultId == R.id.rearRoundVideo) {
        newMode = MoexConfig.START_WITH_REAR;
      } else if (resultId == R.id.askRoundVideo) {
        newMode = MoexConfig.START_WITH_ASK;
      }
      MoexConfig.instance().setRoundVideos(newMode);
      adapter.updateValuedSettingById(R.id.btn_rearRounds);
    }));
  }

  private void showChannelBottomButtonOptions () {
    int selected = MoexConfig.instance().getChannelBottomButton();
    showSettings(new SettingsWrapBuilder(R.id.btn_channelBottomButton).setRawItems(new ListItem[] {
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.channelBottomButtonAuto, 0, R.string.ChannelBottomButtonAuto, R.id.btn_channelBottomButton, selected == MoexConfig.CHANNEL_BOTTOM_BUTTON_AUTO),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.channelBottomButtonDiscuss, 0, R.string.ChannelBottomButtonDiscuss, R.id.btn_channelBottomButton, selected == MoexConfig.CHANNEL_BOTTOM_BUTTON_DISCUSS),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.channelBottomButtonMute, 0, R.string.ChannelBottomButtonMute, R.id.btn_channelBottomButton, selected == MoexConfig.CHANNEL_BOTTOM_BUTTON_MUTE),
    }).setAllowResize(false).addHeaderItem(Lang.getMarkdownString(this, R.string.ChannelBottomButtonInfo)).setIntDelegate((id, result) -> {
      int resultId = result.get(R.id.btn_channelBottomButton);
      int mode = resultId == R.id.channelBottomButtonDiscuss ? MoexConfig.CHANNEL_BOTTOM_BUTTON_DISCUSS
        : resultId == R.id.channelBottomButtonMute ? MoexConfig.CHANNEL_BOTTOM_BUTTON_MUTE
        : MoexConfig.CHANNEL_BOTTOM_BUTTON_AUTO;
      MoexConfig.instance().setChannelBottomButton(mode);
      adapter.updateValuedSettingById(R.id.btn_channelBottomButton);
    }));
  }

  private void showFolderTabsStyleOptions () {
    int selected = MoexConfig.instance().getFolderTabsStyle();
    showSettings(new SettingsWrapBuilder(R.id.btn_folderTabsStyle).setRawItems(new ListItem[] {
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.folderTabsStyleUpstream, 0, R.string.FolderTabsStyleUpstream, R.id.btn_folderTabsStyle, selected == MoexConfig.FOLDER_TABS_STYLE_UPSTREAM),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.folderTabsStyleText, 0, R.string.FolderTabsStyleText, R.id.btn_folderTabsStyle, selected == MoexConfig.FOLDER_TABS_STYLE_TEXT),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.folderTabsStyleIcons, 0, R.string.FolderTabsStyleIcons, R.id.btn_folderTabsStyle, selected == MoexConfig.FOLDER_TABS_STYLE_ICONS),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.folderTabsStyleTextIcons, 0, R.string.FolderTabsStyleTextIcons, R.id.btn_folderTabsStyle, selected == MoexConfig.FOLDER_TABS_STYLE_TEXT_ICONS)
    }).setAllowResize(false).setIntDelegate((id, result) -> {
      int resultId = result.get(R.id.btn_folderTabsStyle);
      int style = resultId == R.id.folderTabsStyleText ? MoexConfig.FOLDER_TABS_STYLE_TEXT
        : resultId == R.id.folderTabsStyleIcons ? MoexConfig.FOLDER_TABS_STYLE_ICONS
        : resultId == R.id.folderTabsStyleTextIcons ? MoexConfig.FOLDER_TABS_STYLE_TEXT_ICONS
        : MoexConfig.FOLDER_TABS_STYLE_UPSTREAM;
      MoexConfig.instance().setFolderTabsStyle(style);
      adapter.updateValuedSettingById(R.id.btn_folderTabsStyle);
    }));
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        view.setDrawModifier(item.getDrawModifier());
        int itemId = item.getId();

        if (itemId == R.id.btn_moexCrowdinLink) {
          view.setData(R.string.MoexCrowdinText);
        } else if (itemId == R.id.btn_moexChatLink) {
          view.setData(R.string.moexChat);
        } else if (itemId == R.id.btn_moexChannelLink) {
          view.setData(R.string.moexChannel);
        } else if (itemId == R.id.btn_moexSourceLink) {
          view.setData(R.string.moexGithub);
        } else if (itemId == R.id.btn_hidePhone) {
          view.getToggler().setRadioEnabled(MoexConfig.hidePhoneNumber, isUpdate);
        } else if (itemId == R.id.btn_hideProfileMusic) {
          view.getToggler().setRadioEnabled(MoexConfig.hideProfileMusic, isUpdate);
        } else if (itemId == R.id.btn_hideProfileBirthdate) {
          view.getToggler().setRadioEnabled(MoexConfig.hideProfileBirthdate, isUpdate);
        } else if (itemId == R.id.btn_hideProfileChannel) {
          view.getToggler().setRadioEnabled(MoexConfig.hideProfileChannel, isUpdate);
        } else if (itemId == R.id.btn_hideSponsoredMessages) {
          view.getToggler().setRadioEnabled(MoexConfig.hideSponsoredMessages, isUpdate);
        } else if (itemId == R.id.btn_channelBottomButton) {
          switch (MoexConfig.instance().getChannelBottomButton()) {
            case MoexConfig.CHANNEL_BOTTOM_BUTTON_DISCUSS:
              view.setData(R.string.ChannelBottomButtonDiscuss);
              break;
            case MoexConfig.CHANNEL_BOTTOM_BUTTON_MUTE:
              view.setData(R.string.ChannelBottomButtonMute);
              break;
            default:
              view.setData(R.string.ChannelBottomButtonAuto);
              break;
          }
        } else if (itemId == R.id.btn_showSearchTabs) {
          view.getToggler().setRadioEnabled(MoexConfig.showSearchTabs, isUpdate);
        } else if (itemId == R.id.btn_enableFeaturesButton) {
          view.getToggler().setRadioEnabled(MoexConfig.enableTestFeatures, isUpdate);
        } else if (itemId == R.id.btn_showIdProfile) {
          view.getToggler().setRadioEnabled(Settings.instance().isExperimentEnabled(Settings.EXPERIMENT_FLAG_SHOW_PEER_IDS), isUpdate);
        } else if (itemId == R.id.btn_experimentalAllow) {
          view.getToggler().setRadioEnabled(Settings.instance().isExperimentEnabled(Settings.EXPERIMENT_FLAG_ALLOW_EXPERIMENTS), isUpdate);
        } else if (itemId == R.id.btn_experimentalForums) {
          view.getToggler().setRadioEnabled(Settings.instance().isForumTopicsExperimentEnabled(), isUpdate);
          view.setEnabledAnimated(Settings.instance().isExperimentEnabled(Settings.EXPERIMENT_FLAG_ALLOW_EXPERIMENTS), isUpdate);
        } else if (itemId == R.id.btn_experimentalQuotes) {
          view.getToggler().setRadioEnabled(Settings.instance().isPartialQuotesExperimentEnabled(), isUpdate);
          view.setEnabledAnimated(Settings.instance().isExperimentEnabled(Settings.EXPERIMENT_FLAG_ALLOW_EXPERIMENTS), isUpdate);
        } else if (itemId == R.id.btn_experimentalLocalPremium) {
          view.getToggler().setRadioEnabled(Settings.instance().isLocalPremiumExperimentEnabled(), isUpdate);
          view.setEnabledAnimated(Settings.instance().isExperimentEnabled(Settings.EXPERIMENT_FLAG_ALLOW_EXPERIMENTS), isUpdate);
        } else if (itemId == R.id.btn_experimentalMessageHistory) {
          view.getToggler().setRadioEnabled(Settings.instance().isMessageHistoryExperimentEnabled(), isUpdate);
          view.setEnabledAnimated(Settings.instance().isExperimentEnabled(Settings.EXPERIMENT_FLAG_ALLOW_EXPERIMENTS), isUpdate);
        } else if (itemId == R.id.btn_ghostMode) {
          boolean experimentsAllowed = Settings.instance().isExperimentEnabled(Settings.EXPERIMENT_FLAG_ALLOW_EXPERIMENTS);
          view.getToggler().setRadioEnabled(MoexConfig.ghostMode, isUpdate);
          view.setEnabledAnimated(experimentsAllowed, isUpdate);
        } else if (itemId == R.id.btn_ghostNoRead) {
          view.getToggler().setRadioEnabled(MoexConfig.ghostMode && MoexConfig.instance().isGhostModeFlagEnabled(MoexConfig.GHOST_NO_READ), isUpdate);
          view.setEnabledAnimated(Settings.instance().isExperimentEnabled(Settings.EXPERIMENT_FLAG_ALLOW_EXPERIMENTS) && MoexConfig.ghostMode, isUpdate);
        } else if (itemId == R.id.btn_ghostNoTyping) {
          view.getToggler().setRadioEnabled(MoexConfig.ghostMode && MoexConfig.instance().isGhostModeFlagEnabled(MoexConfig.GHOST_NO_TYPING), isUpdate);
          view.setEnabledAnimated(Settings.instance().isExperimentEnabled(Settings.EXPERIMENT_FLAG_ALLOW_EXPERIMENTS) && MoexConfig.ghostMode, isUpdate);
        } else if (itemId == R.id.btn_ghostOffline) {
          view.getToggler().setRadioEnabled(MoexConfig.ghostMode && MoexConfig.instance().isGhostModeFlagEnabled(MoexConfig.GHOST_OFFLINE), isUpdate);
          view.setEnabledAnimated(Settings.instance().isExperimentEnabled(Settings.EXPERIMENT_FLAG_ALLOW_EXPERIMENTS) && MoexConfig.ghostMode, isUpdate);
        } else if (itemId == R.id.btn_ghostOnlineExceptions) {
          view.setData(Integer.toString(tdlib.ghostOnline().exceptions().length));
          view.setEnabledAnimated(Settings.instance().isExperimentEnabled(Settings.EXPERIMENT_FLAG_ALLOW_EXPERIMENTS), isUpdate);
        } else if (itemId == R.id.btn_ghostOnlineRetry) {
          String error = tdlib.ghostOnline().error();
          view.setData(error != null ? error : Lang.getString(tdlib.ghostOnline().pending() ? R.string.GhostOnlinePending : R.string.GhostOnlineReady));
        } else if (itemId == R.id.btn_folderTabsStyle) {
          int style = MoexConfig.instance().getFolderTabsStyle();
          view.setData(style == MoexConfig.FOLDER_TABS_STYLE_TEXT ? R.string.FolderTabsStyleText
            : style == MoexConfig.FOLDER_TABS_STYLE_ICONS ? R.string.FolderTabsStyleIcons
            : style == MoexConfig.FOLDER_TABS_STYLE_TEXT_ICONS ? R.string.FolderTabsStyleTextIcons
            : R.string.FolderTabsStyleUpstream);
          view.setEnabledAnimated(Settings.instance().chatFoldersEnabled(), isUpdate);
        } else if (itemId == R.id.btn_secureWindow) {
          view.getToggler().setRadioEnabled(MoexConfig.secureWindow, isUpdate);
        } else if (itemId == R.id.btn_secureOnlySecretChats) {
          view.getToggler().setRadioEnabled(MoexConfig.secureOnlySecretChats, isUpdate);
          view.setEnabledAnimated(MoexConfig.secureWindow, isUpdate);
        } else if (itemId == R.id.btn_confirmCalls) {
          view.getToggler().setRadioEnabled(MoexConfig.confirmCalls, isUpdate);
        } else if (itemId == R.id.btn_confirmVoice) {
          view.getToggler().setRadioEnabled(MoexConfig.confirmVoice, isUpdate);
        } else if (itemId == R.id.btn_hideMessagesBadge) {
          view.getToggler().setRadioEnabled(MoexConfig.hideMessagesBadge, isUpdate);
        } else if (itemId == R.id.btn_changeSizeLimit) {
          int size = MoexConfig.instance().getSizeLimit();
          switch (size) {
            case MoexConfig.SIZE_LIMIT_800:
              view.setData(R.string.px800);
              break;
            case MoexConfig.SIZE_LIMIT_1280:
              view.setData(R.string.px1280);
              break;
            case MoexConfig.SIZE_LIMIT_2560:
              view.setData(R.string.px2560);
              break;
          }
        } else if (itemId == R.id.btn_disableCameraButton) {
          view.getToggler().setRadioEnabled(MoexConfig.disableCameraButton, isUpdate);
        } else if (itemId == R.id.btn_disableRecordButton) {
          view.getToggler().setRadioEnabled(MoexConfig.disableRecordButton, isUpdate);
        } else if (itemId == R.id.btn_disableCommandsButton) {
          view.getToggler().setRadioEnabled(MoexConfig.disableCommandsButton, isUpdate);
        } else if (itemId == R.id.btn_disableSendAsButton) {
          view.getToggler().setRadioEnabled(MoexConfig.disableSendAsButton, isUpdate);
        } else if (itemId == R.id.btn_squareAvatar) {
          view.getToggler().setRadioEnabled(MoexConfig.squareAvatar, isUpdate);
        } else if (itemId == R.id.btn_blurDrawer) {
          view.getToggler().setRadioEnabled(MoexConfig.blurDrawer, isUpdate);
        } else if (itemId == R.id.btn_drawerItems) {
          view.setData(Integer.bitCount(MoexConfig.instance().getDrawerItems()) + " / " + Integer.bitCount(MoexConfig.DRAWER_ITEMS_ALL));
        } else if (itemId == R.id.btn_autoPauseMedia) {
          int flags = MoexConfig.instance().getAutoPauseMediaTypes();
          view.getToggler().setRadioEnabled(flags != 0, isUpdate);
          if (flags == MoexConfig.AUTO_PAUSE_MEDIA_ALL) {
            view.setData(R.string.AllMedia);
          } else if (flags == 0) {
            view.setData(R.string.Off);
          } else {
            StringBuilder sb = new StringBuilder();
            if ((flags & MoexConfig.AUTO_PAUSE_MEDIA_VOICE) != 0)
              sb.append(Lang.getString(R.string.VoiceMessages));
            if ((flags & MoexConfig.AUTO_PAUSE_MEDIA_ROUND) != 0)
              sb.append(sb.length() > 0 ? ", " : "").append(Lang.getString(R.string.VideoMessages));
            if ((flags & MoexConfig.AUTO_PAUSE_MEDIA_VIDEO) != 0)
              sb.append(sb.length() > 0 ? ", " : "").append(Lang.getString(R.string.Videos));
            view.setData(sb.toString());
          }
        } else if (itemId == R.id.btn_headerText) {
          int mode = MoexConfig.instance().getHeaderText();
          switch (mode) {
            case MoexConfig.HEADER_TEXT_CHATS:
              view.setData(R.string.Chats);
              break;
            case MoexConfig.HEADER_TEXT_MOEX:
              view.setData(R.string.moexHeaderClient);
              break;
            case MoexConfig.HEADER_TEXT_USERNAME:
              view.setData(R.string.Username);
              break;
            case MoexConfig.HEADER_TEXT_NAME:
              view.setData(R.string.login_FirstName);
              break;
          }
        } else if (itemId == R.id.btn_disableReactions) {
          view.getToggler().setRadioEnabled(MoexConfig.disableReactions, isUpdate);
        } else if (itemId == R.id.btn_hideMessagePanelButtons) {
          StringBuilder b = new StringBuilder();
          String separator = Lang.getConcatSeparator();

          if (MoexConfig.disableCameraButton) {
            if (b.length() > 0) b.append(separator);
            b.append(Lang.getString(R.string.DisableCameraButton));
          }
          if (MoexConfig.disableRecordButton) {
            if (b.length() > 0) b.append(separator);
            b.append(Lang.getString(R.string.DisableRecordButton));
          }
          if (MoexConfig.disableCommandsButton) {
            if (b.length() > 0) b.append(separator);
            b.append(Lang.getString(R.string.DisableCommandsButton));
          }
          if (MoexConfig.disableSendAsButton) {
            if (b.length() > 0) b.append(separator);
            b.append(Lang.getString(R.string.DisableSendAsButton));
          }

          view.setData(b.length() == 0 ? Lang.getString(R.string.BioNone) : b.toString());
        } else if (itemId == R.id.btn_hideBottomBar) {
          view.getToggler().setRadioEnabled(MoexConfig.hideBottomBar, isUpdate);
        } else if (itemId == R.id.btn_disableStickerTimestamp) {
          view.getToggler().setRadioEnabled(MoexConfig.hideStickerTimestamp, isUpdate);
        } else if (itemId == R.id.btn_roundedStickers) {
          view.getToggler().setRadioEnabled(MoexConfig.roundedStickers, isUpdate);
        } else if (itemId == R.id.btn_IncreaseRecents) {
          view.getToggler().setRadioEnabled(MoexConfig.increaseRecents, isUpdate);
        } else if (itemId == R.id.btn_rememberOptions) {
          view.getToggler().setRadioEnabled(MoexConfig.rememberOptions, isUpdate);
        } else if (itemId == R.id.btn_typingInstead) {
          view.getToggler().setRadioEnabled(MoexConfig.typingInsteadChoosing, isUpdate);
        } else if (itemId == R.id.btn_darkenDrawer) {
          view.getToggler().setRadioEnabled(MoexConfig.darkenDrawer, isUpdate);
        } else if (itemId == R.id.btn_silent) {
          view.getToggler().setRadioEnabled(MoexConfig.silentMessage, isUpdate);
        } else if (itemId == R.id.btn_toggleEdgeAnimSide) {
          updateSettingView(view, item, isUpdate);
        } else if (itemId == R.id.btn_rearRounds) {
          switch (MoexConfig.instance().getRoundVideos()) {
            case MoexConfig.START_WITH_FRONT:
              view.setData(R.string.frontCamera);
              break;
            case MoexConfig.START_WITH_REAR:
              view.setData(R.string.RearCamera);
              break;
            case MoexConfig.START_WITH_ASK:
              view.setData(R.string.Ask);
              break;
          }
        } else if (itemId == R.id.btn_roundVideoQuality) {
          view.setData(VideoQualityUi.roundLabel());
        } else if (itemId == R.id.btn_videoBitrate) {
          view.setData(VideoQualityUi.bitrateLabel());
        }
      }
    };

    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));

    switch (category) {
      case CATEGORY_GENERAL:
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.GeneralMoexSettings));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_hideMessagesBadge, 0, R.string.hideMessagesBadge));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_headerText, 0, R.string.changeHeaderText));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_showSearchTabs, 0, R.string.ShowSearchTabs));
        if (Config.EDGE_TO_EDGE_AVAILABLE) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_toggleEdgeAnimSide, 0, R.string.RightSwipeEdgeAnimation).setLongId(Settings.SETTING_FLAG_FORCE_DEFAULT_ANIMATION_FOR_RIGHT_SWIPE_EDGE).setBoolValue(true));
        }
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        if (Config.EDGE_TO_EDGE_AVAILABLE) {
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.RightSwipeEdgeAnimationInfo));
        }

        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.SecureWindow));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_secureWindow, 0, R.string.SecureWindow));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_secureOnlySecretChats, 0, R.string.SecureOnlySecretChats));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.SecureWindowInfo));
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ConfirmActions));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_confirmCalls, 0, R.string.ConfirmCall));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_confirmVoice, 0, R.string.ConfirmVoice));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ConfirmActionsInfo));

        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ProfileOptions));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_hidePhone, 0, R.string.hidePhoneNumber));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_hideProfileBirthdate, 0, R.string.HideProfileBirthdate));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_hideProfileChannel, 0, R.string.HideProfileChannel));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_hideProfileMusic, 0, R.string.HideProfileMusic));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        break;
      case CATEGORY_INTERFACE:
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.DrawerOptions));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_blurDrawer, 0, R.string.MoexBlurDrawer));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_darkenDrawer, 0, R.string.MoexDarkenDrawer));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_drawerItems, 0, R.string.DrawerItems));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.MoexChatsHeader));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_squareAvatar, 0, R.string.SquareAvatar));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_disableReactions, 0, R.string.DisableReactions));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_folderTabsStyle, 0, R.string.FolderTabsStyle));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.FolderTabsInfo));

        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.MoexHideButtons));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_hideMessagePanelButtons, 0, R.string.HideMessagePanelButtons));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_rearRounds, 0, R.string.SelectRoundVideos));
        if (Config.ROUND_VIDEOS_RECORD_SUPPORTED) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_roundVideoQuality, 0, R.string.RoundVideoQuality));
        }
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_hideBottomBar, 0, R.string.HideBottomBar));
        break;
      case CATEGORY_CHATS:
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.GhostMode));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_ghostMode, 0, R.string.GhostMode));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_ghostNoRead, 0, R.string.GhostNoRead));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_ghostNoTyping, 0, R.string.GhostNoTyping));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_ghostOffline, 0, R.string.GhostOffline));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_ghostOnlineExceptions, 0, R.string.GhostOnlineExceptions));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_ghostOnlineRetry, 0, R.string.GhostOnlineRetry));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.GhostModeInfo));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.GhostOnlineInfo));

        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ChannelOptions));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_channelBottomButton, 0, R.string.ChannelBottomButton));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_hideSponsoredMessages, 0, R.string.HideSponsoredMessages));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.HideSponsoredMessagesInfo));

        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.MoexStickersCount));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_disableStickerTimestamp, 0, R.string.DisableStickerTimestamp));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_roundedStickers, 0, R.string.RoundedStickers));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_IncreaseRecents, 0, R.string.IncreaseRecents));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ActivityOptions));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_rememberOptions, 0, R.string.RememberOptions));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getMarkdownString(this, R.string.RememberOptionsInfo), false));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_silent, 0, R.string.SilentMessage));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_typingInstead, 0, R.string.TypingInstead));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getMarkdownString(this, R.string.TypingInsteadInfo), false));

        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.AutoPauseMedia));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_autoPauseMedia, 0, R.string.AutoPauseMedia));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.AutoPauseMediaHint));
        break;
      case CATEGORY_MISC:
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Other));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_changeSizeLimit, 0, R.string.changeSizeLimit));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_videoBitrate, 0, R.string.VideoBitrate));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getMarkdownString(this, R.string.changeSizeLimitInfo), false));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_enableFeaturesButton, 0, R.string.EnableFeatures));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_exportSettings, R.drawable.baseline_import_export_24, R.string.ExportSettings));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_importSettings, R.drawable.baseline_file_download_24, R.string.ImportSettings));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        break;
      case CATEGORY_EXPERIMENTAL:
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ExperimentalOptions));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_experimentalAllow, 0, R.string.Experiment_EnableAll));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Experiment_EnableAllInfo));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_showIdProfile, 0, R.string.showIdProfile));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_experimentalForums, 0, R.string.Experiment_Forums));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Experiment_ForumsInfo));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_experimentalQuotes, 0, R.string.Experiment_PartialQuotes));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Experiment_PartialQuotesInfo));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_experimentalLocalPremium, 0, R.string.Experiment_LocalPremium));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Experiment_LocalPremiumInfo));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_experimentalMessageHistory, 0, R.string.Experiment_MessageHistory));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_messageHistoryView, 0, R.string.ViewMessageHistory));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Experiment_MessageHistoryInfo));
        break;
      default:
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.MoexAbout));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getMarkdownString(this, R.string.MoexAboutText), false));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_sendCrashReport, R.drawable.baseline_bug_report_24, R.string.SendCrashReport));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.MoexCategories));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_GeneralMoexSettings, R.drawable.baseline_settings_24, R.string.GeneralMoexSettings));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_InterfaceMoexSettings, R.drawable.baseline_extension_24, R.string.InterfaceMoexSettings));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_ChatsMoexSettings, R.drawable.baseline_chat_bubble_24, R.string.ChatsMoexSettings));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_MiscMoexSettings, R.drawable.baseline_layers_24, R.string.Other));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_ExperimentalMoexSettings, R.drawable.templarian_baseline_flask_24, R.string.ExperimentalOptions));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.MoexLinks));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_moexSourceLink, R.drawable.baseline_code_24, R.string.MoexSourceText));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

        items.add(new ListItem(ListItem.TYPE_BUILD_NO, R.id.btn_build, 0, R.string.MoexVer, false));
        break;
    }
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onActivityResult (int requestCode, int resultCode, Intent data) {
    if (resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    try {
      if (requestCode == REQUEST_EXPORT_SETTINGS) {
        try (OutputStream output = context().getContentResolver().openOutputStream(uri)) {
          if (output == null) throw new IllegalStateException("Unable to open destination");
          output.write(MoexConfig.instance().exportToJson().toString(2).getBytes(StandardCharsets.UTF_8));
        }
        UI.showToast(R.string.SettingsExported, Toast.LENGTH_SHORT);
      } else if (requestCode == REQUEST_IMPORT_SETTINGS) {
        StringBuilder text = new StringBuilder();
        try (InputStream input = context().getContentResolver().openInputStream(uri)) {
          if (input == null) throw new IllegalStateException("Unable to open source");
          byte[] buffer = new byte[4096];
          int count;
          while ((count = input.read(buffer)) != -1) text.append(new String(buffer, 0, count, StandardCharsets.UTF_8));
        }
        int changed = MoexConfig.instance().importFromJson(new JSONObject(text.toString()));
        adapter.notifyDataSetChanged();
        UI.showToast(Lang.getString(R.string.SettingsImported, changed), Toast.LENGTH_SHORT);
      }
    } catch (Throwable t) {
      UI.showToast(R.string.SettingsImportFailed, Toast.LENGTH_SHORT);
    }
  }
}
