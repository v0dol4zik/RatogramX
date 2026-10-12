package moe.kirao.mgx.ui;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.ui.ListItem;

import java.util.ArrayList;
import java.util.List;

import moe.kirao.mgx.MoexConfig;
import moe.kirao.mgx.utils.VideoQualityPolicy;

public final class VideoQualityUi {
  private VideoQualityUi () { }

  private static final int[] ROUND_IDS = {R.id.roundQualityDataSaver, R.id.roundQualityStandard, R.id.roundQualityRecommended, R.id.roundQualityMaximum};
  private static final int[] ROUND_LABELS = {R.string.RoundVideoQualityDataSaver, R.string.RoundVideoQualityStandard, R.string.RoundVideoQualityRecommended, R.string.RoundVideoQualityMaximum};
  private static final int[] BITRATE_IDS = {R.id.videoBitrateAuto, R.id.videoBitrateHigh, R.id.videoBitrateMaximum};
  private static final int[] BITRATE_LABELS = {R.string.VideoBitrateAuto, R.string.VideoBitrateHigh, R.string.VideoBitrateMaximum};

  public static int roundLabel () {
    return ROUND_LABELS[MoexConfig.instance().getRoundVideoQuality()];
  }

  public static int bitrateLabel () {
    return BITRATE_LABELS[MoexConfig.instance().getVideoBitrateMode()];
  }

  public static void showRoundOptions (ViewController<?> controller, Runnable after) {
    int selected = MoexConfig.instance().getRoundVideoQuality();
    List<ListItem> items = new ArrayList<>();
    for (int i = Device.NEED_HQ_ROUND_VIDEOS ? VideoQualityPolicy.ROUND_STANDARD : VideoQualityPolicy.ROUND_DATA_SAVER; i < ROUND_IDS.length; i++) {
      items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, ROUND_IDS[i], 0, ROUND_LABELS[i], R.id.btn_roundVideoQuality, selected == i));
    }
    controller.showSettings(new SettingsWrapBuilder(R.id.btn_roundVideoQuality).setRawItems(items.toArray(new ListItem[0]))
      .addHeaderItem(Lang.getString(R.string.RoundVideoQualityInfo)).setIntDelegate((id, result) -> {
        for (int i = 0; i < ROUND_IDS.length; i++) {
          if (result.get(R.id.btn_roundVideoQuality) == ROUND_IDS[i]) {
            MoexConfig.instance().setRoundVideoQuality(i);
            after.run();
            break;
          }
        }
      }));
  }

  public static void showBitrateOptions (ViewController<?> controller, Runnable after) {
    int selected = MoexConfig.instance().getVideoBitrateMode();
    ListItem[] items = new ListItem[BITRATE_IDS.length];
    for (int i = 0; i < items.length; i++) {
      items[i] = new ListItem(ListItem.TYPE_RADIO_OPTION, BITRATE_IDS[i], 0, BITRATE_LABELS[i], R.id.btn_videoBitrate, selected == i);
    }
    controller.showSettings(new SettingsWrapBuilder(R.id.btn_videoBitrate).setRawItems(items)
      .addHeaderItem(Lang.getString(R.string.VideoBitrateInfo)).setIntDelegate((id, result) -> {
        for (int i = 0; i < BITRATE_IDS.length; i++) {
          if (result.get(R.id.btn_videoBitrate) == BITRATE_IDS[i]) {
            MoexConfig.instance().setVideoBitrateMode(i);
            after.run();
            break;
          }
        }
      }));
  }
}
