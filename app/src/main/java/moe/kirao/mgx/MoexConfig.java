package moe.kirao.mgx;

import android.content.SharedPreferences;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkmore.Tracer;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.atomic.AtomicBoolean;

import me.vkryl.core.reference.ReferenceList;
import me.vkryl.leveldb.LevelDB;
import moe.kirao.mgx.utils.SystemUtils;

public class MoexConfig {

  private static final int VERSION = 1;
  private static final AtomicBoolean hasInstance = new AtomicBoolean(false);
  private static volatile MoexConfig instance;
  private final LevelDB config;
  private static final String KEY_VERSION = "version";

  public static final String KEY_DISABLE_CAMERA_BUTTON = "disable_camera_button";
  public static final String KEY_DISABLE_RECORD_BUTTON = "disable_record_button";
  public static final String KEY_DISABLE_COMMANDS_BUTTON = "disable_commands_button";
  public static final String KEY_HIDE_STICKER_TIMESTAMP = "hide_sticker_timestamp";
  public static final String KEY_ENABLE_FEATURES_BUTTON = "enable_features_button";
  public static final String KEY_HIDE_PHONE_NUMBER = "hide_phone_number";
  public static final String KEY_HIDE_PROFILE_MUSIC = "hide_profile_music";
  public static final String KEY_HIDE_PROFILE_BIRTHDATE = "hide_profile_birthdate";
  public static final String KEY_HIDE_PROFILE_CHANNEL = "hide_profile_channel";
  public static final String KEY_HIDE_SPONSORED_MESSAGES = "hide_sponsored_messages";
  public static final String KEY_SHOW_SEARCH_TABS = "show_search_tabs";
  public static final String KEY_DISABLE_SEND_AS_BUTTON = "disable_send_as_button";
  public static final String KEY_ROUNDED_STICKERS = "rounded_stickers";
  public static final String KEY_INCREASE_RECENTS_COUNT = "increase_recents_count";
  public static final String KEY_HIDE_MESSAGES_BADGE = "hide_messages_badge";
  public static final String KEY_CHANGE_SIZE_LIMIT = "change_size_limit";
  public static final String KEY_REMEMBER_SEND_OPTIONS = "remember_send_options";
  public static final String KEY_REMEMBER_SEND_OPTIONS_AUTHOR = "remember_send_options_author";
  public static final String KEY_REMEMBER_SEND_OPTIONS_CAPTIONS = "remember_send_options_captions";
  public static final String KEY_REMEMBER_SEND_OPTIONS_SOUND = "remember_send_options_sound";
  public static final String KEY_SQUARE_AVATAR = "square_avatar";
  public static final String KEY_BLUR_DRAWER = "blur_drawer";
  public static final String KEY_CHANGE_HEADER_TEXT = "change_header_text";
  public static final String KEY_TYPING_INSTEAD_CHOOSING = "typing_instead_choosing";
  public static final String KEY_DISABLE_REACTIONS = "disable_reactions";
  public static final String KEY_HIDE_BOTTOM_BAR = "hide_bottom_bar";
  public static final String KEY_DARKEN_DRAWER = "darken_drawer";
  public static final String KEY_DRAWER_ITEMS = "drawer_items";
  public static final String KEY_SILENT_MESSAGE = "silent_message";
  public static final String KEY_CHAT_QUICK_EDIT = "quick_edit";
  public static final String KEY_CHAT_QUICK_FEATURED = "quick_featured";
  public static final String KEY_CHANNEL_BOTTOM_BUTTON = "channel_bottom_button";

  public static final String KEY_ROUND_VIDEOS = "round_videos";
  public static final String KEY_DISABLE_PROXY_ON_VPN = "disable_proxy_on_vpn";
  /** Hide the channel advertised by an MTProto proxy from chat lists. */
  public static final String KEY_HIDE_PROXY_SPONSOR = "hide_proxy_sponsor";
  public static final String KEY_AUTO_PAUSE_MEDIA_TYPES = "auto_pause_media_types";
  public static final String KEY_AUTO_PAUSE_RESUME = "auto_pause_resume_system_playback";
  public static final String KEY_GHOST_MODE = "ghost_mode";
  public static final String KEY_GHOST_MODE_FLAGS = "ghost_mode_flags";
  /** Show Telegram chat folders as tabs above/below the chat list. */
  public static final String KEY_FOLDER_TABS = "folder_tabs";
  public static final String KEY_FOLDER_TABS_STYLE = "folder_tabs_style";
  public static final String KEY_CHAT_SWIPE_ACTION = "chat_swipe_action";
  public static final String KEY_DOUBLE_TAP_ACTION = "double_tap_action";
  public static final String KEY_DOUBLE_TAP_REACTION = "double_tap_reaction";
  public static final String KEY_SECURE_WINDOW = "secure_window";
  public static final String KEY_SECURE_ONLY_SECRET_CHATS = "secure_only_secret_chats";
  public static final int FOLDER_TABS_STYLE_UPSTREAM = -1;
  public static final int FOLDER_TABS_STYLE_TEXT = 0;
  public static final int FOLDER_TABS_STYLE_ICONS = 1;
  public static final int FOLDER_TABS_STYLE_TEXT_ICONS = 2;
  public static final int SWIPE_NONE = 0;
  public static final int SWIPE_ARCHIVE = 1;
  public static final int SWIPE_MUTE = 2;
  public static final int SWIPE_PIN = 3;
  public static final int SWIPE_READ = 4;
  public static final int SWIPE_DELETE = 5;
  public static final int DOUBLE_TAP_NONE = 0;
  public static final int DOUBLE_TAP_REACTION = 1;
  public static final int DOUBLE_TAP_REPLY = 2;
  public static final int DOUBLE_TAP_COPY = 3;
  public static final int DOUBLE_TAP_TRANSLATE = 4;
  public static final int DOUBLE_TAP_FORWARD = 5;
  public static final String DEFAULT_DOUBLE_TAP_REACTION = "❤";

  public static final int GHOST_NO_READ = 1;
  public static final int GHOST_NO_TYPING = 1 << 1;
  public static final int GHOST_OFFLINE = 1 << 2;
  public static final int GHOST_MODE_ALL = GHOST_NO_READ | GHOST_NO_TYPING | GHOST_OFFLINE;

  public static final int START_WITH_FRONT = 0;
  public static final int START_WITH_REAR = 1;
  public static final int START_WITH_ASK = 2;

  public static final int AUTO_PAUSE_MEDIA_VOICE = 1;
  public static final int AUTO_PAUSE_MEDIA_ROUND = 1 << 1;
  public static final int AUTO_PAUSE_MEDIA_VIDEO = 1 << 2;
  public static final int AUTO_PAUSE_MEDIA_ALL =
    AUTO_PAUSE_MEDIA_VOICE | AUTO_PAUSE_MEDIA_ROUND | AUTO_PAUSE_MEDIA_VIDEO;

  public static final int DRAWER_ITEM_CONTACTS = 1;
  public static final int DRAWER_ITEM_ARCHIVE = 1 << 1;
  public static final int DRAWER_ITEM_CALLS = 1 << 2;
  public static final int DRAWER_ITEM_SAVED_MESSAGES = 1 << 3;
  public static final int DRAWER_ITEM_HELP = 1 << 4;
  public static final int DRAWER_ITEM_NIGHT_MODE = 1 << 5;
  public static final int DRAWER_ITEM_PROXY = 1 << 6;
  public static final int DRAWER_ITEMS_ALL = DRAWER_ITEM_CONTACTS | DRAWER_ITEM_ARCHIVE |
    DRAWER_ITEM_CALLS | DRAWER_ITEM_SAVED_MESSAGES | DRAWER_ITEM_HELP | DRAWER_ITEM_NIGHT_MODE |
    DRAWER_ITEM_PROXY;

  public static final int SIZE_LIMIT_800 = 0;
  public static final int SIZE_LIMIT_1280 = 1;
  public static final int SIZE_LIMIT_2560 = 2;
  public static final int HEADER_TEXT_CHATS = 0;
  public static final int HEADER_TEXT_MOEX = 1;
  public static final int HEADER_TEXT_USERNAME = 2;
  public static final int HEADER_TEXT_NAME = 3;

  /** Bottom action shown for channel chats that have a linked discussion group. */
  public static final int CHANNEL_BOTTOM_BUTTON_AUTO = 0;
  public static final int CHANNEL_BOTTOM_BUTTON_DISCUSS = 1;
  public static final int CHANNEL_BOTTOM_BUTTON_MUTE = 2;

  public static boolean disableCameraButton = instance().getBoolean(KEY_DISABLE_CAMERA_BUTTON, false);
  public static boolean secureWindow = instance().getBoolean(KEY_SECURE_WINDOW, false);
  public static boolean secureOnlySecretChats = instance().getBoolean(KEY_SECURE_ONLY_SECRET_CHATS, false);
  public static boolean disableRecordButton = instance().getBoolean(KEY_DISABLE_RECORD_BUTTON, false);
  public static boolean disableCommandsButton = instance().getBoolean(KEY_DISABLE_COMMANDS_BUTTON, false);
  public static boolean disableSendAsButton = instance().getBoolean(KEY_DISABLE_SEND_AS_BUTTON, false);
  public static boolean hideStickerTimestamp = instance().getBoolean(KEY_HIDE_STICKER_TIMESTAMP, false);
  public static boolean enableTestFeatures = instance().getBoolean(KEY_ENABLE_FEATURES_BUTTON, false);
  public static boolean hidePhoneNumber = instance().getBoolean(KEY_HIDE_PHONE_NUMBER, false);
  public static boolean hideProfileMusic = instance().getBoolean(KEY_HIDE_PROFILE_MUSIC, false);
  public static boolean hideProfileBirthdate = instance().getBoolean(KEY_HIDE_PROFILE_BIRTHDATE, false);
  public static boolean hideProfileChannel = instance().getBoolean(KEY_HIDE_PROFILE_CHANNEL, false);
  public static boolean hideSponsoredMessages = instance().getBoolean(KEY_HIDE_SPONSORED_MESSAGES, false);
  public static boolean showSearchTabs = instance().getBoolean(KEY_SHOW_SEARCH_TABS, true);
  public static boolean roundedStickers = instance().getBoolean(KEY_ROUNDED_STICKERS, false);
  public static boolean increaseRecents = instance().getBoolean(KEY_INCREASE_RECENTS_COUNT, false);
  public static boolean hideMessagesBadge = instance().getBoolean(KEY_HIDE_MESSAGES_BADGE, false);
  public static boolean rememberOptions = instance().getBoolean(KEY_REMEMBER_SEND_OPTIONS, false);
  public static boolean squareAvatar = instance().getBoolean(KEY_SQUARE_AVATAR, false);
  public static boolean blurDrawer = instance().getBoolean(KEY_BLUR_DRAWER, false);
  public static boolean typingInsteadChoosing = instance().getBoolean(KEY_TYPING_INSTEAD_CHOOSING, true);
  public static boolean disableReactions = instance().getBoolean(KEY_DISABLE_REACTIONS, false);
  public static boolean hideBottomBar = instance().getBoolean(KEY_HIDE_BOTTOM_BAR, false);
  public static boolean darkenDrawer = instance().getBoolean(KEY_DARKEN_DRAWER, false);
  public static boolean silentMessage = instance().getBoolean(KEY_SILENT_MESSAGE, false);
  public static boolean quickEdit = instance().getBoolean(KEY_CHAT_QUICK_EDIT, false);
  public static boolean quickFeatured = instance().getBoolean(KEY_CHAT_QUICK_FEATURED, false);
  public static boolean disableProxyOnVpn = instance().getBoolean(KEY_DISABLE_PROXY_ON_VPN, false);
  public static boolean hideProxySponsor = instance().getBoolean(KEY_HIDE_PROXY_SPONSOR, false);
  public static boolean autoPauseResumeSystemPlayback = instance().getBoolean(KEY_AUTO_PAUSE_RESUME, false);
  public static boolean ghostMode = instance().getBoolean(KEY_GHOST_MODE, false);
  public static boolean folderTabs = instance().getBoolean(KEY_FOLDER_TABS, false);

  private MoexConfig () {
    File configDir = new File(UI.getAppContext().getFilesDir(), "moexconf");
    if (!configDir.exists() && !configDir.mkdir()) {
      throw new IllegalStateException("Unable to create working directory");
    }
    long ms = SystemClock.uptimeMillis();
    config = new LevelDB(new File(configDir, "db").getPath(), true, new LevelDB.ErrorHandler() {
      @Override public boolean onFatalError (LevelDB levelDB, Throwable error) {
        Tracer.onDatabaseError(error);
        return true;
      }

      @Override public void onError (LevelDB levelDB, String message, @Nullable Throwable error) {
        // Cannot use custom Log, since settings are not yet loaded
        android.util.Log.e(Log.LOG_TAG, message, error);
      }
    });
    int configVersion = 0;
    try {
      configVersion = Math.max(0, config.tryGetInt(KEY_VERSION));
    } catch (FileNotFoundException ignored) {
    }
    if (configVersion > VERSION) {
      Log.e("Downgrading database version: %d -> %d", configVersion, VERSION);
      config.putInt(KEY_VERSION, VERSION);
    }
    for (int version = configVersion + 1; version <= VERSION; version++) {
      SharedPreferences.Editor editor = config.edit();
      editor.putInt(KEY_VERSION, version);
      editor.apply();
    }
    Log.i("Opened database in %dms", SystemClock.uptimeMillis() - ms);
  }

  public static MoexConfig instance () {
    if (instance == null) {
      synchronized (MoexConfig.class) {
        if (instance == null) {
          if (hasInstance.getAndSet(true)) throw new AssertionError();
          instance = new MoexConfig();
        }
      }
    }
    return instance;
  }

  public LevelDB edit () {
    return config.edit();
  }

  public void remove (String key) {
    config.remove(key);
  }

  public void putLong (String key, long value) {
    config.putLong(key, value);
  }

  public long getLong (String key, long defValue) {
    return config.getLong(key, defValue);
  }

  public void putLongArray (String key, long[] value) {
    config.putLongArray(key, value);
  }

  public long[] getLongArray (String key) {
    return config.getLongArray(key);
  }

  public void putInt (String key, int value) {
    config.putInt(key, value);
  }

  public int getInt (String key, int defValue) {
    return config.getInt(key, defValue);
  }

  public void putFloat (String key, float value) {
    config.putFloat(key, value);
  }

  public void getFloat (String key, float defValue) {
    config.getFloat(key, defValue);
  }

  public void putBoolean (String key, boolean value) {
    config.putBoolean(key, value);
  }

  public boolean getBoolean (String key, boolean defValue) {
    return config.getBoolean(key, defValue);
  }

  public void putString (String key, @NonNull String value) {
    config.putString(key, value);
  }

  public String getString (String key, String defValue) {
    return config.getString(key, defValue);
  }

  public boolean containsKey (String key) {
    return config.contains(key);
  }

  public LevelDB config () {
    return config;
  }

  public interface SettingsChangeListener {
    void onSettingsChanged (String key, Object newSettings, Object oldSettings);
  }

  private ReferenceList<SettingsChangeListener> settingsListeners;

  public void addSettingsListener (SettingsChangeListener listener) {
    if (settingsListeners == null)
      settingsListeners = new ReferenceList<>();
    settingsListeners.add(listener);
  }

  public void removeSettingsListener (SettingsChangeListener listener) {
    if (settingsListeners != null) {
      settingsListeners.remove(listener);
    }
  }

  private void notifyClientListeners (String key, Object newSettings, Object oldSettings) {
    if (settingsListeners != null) {
      for (SettingsChangeListener listener : settingsListeners) {
        listener.onSettingsChanged(key, newSettings, oldSettings);
      }
    }
  }

  public void toggleDisableCameraButton () {
    notifyClientListeners(KEY_DISABLE_CAMERA_BUTTON, !disableCameraButton, disableCameraButton);
    putBoolean(KEY_DISABLE_CAMERA_BUTTON, disableCameraButton ^= true);
  }

  public void toggleDisableRecordButton () {
    notifyClientListeners(KEY_DISABLE_RECORD_BUTTON, !disableRecordButton, disableRecordButton);
    putBoolean(KEY_DISABLE_RECORD_BUTTON, disableRecordButton ^= true);
  }

  public void toggleDisableCommandsButton () {
    notifyClientListeners(KEY_DISABLE_COMMANDS_BUTTON, !disableCommandsButton, disableCommandsButton);
    putBoolean(KEY_DISABLE_COMMANDS_BUTTON, disableCommandsButton ^= true);
  }

  public void toggleDisableStickerTimestamp () {
    putBoolean(KEY_HIDE_STICKER_TIMESTAMP, hideStickerTimestamp ^= true);
  }

  public void toggleEnableFeaturesButton () {
    putBoolean(KEY_ENABLE_FEATURES_BUTTON, enableTestFeatures ^= true);
  }

  public void toggleHidePhoneNumber () {
    putBoolean(KEY_HIDE_PHONE_NUMBER, hidePhoneNumber ^= true);
    notifyClientListeners(KEY_HIDE_PHONE_NUMBER, !hidePhoneNumber, hidePhoneNumber);
  }

  public void toggleHideProfileMusic () {
    putBoolean(KEY_HIDE_PROFILE_MUSIC, hideProfileMusic ^= true);
    notifyClientListeners(KEY_HIDE_PROFILE_MUSIC, hideProfileMusic, !hideProfileMusic);
  }

  public void toggleHideProfileBirthdate () {
    putBoolean(KEY_HIDE_PROFILE_BIRTHDATE, hideProfileBirthdate ^= true);
    notifyClientListeners(KEY_HIDE_PROFILE_BIRTHDATE, hideProfileBirthdate, !hideProfileBirthdate);
  }

  public void toggleHideProfileChannel () {
    putBoolean(KEY_HIDE_PROFILE_CHANNEL, hideProfileChannel ^= true);
    notifyClientListeners(KEY_HIDE_PROFILE_CHANNEL, hideProfileChannel, !hideProfileChannel);
  }

  public void toggleHideSponsoredMessages () {
    putBoolean(KEY_HIDE_SPONSORED_MESSAGES, hideSponsoredMessages ^= true);
    notifyClientListeners(KEY_HIDE_SPONSORED_MESSAGES, hideSponsoredMessages, !hideSponsoredMessages);
  }

  public void toggleShowSearchTabs () {
    putBoolean(KEY_SHOW_SEARCH_TABS, showSearchTabs ^= true);
    notifyClientListeners(KEY_SHOW_SEARCH_TABS, showSearchTabs, !showSearchTabs);
  }

  public void toggleDisableSendAsButton () {
    notifyClientListeners(KEY_DISABLE_SEND_AS_BUTTON, !disableSendAsButton, disableSendAsButton);
    putBoolean(KEY_DISABLE_SEND_AS_BUTTON, disableSendAsButton ^= true);
  }

  public void toggleRoundedStickers () {
    putBoolean(KEY_ROUNDED_STICKERS, roundedStickers ^= true);
  }

  public void toggleIncreaseRecents () {
    notifyClientListeners(KEY_INCREASE_RECENTS_COUNT, !increaseRecents, increaseRecents);
    putBoolean(KEY_INCREASE_RECENTS_COUNT, increaseRecents ^= true);
  }

  public void toggleHideMessagesBadge () {
    putBoolean(KEY_HIDE_MESSAGES_BADGE, hideMessagesBadge ^= true);
  }

  public int getSizeLimit () {
    return getInt(KEY_CHANGE_SIZE_LIMIT, SIZE_LIMIT_1280);
  }

  public void setSizeLimit (int size) {
    if (size == SIZE_LIMIT_1280) {
      remove(KEY_CHANGE_SIZE_LIMIT);
    } else {
      putInt(KEY_CHANGE_SIZE_LIMIT, size);
    }
  }

  public void toggleRememberSendOptions () {
    putBoolean(KEY_REMEMBER_SEND_OPTIONS, rememberOptions ^= true);
  }

  public void SendAsCopy (boolean state) {
    putBoolean(KEY_REMEMBER_SEND_OPTIONS_AUTHOR, state);
  }

  public Boolean getAuthorState () {
    return getBoolean(KEY_REMEMBER_SEND_OPTIONS_AUTHOR, false);
  }

  public void SendWithoutCaption (boolean state) {
    putBoolean(KEY_REMEMBER_SEND_OPTIONS_CAPTIONS, state);
  }

  public Boolean getCaptionState () {
    return getBoolean(KEY_REMEMBER_SEND_OPTIONS_CAPTIONS, false);
  }

  public void SendSilent (boolean state) {
    putBoolean(KEY_REMEMBER_SEND_OPTIONS_SOUND, state);
  }

  public Boolean getSilentState () {
    return getBoolean(KEY_REMEMBER_SEND_OPTIONS_SOUND, false);
  }

  public void toggleSquareAvatar () {
    putBoolean(KEY_SQUARE_AVATAR, squareAvatar ^= true);
  }

  public void toggleBlurDrawer () {
    putBoolean(KEY_BLUR_DRAWER, blurDrawer ^= true);
    notifyClientListeners(KEY_BLUR_DRAWER, !blurDrawer, blurDrawer);
  }

  public int getHeaderText () {
    return getInt(KEY_CHANGE_HEADER_TEXT, HEADER_TEXT_MOEX);
  }

  public int getChannelBottomButton () {
    int value = getInt(KEY_CHANNEL_BOTTOM_BUTTON, CHANNEL_BOTTOM_BUTTON_AUTO);
    return value >= CHANNEL_BOTTOM_BUTTON_AUTO && value <= CHANNEL_BOTTOM_BUTTON_MUTE ? value : CHANNEL_BOTTOM_BUTTON_AUTO;
  }

  public void setChannelBottomButton (int value) {
    int oldValue = getChannelBottomButton();
    value = value >= CHANNEL_BOTTOM_BUTTON_AUTO && value <= CHANNEL_BOTTOM_BUTTON_MUTE ? value : CHANNEL_BOTTOM_BUTTON_AUTO;
    if (oldValue == value) {
      return;
    }
    if (value == CHANNEL_BOTTOM_BUTTON_AUTO) {
      remove(KEY_CHANNEL_BOTTOM_BUTTON);
    } else {
      putInt(KEY_CHANNEL_BOTTOM_BUTTON, value);
    }
    notifyClientListeners(KEY_CHANNEL_BOTTOM_BUTTON, value, oldValue);
  }

  public void setHeaderText (int mode) {
    int oldState = getHeaderText();
    if (mode == HEADER_TEXT_MOEX) {
      remove(KEY_CHANGE_HEADER_TEXT);
    } else {
      putInt(KEY_CHANGE_HEADER_TEXT, mode);
    }
    notifyClientListeners(KEY_CHANGE_HEADER_TEXT, mode, oldState);
  }

  public void toggleTypingInsteadChoosing () {
    putBoolean(KEY_TYPING_INSTEAD_CHOOSING, typingInsteadChoosing ^= true);
  }

  public void toggleDisableReactions () {
    putBoolean(KEY_DISABLE_REACTIONS, disableReactions ^= true);
  }

  public void toggleSecureWindow () {
    boolean oldValue = secureWindow;
    secureWindow = !secureWindow;
    putBoolean(KEY_SECURE_WINDOW, secureWindow);
    notifyClientListeners(KEY_SECURE_WINDOW, secureWindow, oldValue);
  }

  public void toggleSecureOnlySecretChats () {
    boolean oldValue = secureOnlySecretChats;
    secureOnlySecretChats = !secureOnlySecretChats;
    putBoolean(KEY_SECURE_ONLY_SECRET_CHATS, secureOnlySecretChats);
    notifyClientListeners(KEY_SECURE_ONLY_SECRET_CHATS, secureOnlySecretChats, oldValue);
  }

  public void toggleHideBottomBar () {
    putBoolean(KEY_HIDE_BOTTOM_BAR, hideBottomBar ^= true);
  }

  public void toggleDarkenDrawer () {
    putBoolean(KEY_DARKEN_DRAWER, darkenDrawer ^= true);
  }

  public int getDrawerItems () {
    return getInt(KEY_DRAWER_ITEMS, DRAWER_ITEMS_ALL) & DRAWER_ITEMS_ALL;
  }

  public boolean isDrawerItemVisible (int item) {
    return (getDrawerItems() & item) != 0;
  }

  public void setDrawerItems (int items) {
    items &= DRAWER_ITEMS_ALL;
    int oldItems = getDrawerItems();
    if (items == oldItems) {
      return;
    }
    if (items == DRAWER_ITEMS_ALL) {
      remove(KEY_DRAWER_ITEMS);
    } else {
      putInt(KEY_DRAWER_ITEMS, items);
    }
    notifyClientListeners(KEY_DRAWER_ITEMS, items, oldItems);
  }

  public void toggleSilentMessage () {
    putBoolean(KEY_SILENT_MESSAGE, silentMessage ^= true);
  }

  public void toggleQuickEdit () {
    putBoolean(KEY_CHAT_QUICK_EDIT, quickEdit ^= true);
  }

  public void toggleQuickFeatured () {
    putBoolean(KEY_CHAT_QUICK_FEATURED, quickFeatured ^= true);
  }

  public int getRoundVideos () {
    return getInt(KEY_ROUND_VIDEOS, Settings.instance().startRoundWithRear() ? START_WITH_REAR : START_WITH_FRONT);
  }

  public void setRoundVideos (int value) {
    putInt(KEY_ROUND_VIDEOS, value);
  }

  public void toggleDisableProxyOnVpn () {
    putBoolean(KEY_DISABLE_PROXY_ON_VPN, disableProxyOnVpn ^= true);
    notifyClientListeners(KEY_DISABLE_PROXY_ON_VPN, disableProxyOnVpn, !disableProxyOnVpn);
  }

  public void toggleHideProxySponsor () {
    putBoolean(KEY_HIDE_PROXY_SPONSOR, hideProxySponsor ^= true);
    notifyClientListeners(KEY_HIDE_PROXY_SPONSOR, hideProxySponsor, !hideProxySponsor);
  }

  public void toggleGhostMode () {
    boolean oldValue = ghostMode;
    ghostMode = !ghostMode;
    putBoolean(KEY_GHOST_MODE, ghostMode);
    notifyClientListeners(KEY_GHOST_MODE, ghostMode, oldValue);
  }

  public void toggleFolderTabs () {
    boolean oldValue = folderTabs;
    folderTabs = !folderTabs;
    putBoolean(KEY_FOLDER_TABS, folderTabs);
    notifyClientListeners(KEY_FOLDER_TABS, folderTabs, oldValue);
  }

  public int getFolderTabsStyle () {
    int value = getInt(KEY_FOLDER_TABS_STYLE, FOLDER_TABS_STYLE_UPSTREAM);
    return value >= FOLDER_TABS_STYLE_UPSTREAM && value <= FOLDER_TABS_STYLE_TEXT_ICONS ? value : FOLDER_TABS_STYLE_UPSTREAM;
  }

  public int getChatSwipeAction () {
    int value = getInt(KEY_CHAT_SWIPE_ACTION, SWIPE_NONE);
    return value >= SWIPE_NONE && value <= SWIPE_DELETE ? value : SWIPE_NONE;
  }

  public void setChatSwipeAction (int value) {
    int oldValue = getChatSwipeAction();
    if (value < SWIPE_NONE || value > SWIPE_DELETE) value = SWIPE_NONE;
    if (oldValue == value) return;
    if (value == SWIPE_NONE) remove(KEY_CHAT_SWIPE_ACTION); else putInt(KEY_CHAT_SWIPE_ACTION, value);
    notifyClientListeners(KEY_CHAT_SWIPE_ACTION, value, oldValue);
  }

  public int getDoubleTapAction () {
    int value = getInt(KEY_DOUBLE_TAP_ACTION, DOUBLE_TAP_NONE);
    return value >= DOUBLE_TAP_NONE && value <= DOUBLE_TAP_FORWARD ? value : DOUBLE_TAP_NONE;
  }

  @NonNull
  public String getDoubleTapReaction () {
    String value = getString(KEY_DOUBLE_TAP_REACTION, DEFAULT_DOUBLE_TAP_REACTION);
    return value == null || value.isEmpty() ? DEFAULT_DOUBLE_TAP_REACTION : value;
  }

  public void setDoubleTapAction (int value) {
    int oldValue = getDoubleTapAction();
    if (value < DOUBLE_TAP_NONE || value > DOUBLE_TAP_FORWARD) value = DOUBLE_TAP_NONE;
    if (oldValue == value) return;
    if (value == DOUBLE_TAP_NONE) remove(KEY_DOUBLE_TAP_ACTION); else putInt(KEY_DOUBLE_TAP_ACTION, value);
    notifyClientListeners(KEY_DOUBLE_TAP_ACTION, value, oldValue);
  }

  public void setDoubleTapReaction (@NonNull String value) {
    if (value.isEmpty()) value = DEFAULT_DOUBLE_TAP_REACTION;
    String oldValue = getDoubleTapReaction();
    if (oldValue.equals(value)) return;
    putString(KEY_DOUBLE_TAP_REACTION, value);
    notifyClientListeners(KEY_DOUBLE_TAP_REACTION, value, oldValue);
  }

  public void setFolderTabsStyle (int value) {
    int oldValue = getFolderTabsStyle();
    if (value < FOLDER_TABS_STYLE_UPSTREAM || value > FOLDER_TABS_STYLE_TEXT_ICONS) value = FOLDER_TABS_STYLE_UPSTREAM;
    if (oldValue == value) return;
    if (value == FOLDER_TABS_STYLE_UPSTREAM) remove(KEY_FOLDER_TABS_STYLE); else putInt(KEY_FOLDER_TABS_STYLE, value);
    notifyClientListeners(KEY_FOLDER_TABS_STYLE, value, oldValue);
  }

  public int getGhostModeFlags () {
    return getInt(KEY_GHOST_MODE_FLAGS, GHOST_MODE_ALL) & GHOST_MODE_ALL;
  }

  public boolean isGhostModeFlagEnabled (int flag) {
    return (getGhostModeFlags() & flag) != 0;
  }

  public void setGhostModeFlags (int flags) {
    int oldFlags = getGhostModeFlags();
    flags &= GHOST_MODE_ALL;
    if (flags == oldFlags) return;
    putInt(KEY_GHOST_MODE_FLAGS, flags);
    notifyClientListeners(KEY_GHOST_MODE_FLAGS, flags, oldFlags);
  }

  public void toggleGhostModeFlag (int flag) {
    setGhostModeFlags(getGhostModeFlags() ^ (flag & GHOST_MODE_ALL));
  }

  public static boolean shouldBypassProxyForVpn () {
    return disableProxyOnVpn && SystemUtils.isVpnActive();
  }

  public int getAutoPauseMediaTypes () {
    return getInt(KEY_AUTO_PAUSE_MEDIA_TYPES, 0);
  }

  public void setAutoPauseMediaTypes (int flags) {
    putInt(KEY_AUTO_PAUSE_MEDIA_TYPES, flags);
  }

  public static boolean shouldAutoPauseFor (int mediaType) {
    return (instance().getAutoPauseMediaTypes() & mediaType) != 0;
  }

  public void setAutoPauseResumeSystemPlayback (boolean value) {
    autoPauseResumeSystemPlayback = value;
    putBoolean(KEY_AUTO_PAUSE_RESUME, value);
  }
}
