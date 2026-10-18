package moe.kirao.mgx;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.app.NotificationCompat;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.component.attach.MediaBottomBar;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessageViewGroup;
import org.thunderdog.challegram.component.chat.WallpaperView;
import org.thunderdog.challegram.data.MediaWrapper;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGChat;
import org.thunderdog.challegram.data.TGMessagePoll;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerTopView;
import org.thunderdog.challegram.player.RoundVideoController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibNotification;
import org.thunderdog.challegram.telegram.TdlibNotificationGroup;
import org.thunderdog.challegram.telegram.TdlibNotificationUtils;
import org.thunderdog.challegram.telegram.EmojiMediaType;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.AppIconManager;
import org.thunderdog.challegram.v.MessagesRecyclerView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.RectFrameLayout;
import org.thunderdog.challegram.widget.EmojiLayout;
import org.thunderdog.challegram.widget.emoji.EmojiLayoutSectionPager;
import org.thunderdog.challegram.widget.emoji.section.EmojiSection;
import org.thunderdog.challegram.widget.emoji.section.EmojiSectionView;
import org.thunderdog.challegram.widget.rtl.RtlViewPager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import moe.kirao.mgx.utils.GhostReadStore;
import moe.kirao.mgx.utils.GhostReadPolicy;
import moe.kirao.mgx.utils.LocalRichMessageStore;
import moe.kirao.mgx.utils.RichMessageDocument;
import moe.kirao.mgx.utils.RichMessageMarkdown;
import moe.kirao.mgx.utils.RichMessageUtils;
import moe.kirao.mgx.utils.RichMessagePolicy;
import moe.kirao.mgx.ui.RichMessageEditorController;
import tgx.bridge.DeviceTokenRetriever;
import tgx.bridge.TokenRetrieverListener;

/** Device regressions with local fixtures. Never sends messages or edits server privacy. */
public final class EmulatorSmokeInstrumentation extends Instrumentation {
  private static final int FIXTURE_ACCOUNT = 2_147_483_000;
  private static final long FIXTURE_CHAT = -999_999_999_999L;
  private static final String CHANNEL = "ratogramx_emulator_smoke";
  private static final String RICH_RESTART_MARKER = "Ratogram X rich restart fixture";
  private MainActivity activity;
  private Tdlib tdlib;
  private int passed, failed;
  private boolean resumeAfterFixtureCrash;
  private String requestedCheck;

  @Override public void onCreate (Bundle arguments) {
    super.onCreate(arguments);
    resumeAfterFixtureCrash = arguments != null && "true".equals(arguments.getString("resumeAfterFixtureCrash"));
    requestedCheck = arguments != null ? arguments.getString("check") : null;
    start();
  }

  @Override public void onStart () {
    try {
      require(BuildConfig.DEBUG && getTargetContext().getPackageName().endsWith(".debug"), "Development package required");
      require(Build.VERSION.SDK_INT >= 30, "Android 11 or newer required for IME checks");
      activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      waitForIdleSync();
      onUi(() -> {
        tdlib = activity.currentTdlib();
        if (tdlib.context().inRecoveryMode()) {
          require(resumeAfterFixtureCrash, "Resolve the previous crash before testing; recovery mode cannot load the account");
          activity.proceedFromRecovery();
        }
      });
      if (!"rich_messages_markdown_syntax".equals(requestedCheck)) {
        long deadline = SystemClock.uptimeMillis() + 60_000;
        while (tdlib.myUserId() == 0 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100);
        require(tdlib.myUserId() != 0, "An authorized development account is required");
      }
      waitForIdleSync();
      runCase("clip_pixels_and_extreme_insets", () -> onUi(this::clipPixels));
      runCase("forum_counters_storage_and_account_isolation", () -> onUi(this::forumCounters));
      runCase("ghost_reactions_pins_and_service_reads", () -> onUi(this::ghostContentReads));
      runCase("rich_messages_private_storage", this::richMessageStorage);
      runCase("rich_messages_markdown_syntax", () -> onUi(this::richMarkdownSyntax));
      runCase("rich_messages_editor_preview_and_history", this::richMessageEditor);
      runCase("rich_messages_restart_write", this::richMessageRestartWrite);
      runCase("rich_messages_restart_read_cleanup", this::richMessageRestartRead);
      runCase("github_updater_release_check", this::githubUpdaterCheck);
      runCase("local_notifications_and_delayed_push", () -> onUi(this::localNotifications));
      runCase("android_notification_icons", () -> onUi(this::notificationIcons));
      runCase("poll_media_render_and_update", this::pollRendering);
      runCase("round_video_playback_with_keyboard_and_panels", this::roundVideo);
      runCase("profile_tab_mutations", () -> onUi(this::profileTabs));
      runCase("emoji_keyboard_navigation_and_reopen", this::emojiKeyboard);
      runCase("push_configuration_error", this::pushConfiguration);
      require(passed + failed > 0, "Unknown check: " + requestedCheck);
    } catch (Throwable error) {
      failed++;
      report("setup", error);
    }
    Bundle result = new Bundle();
    result.putString("stream", "\nRatogram emulator checks: " + passed + " passed, " + failed + " failed\n");
    finish(failed == 0 ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
  }

  private interface Check { void run () throws Exception; }

  private <T> T localResult (Consumer<LocalRichMessageStore.Callback<T>> operation) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    AtomicReference<T> value = new AtomicReference<>();
    AtomicReference<Exception> failure = new AtomicReference<>();
    operation.accept((result, error) -> { value.set(result); failure.set(error); done.countDown(); });
    require(done.await(20, TimeUnit.SECONDS), "Local storage operation timed out");
    if (failure.get() != null) throw failure.get();
    return value.get();
  }

  private RichMessageDocument richDocument (String text) {
    RichMessageDocument document = new RichMessageDocument();
    RichMessageDocument.Block heading = new RichMessageDocument.Block(RichMessageDocument.HEADING);
    heading.text = text; heading.size = 1; document.blocks.add(heading);
    RichMessageDocument.Block list = new RichMessageDocument.Block(RichMessageDocument.LIST);
    list.text = "First item\nSecond item"; list.ordered = true; document.blocks.add(list);
    RichMessageDocument.Block code = new RichMessageDocument.Block(RichMessageDocument.CODE);
    code.text = "local only 🐀"; code.extra = "text"; document.blocks.add(code);
    return document;
  }

  private void richMessageStorage () throws Exception {
    File root = new File(getTargetContext().getCacheDir(), "rich-store-smoke-" + java.util.UUID.randomUUID());
    File legacy = new File(root, (FIXTURE_ACCOUNT - 2) + "-77/messages.bin");
    require(legacy.getParentFile().mkdirs(), "Cannot prepare legacy history fixture");
    try (java.io.DataOutputStream out = new java.io.DataOutputStream(new FileOutputStream(legacy))) {
      out.writeInt(0x5258524d); out.writeInt(1); out.writeInt(FIXTURE_ACCOUNT - 2); out.writeLong(77); out.writeInt(1);
      out.writeLong(RichMessagePolicy.localId(123)); out.writeInt(1); out.writeInt(0);
      out.writeBoolean(false); out.writeInt(1); out.writeInt(RichMessageDocument.PARAGRAPH);
      for (String value : new String[] {"Legacy fixture", "", ""}) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8); out.writeInt(bytes.length); out.write(bytes);
      }
      out.writeInt(2); out.writeInt(0); out.writeInt(0); out.writeBoolean(false);
    }
    LocalRichMessageStore legacyStore = new LocalRichMessageStore(root, FIXTURE_ACCOUNT - 2, 77);
    java.util.List<LocalRichMessageStore.Entry> old = localResult(legacyStore::load);
    require(old.size() == 1 && old.get(0).document.blocks.get(0).text.equals("Legacy fixture"), "Legacy history no longer loads");
    this.<LocalRichMessageStore.Entry>localResult(callback -> legacyStore.save(old.get(0).id, old.get(0).document, callback));
    try (java.io.DataInputStream in = new java.io.DataInputStream(new java.io.FileInputStream(legacy))) {
      require(in.readInt() == 0x5258524d && in.readInt() == 2, "Saving did not migrate legacy history");
    }
    require(this.<java.util.List<LocalRichMessageStore.Entry>>localResult(legacyStore::load).size() == 1, "Migration lost the existing entry");
    LocalRichMessageStore store = new LocalRichMessageStore(root, FIXTURE_ACCOUNT, 77);
    LocalRichMessageStore anotherAccount = new LocalRichMessageStore(root, FIXTURE_ACCOUNT - 1, 77);
    LocalRichMessageStore anotherUser = new LocalRichMessageStore(root, FIXTURE_ACCOUNT, 78);
    RichMessageDocument document = richDocument("Private rich fixture");
    File source = richFixturePhoto(root);
    RichMessageDocument.Block photo = new RichMessageDocument.Block(RichMessageDocument.PHOTO);
    photo.photoPath = source.getAbsolutePath(); photo.width = 96; photo.height = 64;
    document.blocks.add(photo);
    LocalRichMessageStore.Entry entry = localResult(callback -> store.save(0, document, callback));
    try {
      File storedPhoto = new File(entry.document.blocks.get(3).photoPath);
      require(!source.equals(storedPhoto) && storedPhoto.isFile(), "Image was not copied into private history");
      require(source.delete(), "Could not remove source image fixture");
      java.util.List<LocalRichMessageStore.Entry> reloaded = localResult(callback -> new LocalRichMessageStore(root, FIXTURE_ACCOUNT, 77).load(callback));
      require(reloaded.size() == 1 && reloaded.get(0).id == entry.id, "Local history did not survive reopening the store");
      require(new File(reloaded.get(0).document.blocks.get(3).photoPath).isFile(), "Private image depends on the removed source");
      java.util.List<LocalRichMessageStore.Entry> other = localResult(anotherAccount::load);
      require(other.isEmpty(), "Local history leaked across accounts");
      other = localResult(anotherUser::load);
      require(other.isEmpty(), "Reused account slot exposed another user's history");
      RichMessageDocument changed = entry.document.copy(); changed.blocks.get(0).text = "Edited private fixture";
      LocalRichMessageStore.Entry edited = localResult(callback -> store.save(entry.id, changed, callback));
      require(edited.id == entry.id && edited.date == entry.date && edited.editDate != 0, "Editing replaced identity or creation date");
      require(localResult(callback -> store.delete(entry.id, callback)), "Local delete did not remove the entry");
      reloaded = localResult(store::load);
      require(reloaded.isEmpty(), "Deleted entry reappeared on reload");
      require(!storedPhoto.exists(), "Deleting the entry retained its private image");
    } finally {
      // Only this randomly named fixture directory is removed.
      File[] accountDirs = root.listFiles();
      if (accountDirs != null) for (File dir : accountDirs) {
        File[] files = dir.listFiles(); if (files != null) for (File file : files) file.delete();
        dir.delete();
      }
      root.delete();
    }
  }

  private File richFixturePhoto (File directory) throws Exception {
    require(directory.isDirectory() || directory.mkdirs(), "Could not create image fixture directory");
    File source = new File(directory, "rich-photo-fixture-" + java.util.UUID.randomUUID() + ".jpg");
    Bitmap bitmap = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    canvas.drawColor(Color.rgb(45, 65, 90));
    android.graphics.Paint paint = new android.graphics.Paint(); paint.setColor(Color.rgb(180, 60, 70));
    canvas.drawRect(8, 8, 88, 56, paint);
    try (FileOutputStream out = new FileOutputStream(source)) {
      require(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out), "Could not encode image fixture");
    } finally { bitmap.recycle(); }
    return source;
  }

  private void richMessageRestartWrite () throws Exception {
    LocalRichMessageStore store = new LocalRichMessageStore(tdlib.id(), tdlib.myUserId());
    java.util.List<LocalRichMessageStore.Entry> entries = localResult(store::load);
    for (LocalRichMessageStore.Entry entry : entries) if (RICH_RESTART_MARKER.equals(entry.document.blocks.get(0).text))
      this.<Boolean>localResult(callback -> store.delete(entry.id, callback));
    File source = richFixturePhoto(getTargetContext().getCacheDir());
    try {
      RichMessageDocument document = richDocument(RICH_RESTART_MARKER);
      RichMessageDocument.Block photo = new RichMessageDocument.Block(RichMessageDocument.PHOTO);
      photo.photoPath = source.getAbsolutePath(); photo.width = 96; photo.height = 64; document.blocks.add(photo);
      RichMessageMarkdown.Source markdown = RichMessageMarkdown.source(document);
      RichMessageDocument parsed = RichMessageMarkdown.parse(markdown.text + "\n\n**Offline bold** and __italic__", markdown, false);
      LocalRichMessageStore.Entry entry = localResult(callback -> store.save(0, parsed, callback));
      require(RichMessagePolicy.isLocalId(entry.id), "Restart fixture has no private identity");
    } finally { source.delete(); }
  }

  private void richMessageRestartRead () throws Exception {
    LocalRichMessageStore store = new LocalRichMessageStore(tdlib.id(), tdlib.myUserId());
    java.util.List<LocalRichMessageStore.Entry> entries = localResult(store::load);
    LocalRichMessageStore.Entry fixture = null;
    for (LocalRichMessageStore.Entry entry : entries) if (RICH_RESTART_MARKER.equals(entry.document.blocks.get(0).text)) fixture = entry;
    require(fixture != null, "Restart fixture did not survive the process restart");
    long id = fixture.id;
    try {
      require(new File(fixture.document.blocks.get(3).photoPath).isFile(), "Private photo did not survive the process restart");
      require(fixture.toMessage(tdlib.myUserId()).content instanceof TdApi.MessageRichMessage, "Restored content has the wrong type");
      require(fixture.document.markdownSource != null && fixture.document.markdownSource.contains("**Offline bold**"), "Restart lost the Markdown source");
      require(Arrays.stream(RichMessageUtils.text(fixture.document.output()).entities).anyMatch(e -> e.type instanceof TdApi.TextEntityTypeItalic), "Restart lost inline formatting");
    } finally { this.<Boolean>localResult(callback -> store.delete(id, callback)); }
  }

  private void richMarkdownSyntax () throws Exception {
    RichMessageDocument empty = new RichMessageDocument();
    empty.blocks.add(new RichMessageDocument.Block(RichMessageDocument.PARAGRAPH));
    require(RichMessageMarkdown.source(empty).text.equals(""), "A new editor must start with empty Markdown");
    String source = "# Heading\n\n**bold** __italic__ ~~strike~~ ||hidden|| `code` [link](https://example.com) 🐀\n\n> Quote\n> - nested\n\n3. [x] checked\n  - child\n\n```java\n\n**literal**\n```";
    RichMessageDocument document = RichMessageMarkdown.parse(source, new RichMessageMarkdown.Source(source), false);
    TdApi.FormattedText text = RichMessageUtils.text(document.output());
    require(text.text.contains("bold italic strike hidden code link 🐀"), "Native Telegram Markdown did not render inline syntax");
    for (Class<?> type : new Class<?>[] {TdApi.TextEntityTypeBold.class, TdApi.TextEntityTypeItalic.class, TdApi.TextEntityTypeStrikethrough.class,
      TdApi.TextEntityTypeSpoiler.class, TdApi.TextEntityTypeCode.class, TdApi.TextEntityTypeTextUrl.class})
      require(Arrays.stream(text.entities).anyMatch(e -> type.isInstance(e.type)), "Missing native formatting: " + type.getSimpleName());
    String escaped = RichMessageUtils.plainText(RichMessageMarkdown.inline("\\*\\*literal\\*\\* `a\\*b`"));
    require(escaped.equals("**literal** a\\*b"), "Escapes changed literal text or inline code");
    java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
    document.write(new java.io.DataOutputStream(buffer));
    RichMessageDocument restored = RichMessageDocument.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(buffer.toByteArray())));
    require(source.equals(restored.markdownSource), "Persistence changed Markdown whitespace");
    require(text.text.equals(RichMessageUtils.text(restored.output()).text), "Persistence changed rendered content");
    require(Arrays.stream(RichMessageUtils.text(restored.output()).entities).anyMatch(e -> e.type instanceof TdApi.TextEntityTypeSpoiler), "Persistence lost spoiler formatting");
    // A remote message has no stored Markdown: its supported formatting must be editable, too.
    RichMessageDocument remote = RichMessageDocument.fromRichMessage(document.output());
    RichMessageMarkdown.Source editable = RichMessageMarkdown.source(remote);
    RichMessageDocument edited = RichMessageMarkdown.parse(editable.text.replace("bold", "changed"), editable, false);
    require(RichMessageUtils.text(edited.output()).text.contains("changed italic strike hidden code link 🐀"), "Editing remote Markdown lost content");
    require(Arrays.stream(RichMessageUtils.text(edited.output()).entities).anyMatch(e -> e.type instanceof TdApi.TextEntityTypeBold), "Editing remote Markdown lost bold formatting");
    RichMessageDocument nested = RichMessageMarkdown.parse("**outer __inner__ tail**", new RichMessageMarkdown.Source(""), false);
    RichMessageMarkdown.Source nestedSource = RichMessageMarkdown.source(RichMessageDocument.fromRichMessage(nested.output()));
    RichMessageDocument changed = RichMessageMarkdown.parse(nestedSource.text.replace("tail", "end"), nestedSource, false);
    TdApi.FormattedText nestedText = RichMessageUtils.text(changed.output());
    require(nestedText.text.equals("outer inner end"), "Editing nested formatting left literal Markdown delimiters");
    require(Arrays.stream(nestedText.entities).anyMatch(e -> e.type instanceof TdApi.TextEntityTypeItalic), "Nested italic formatting was lost");
  }

  private void richAttachmentRouting (MessagesController parent) throws Exception {
    Field direction = field(Lang.class, "languageRtl");
    boolean previous = direction.getBoolean(null);
    try {
      for (boolean rtl : new boolean[] {false, true}) {
        direction.setBoolean(null, rtl);
        MediaLayout layout = new MediaLayout(parent);
        layout.initDefault(parent);
        try {
          MediaBottomBar bar = (MediaBottomBar) field(MediaLayout.class, "bottomBar").get(layout);
          Object[] items = (Object[]) field(MediaBottomBar.class, "items").get(bar);
          require(items.length == 6, "Rich message has no separate attachment action");
          require(bar.getCurrentIndex() == (rtl ? 3 : 2), "RTL opened the wrong attachment category");
          ViewController<?> files = layout.createControllerForIndex(rtl ? 4 : 1);
          ViewController<?> location = layout.createControllerForIndex(rtl ? 2 : 3);
          require(files instanceof org.thunderdog.challegram.component.attach.MediaBottomFilesController, "File routing is reversed");
          require(location instanceof org.thunderdog.challegram.component.attach.MediaBottomLocationController, "Location routing is reversed");
          files.destroy(); // The map was never created, so it has no lifecycle to close.
          Object rich = items[rtl ? 0 : 5];
          require(Lang.getString(R.string.RichMessageAttach).equals(field(rich.getClass(), "name").get(rich)), "Rich attachment action has no label");
        } finally { layout.performDestroy(); }
      }
    } finally { direction.setBoolean(null, previous); }
  }

  private void githubUpdaterCheck () throws Exception {
    org.thunderdog.challegram.util.AppUpdater updater = activity.appUpdater();
    CountDownLatch done = new CountDownLatch(1);
    org.thunderdog.challegram.util.AppUpdater.Listener listener = (state, old, isApk) -> {
      if (old == org.thunderdog.challegram.util.AppUpdater.State.CHECKING) done.countDown();
    };
    onUi(() -> {
      require(updater.state() == org.thunderdog.challegram.util.AppUpdater.State.NONE, "Updater is already busy");
      updater.addListener(listener);
      updater.checkForUpdates();
    });
    try {
      require(done.await(60, TimeUnit.SECONDS), "GitHub release check timed out");
      onUi(() -> {
        require(!updater.checkFailed(), "GitHub release check failed");
        int state = updater.state();
        require(state == org.thunderdog.challegram.util.AppUpdater.State.NONE || state == org.thunderdog.challegram.util.AppUpdater.State.AVAILABLE, "Unexpected update state after checking GitHub");
        if (state == org.thunderdog.challegram.util.AppUpdater.State.AVAILABLE) {
          require(updater.flowType() == org.thunderdog.challegram.util.AppUpdater.FlowType.GITHUB, "Update came from an unexpected source");
          activity.hideContextualPopups(false);
        }
      });
    } finally { onUi(() -> updater.removeListener(listener)); }
  }

  private void richMessageEditor () throws Exception {
    waitForNavigation(null);
    Settings settings = Settings.instance();
    long previous = (long) method(Settings.class, "getExperiments").invoke(settings);
    String marker = "Rich editor smoke " + java.util.UUID.randomUUID();
    LocalRichMessageStore store = new LocalRichMessageStore(tdlib.id(), tdlib.myUserId());
    MessagesController[] parent = new MessagesController[1];
    RichMessageEditorController[] editor = new RichMessageEditorController[1];
    File sourcePhoto = richFixturePhoto(getTargetContext().getCacheDir());
    File[] preparedPhoto = {null};
    long[] createdId = {0};
    String[] markdown = {null};
    int[] selection = {0}, scrollTop = {0};
    try {
      onUi(() -> {
        settings.setExperimentEnabled(Settings.EXPERIMENT_FLAG_ALLOW_EXPERIMENTS, true);
        settings.setLocalPremiumExperimentEnabled(true);
        TdApi.Chat saved = tdlib.chat(tdlib.myUserId());
        require(saved != null, "Open Saved Messages once before running the editor fixture");
        parent[0] = new MessagesController(activity, tdlib);
        parent[0].setArguments(new MessagesController.Arguments(null, saved, null, null, null, 0, null));
        require(activity.navigation().navigateTo(parent[0]), "Could not open Saved Messages");
      });
      waitForNavigation(parent[0]);
      onUi(() -> {
        richAttachmentRouting(parent[0]);
        MediaLayout layout = new MediaLayout(parent[0]);
        layout.initDefault(parent[0]);
        layout.show();
        MediaBottomBar bar = (MediaBottomBar) field(MediaLayout.class, "bottomBar").get(layout);
        int richIndex = Lang.rtl() ? 0 : 5;
        bar.postDelayed(() -> bar.setSelectedIndex(richIndex), 500);
      });
      SystemClock.sleep(800);
      onUi(() -> {
        require(activity.navigation().getCurrentStackItem() instanceof RichMessageEditorController, "Attachment action did not open the Markdown editor");
        editor[0] = (RichMessageEditorController) activity.navigation().getCurrentStackItem();
      });
      waitForNavigation(editor[0]);
      waitForIdleSync(); SystemClock.sleep(400);
      onUi(() -> {
        require(editor[0].getRecyclerView().getAdapter().getItemCount() == 2, "Editor did not use one Markdown field");
        ViewGroup textRow = (ViewGroup) editor[0].getRecyclerView().getLayoutManager().findViewByPosition(0);
        require(textRow != null, "Editor text field did not render");
        org.thunderdog.challegram.widget.MaterialEditTextGroup textInput = (org.thunderdog.challegram.widget.MaterialEditTextGroup) textRow.getChildAt(0);
        String value = "# " + marker + "\n\n**Bold** and __italic__, ||spoiler|| and [link](https://example.com) 🐀\n\n> Quote\n\n- [x] First item\n  - Nested item\n- Second item\n\n```java\nlocal only\n```";
        textInput.setText(value);
        require(value.equals(field(RichMessageEditorController.class, "markdown").get(editor[0])), "Native text field did not update Markdown");
        RichMessageDocument.Block photo = (RichMessageDocument.Block) method(RichMessageEditorController.class, "preparePhoto", String.class).invoke(editor[0], sourcePhoto.getAbsolutePath());
        preparedPhoto[0] = new File(photo.photoPath);
        textInput.getEditText().setSelection(marker.length() + 2);
        method(RichMessageEditorController.class, "insertPhoto", RichMessageDocument.Block.class).invoke(editor[0], photo);
        markdown[0] = textInput.getText().toString();
        selection[0] = markdown[0].indexOf("Bold");
        textInput.getEditText().setSelection(selection[0], selection[0] + 4);
      });
      waitForIdleSync(); SystemClock.sleep(250);
      onUi(() -> {
        editor[0].getRecyclerView().scrollBy(0, 60);
        scrollTop[0] = editor[0].getRecyclerView().getChildAt(0).getTop();
        View view = editor[0].getValue();
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap)); saveBitmap(bitmap, "rich-message-markdown-editor-smoke.png"); bitmap.recycle();
        editor[0].onMenuItemPressed(R.id.menu_btn_view, null);
      });
      waitForNavigation(null);
      SystemClock.sleep(400);
      onUi(() -> {
        ViewController<?> viewer = activity.navigation().getCurrentStackItem();
        require(viewer instanceof org.thunderdog.challegram.ui.InstantViewController, "Preview did not use Instant View");
        org.thunderdog.challegram.ui.InstantViewController instant = (org.thunderdog.challegram.ui.InstantViewController) viewer;
        require(RichMessagePolicy.isLocalId(instant.getRichMessageSource().id), "Preview has a server identity");
        View view = viewer.getValue();
        require(view.getWidth() > 0 && view.getHeight() > 0, "Preview has no layout");
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap)); saveBitmap(bitmap, "rich-message-preview-smoke.png"); bitmap.recycle();
        viewer.navigateBack();
      });
      waitForNavigation(editor[0]);
      waitForIdleSync(); SystemClock.sleep(400);
      onUi(() -> {
        org.thunderdog.challegram.widget.MaterialEditTextGroup input = (org.thunderdog.challegram.widget.MaterialEditTextGroup) field(RichMessageEditorController.class, "sourceInput").get(editor[0]);
        require(markdown[0].equals(input.getText().toString()), "Preview rewrote the Markdown source");
        require(input.getEditText().getSelectionStart() == selection[0] && input.getEditText().getSelectionEnd() == selection[0] + 4, "Preview lost the selection");
        int actualTop = editor[0].getRecyclerView().getChildAt(0).getTop();
        // Native focus/inset layout can round the restored offset by a few pixels.
        require(Math.abs(actualTop - scrollTop[0]) <= Screen.dp(4f), "Preview lost the source scroll position: " + scrollTop[0] + " -> " + actualTop);
        method(RichMessageEditorController.class, "onDoneClick").invoke(editor[0]);
      });
      waitForNavigation(parent[0]);
      java.util.List<LocalRichMessageStore.Entry> entries = localResult(store::load);
      for (LocalRichMessageStore.Entry entry : entries) if (marker.equals(entry.document.blocks.get(0).text)) createdId[0] = entry.id;
      require(createdId[0] != 0, "Editor did not save a local entry");
      LocalRichMessageStore.Entry created = entries.stream().filter(e -> e.id == createdId[0]).findFirst().orElseThrow();
      require(markdown[0].equals(created.document.markdownSource), "Saving lost the Markdown source");
      require(Arrays.stream(RichMessageUtils.text(created.document.output()).entities).anyMatch(e -> e.type instanceof TdApi.TextEntityTypeItalic), "Saving lost inline formatting");
      onUi(() -> {
        editor[0] = new RichMessageEditorController(activity, tdlib);
        editor[0].setArguments(new RichMessageEditorController.Args(parent[0], RichMessagePolicy.Mode.LOCAL, created.toMessage(tdlib.myUserId()), created, created.document));
        parent[0].navigateTo(editor[0]);
      });
      waitForNavigation(editor[0]);
      onUi(() -> {
        org.thunderdog.challegram.widget.MaterialEditTextGroup input = (org.thunderdog.challegram.widget.MaterialEditTextGroup) field(RichMessageEditorController.class, "sourceInput").get(editor[0]);
        require(markdown[0].equals(input.getText().toString()), "Reopening edit changed the source");
        input.setText(markdown[0] + "\n\n**Edited**");
        method(RichMessageEditorController.class, "onDoneClick").invoke(editor[0]);
      });
      waitForNavigation(parent[0]);
      java.util.List<LocalRichMessageStore.Entry> edited = localResult(store::load);
      require(edited.stream().anyMatch(e -> e.id == createdId[0] && e.document.markdownSource.endsWith("**Edited**") && e.editDate != 0), "Markdown edit was not persisted");
      onUi(() -> {
        settings.setLocalPremiumExperimentEnabled(false);
        parent[0].refreshLocalRichMessages();
      });
      waitForIdleSync(); SystemClock.sleep(600);
      onUi(() -> {
        TGMessage bubble = parent[0].getManager().getAdapter().findMessageById(createdId[0]);
        require(bubble != null && bubble.isFakeMessage() && !bubble.isRealMessage(), "Local entry is missing from history or treated as server data");
        require(!bubble.canBeSelected() && !bubble.canReplyTo() && !bubble.canBeReacted(), "Local entry exposes server actions");
        require(bubble.getIds().length == 0, "Local identity leaked into server message IDs");
        require(parent[0].getManager().localRichMessage(createdId[0]) != null, "Disabling local Premium hid existing entries");
        bubble.buildLayout(720);
        require(bubble.getHeight() > 0, "Local bubble cannot be rendered");
      });
    } finally {
      if (createdId[0] != 0) this.<Boolean>localResult(callback -> store.delete(createdId[0], callback));
      sourcePhoto.delete();
      if (preparedPhoto[0] != null) preparedPhoto[0].delete();
      onUi(() -> {
        settings.setLocalPremiumExperimentEnabled((previous & Settings.EXPERIMENT_FLAG_LOCAL_PREMIUM) != 0);
        settings.setExperimentEnabled(Settings.EXPERIMENT_FLAG_ALLOW_EXPERIMENTS, (previous & Settings.EXPERIMENT_FLAG_ALLOW_EXPERIMENTS) != 0);
        if (parent[0] != null && !parent[0].isDestroyed()) {
          parent[0].refreshLocalRichMessages();
          ViewController<?> current = activity.navigation().getCurrentStackItem();
          if (current == editor[0]) current.navigateBack();
          else if (current == parent[0]) current.navigateBack();
        }
      });
    }
  }

  private void waitForNavigation (ViewController<?> expected) throws Exception {
    long deadline = SystemClock.uptimeMillis() + 20_000;
    boolean[] ready = {false};
    do {
      onUi(() -> {
        ViewController<?> current = activity.navigation().getCurrentStackItem();
        ready[0] = !activity.navigation().isAnimating() && !activity.navigation().getStack().isLocked() &&
          current != null && current.isFocused() && (expected == null || current == expected);
      });
      if (ready[0]) return;
      SystemClock.sleep(100);
    } while (SystemClock.uptimeMillis() < deadline);
    throw new AssertionError("Navigation did not finish opening the expected screen");
  }

  private void runCase (String name, Check check) {
    if (requestedCheck != null && !Arrays.asList(requestedCheck.split(",")).contains(name)) return;
    Throwable failure = null;
    try { check.run(); } catch (Throwable error) { failure = error; }
    if (failure == null) passed++; else failed++;
    report(name, failure);
    waitForIdleSync();
  }

  private void onUi (Check check) throws Exception {
    final Throwable[] failure = new Throwable[1];
    runOnMainSync(() -> {
      try { check.run(); } catch (Throwable error) { failure[0] = error; }
    });
    if (failure[0] instanceof Exception) throw (Exception) failure[0];
    if (failure[0] != null) throw (Error) failure[0];
  }

  private void report (String name, Throwable error) {
    Bundle result = new Bundle();
    result.putString("stream", "\n" + (error == null ? "PASS " : "FAIL ") + name + (error == null ? "" : ": " + android.util.Log.getStackTraceString(error)) + "\n");
    sendStatus(error == null ? 0 : -2, result);
  }

  private static void require (boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private void clipPixels () {
    RectFrameLayout clip = new RectFrameLayout(activity);
    View child = new View(activity);
    child.setBackgroundColor(Color.RED);
    clip.addView(child, new FrameLayout.LayoutParams(100, 200));
    clip.measure(exact(100), exact(200));
    clip.layout(0, 0, 100, 200);
    Bitmap bitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888);
    clip.setClip(30, 50);
    clip.draw(new Canvas(bitmap));
    require(bitmap.getPixel(50, 29) == Color.TRANSPARENT, "Top inset leaked pixels");
    require(bitmap.getPixel(50, 30) == Color.RED && bitmap.getPixel(50, 149) == Color.RED, "Visible video was clipped");
    require(bitmap.getPixel(50, 150) == Color.TRANSPARENT, "Composer inset leaked pixels");
    bitmap.eraseColor(Color.TRANSPARENT);
    clip.setClip(300, 400);
    clip.draw(new Canvas(bitmap));
    require(bitmap.getPixel(50, 100) == Color.TRANSPARENT, "Overlapping insets must hide all pixels");
    clip.setClip(-1, -1);
    clip.draw(new Canvas(bitmap));
    require(bitmap.getPixel(50, 0) == Color.RED && bitmap.getPixel(50, 199) == Color.RED, "Insets did not reset");
    bitmap.recycle();
  }

  private static int exact (int value) { return View.MeasureSpec.makeMeasureSpec(value, View.MeasureSpec.EXACTLY); }

  private void profileTabs () throws Exception {
    ViewPagerTopView tabs = new ViewPagerTopView(activity);
    try {
      for (int width : new int[] {ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT}) {
        tabs.setLayoutParams(new FrameLayout.LayoutParams(width, Screen.dp(48)));
        tabs.setItems(new String[0]);
        tabs.addItem("Members");
        measureTabs(tabs);
        tabs.setItems(new String[] {"Media", "Files", "Music", "Links"});
        tabs.setSelectionFactor(2);
        measureTabs(tabs);
        tabs.removeItemAt(0);
        require(tabs.getSelectionFactor() == 1, "Removing a preceding tab changed selection");
        measureTabs(tabs);
        tabs.removeItemAt(1);
        measureTabs(tabs);
        tabs.addItemAtIndex("Shared media", 0);
        require(tabs.getSelectionFactor() == 2, "Inserting a preceding tab changed selection");
        measureTabs(tabs);
        tabs.removeLastItem();
        measureTabs(tabs);
        tabs.removeItemAt(0);
        tabs.removeLastItem();
        measureTabs(tabs);
        require(tabs.getSelectionFactor() == 0 && tabs.getChildCount() == 0, "Empty tabs retained selection or hit targets");
        tabs.addItem("Members");
        measureTabs(tabs);
        require(tabs.getSelectionFactor() == 0, "First added tab was not selected");
        tabs.setItems(new String[0]);
        measureTabs(tabs);
        tabs.setItems(new String[] {"Media", "Files", "Links"});
        tabs.setSelectionFactor(2);
        tabs.setItems(new String[] {"Members"});
        measureTabs(tabs);
        require(tabs.getSelectionFactor() == 0, "Replacing tabs retained an invalid selection");
      }
    } finally { tabs.performDestroy(); }
  }

  private void measureTabs (ViewPagerTopView tabs) throws Exception {
    tabs.measure(exact(720), exact(Screen.dp(48)));
    tabs.layout(0, 0, tabs.getMeasuredWidth(), tabs.getMeasuredHeight());
    require(tabs.getChildCount() == tabs.getItemCount(), "Tab hit target count is stale");
    require(field(ViewPagerTopView.class, "totalWidth").getInt(tabs) ==
      (int) method(ViewPagerTopView.class, "calculateTotalWidth").invoke(tabs), "Tab width cache is stale");
    int left = field(ViewPagerTopView.class, "selectionLeft").getInt(tabs);
    int width = field(ViewPagerTopView.class, "selectionWidth").getInt(tabs);
    require(left >= 0 && left + width <= tabs.getMeasuredWidth(), "Selection escaped tab bounds");
  }

  private void emojiKeyboard () throws Exception {
    final EmojiLayout[] keyboard = new EmojiLayout[1];
    final ViewController<?>[] owner = new ViewController<?>[1];
    final FrameLayout[] stage = new FrameLayout[1];
    final int[] saved = new int[2];
    final int[] searches = new int[1];
    try {
      onUi(() -> {
        saved[0] = Settings.instance().getEmojiPosition();
        saved[1] = Settings.instance().getEmojiMediaSection();
        Settings.instance().setEmojiPosition(1);
        Settings.instance().setEmojiMediaSection(EmojiMediaType.STICKER);
        owner[0] = new ViewController<Void>(activity, tdlib) {
          @Override public int getId () { return 0; }
          @Override protected View onCreateView (Context context) { return new FrameLayout(context); }
        };
        keyboard[0] = new EmojiLayout(activity);
        keyboard[0].initWithMediasEnabled(owner[0], true, false, new EmojiLayout.Listener() {
          @Override public void onSearchRequested (EmojiLayout layout, boolean stickers) { searches[0]++; }
        }, owner[0], false, true);
        keyboard[0].setForceHeight(Screen.dp(320));
        stage[0] = new FrameLayout(activity);
        stage[0].setClickable(true);
        stage[0].addView(keyboard[0], new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(320), Gravity.BOTTOM));
        activity.addToRoot(stage[0], false);
      });
      awaitFrame();
      SystemClock.sleep(400);
      onUi(() -> assertEmojiPage(keyboard[0], 1));
      tapEmojiSection(keyboard[0], "mediaSectionsView", -1);
      onUi(() -> assertEmojiPage(keyboard[0], 0));
      tapEmojiSection(keyboard[0], "emojiHeaderView", EmojiSection.SECTION_EMOJI_SMILEYS);
      onUi(() -> assertEmojiPage(keyboard[0], 0));
      tapEmojiSection(keyboard[0], "emojiHeaderView", EmojiSection.SECTION_SWITCH_TO_MEDIA);
      onUi(() -> assertEmojiPage(keyboard[0], 1));
      tapEmojiSection(keyboard[0], "mediaSectionsView", -2);
      onUi(() -> require(searches[0] > 0 || keyboard[0].getCurrentEmojiSection() == EmojiMediaType.GIF, "GIF tab neither opened GIFs nor requested search"));
      // Interrupt an outer page transition by closing the keyboard immediately.
      onUi(() -> {
        RtlViewPager pager = (RtlViewPager) field(EmojiLayout.class, "pager").get(keyboard[0]);
        pager.setCurrentItem(0, true);
        stage[0].removeView(keyboard[0]);
      });
      SystemClock.sleep(100);
      onUi(() -> stage[0].addView(keyboard[0]));
      awaitFrame();
      SystemClock.sleep(700);
      onUi(() -> assertEmojiPage(keyboard[0], 0));
      onUi(() -> {
        RtlViewPager pager = (RtlViewPager) field(EmojiLayout.class, "pager").get(keyboard[0]);
        pager.setCurrentItem(1, true);
        stage[0].removeView(keyboard[0]);
      });
      SystemClock.sleep(100);
      onUi(() -> stage[0].addView(keyboard[0]));
      awaitFrame();
      SystemClock.sleep(700);
      onUi(() -> assertEmojiPage(keyboard[0], 1));
      tapEmojiSection(keyboard[0], "mediaSectionsView", -1);
      onUi(() -> assertEmojiPage(keyboard[0], 0));
      tapEmojiSection(keyboard[0], "emojiHeaderView", EmojiSection.SECTION_SWITCH_TO_MEDIA);
      onUi(() -> assertEmojiPage(keyboard[0], 1));
      tapEmojiSection(keyboard[0], "mediaSectionsView", -3);
      onUi(() -> {
        EmojiLayoutSectionPager sections = mediaSectionPager(keyboard[0]);
        require(sections.canChangeSection() && sections.getNextSection() == -1, "Media section transition did not finish");
      });
    } finally {
      onUi(() -> {
        if (stage[0] != null) activity.removeFromRoot(stage[0]);
        if (keyboard[0] != null) keyboard[0].destroy();
        if (owner[0] != null) owner[0].destroy();
        Settings.instance().setEmojiPosition(saved[0]);
        Settings.instance().setEmojiMediaSection(saved[1]);
      });
    }
  }

  private EmojiLayoutSectionPager mediaSectionPager (EmojiLayout keyboard) throws Exception {
    Object adapter = field(EmojiLayout.class, "adapter").get(keyboard);
    ViewController<?> media = (ViewController<?>) method(adapter.getClass(), "getCachedItem", int.class).invoke(adapter, 1);
    return (EmojiLayoutSectionPager) media.getValue();
  }

  private void assertEmojiPage (EmojiLayout keyboard, int expected) throws Exception {
    RtlViewPager pager = (RtlViewPager) field(EmojiLayout.class, "pager").get(keyboard);
    View emojiHeader = (View) field(EmojiLayout.class, "emojiHeaderView").get(keyboard);
    View mediaHeader = (View) field(EmojiLayout.class, "mediaSectionsView").get(keyboard);
    require(pager.getCurrentItem() == expected, "Keyboard page did not switch");
    require(field(EmojiLayout.class, "scrollState").getInt(keyboard) == 0, "Keyboard kept an unfinished outer transition");
    require(Math.abs(field(EmojiLayout.class, "currentPageFactor").getFloat(keyboard) - expected) < .001f, "Header and content page factors differ");
    require(Math.abs((expected == 0 ? emojiHeader : mediaHeader).getTranslationX()) < 1f, "Selected header is offscreen");
    Object adapter = field(EmojiLayout.class, "adapter").get(keyboard);
    ViewController<?> content = (ViewController<?>) method(adapter.getClass(), "getCachedItem", int.class).invoke(adapter, expected);
    require(Math.abs(content.getValue().getLeft() - pager.getScrollX()) <= 1, "Selected content is offscreen: left=" +
      content.getValue().getLeft() + ", scrollX=" + pager.getScrollX() + ", width=" + pager.getWidth() +
      ", firstLayout=" + field(androidx.viewpager.widget.ViewPager.class, "mFirstLayout").getBoolean(pager));
  }

  private void tapEmojiSection (EmojiLayout keyboard, String headerName, int index) throws Exception {
    final int[] point = new int[2];
    onUi(() -> {
      View header = (View) field(EmojiLayout.class, headerName).get(keyboard);
      View section = findEmojiSection(header, index);
      require(section != null, "Missing visible header section " + index);
      section.getLocationOnScreen(point);
      point[0] += section.getWidth() / 2;
      point[1] += section.getHeight() / 2;
    });
    long time = SystemClock.uptimeMillis();
    MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, point[0], point[1], 0);
    MotionEvent up = MotionEvent.obtain(time, time + 50, MotionEvent.ACTION_UP, point[0], point[1], 0);
    try { sendPointerSync(down); sendPointerSync(up); } finally { down.recycle(); up.recycle(); }
    SystemClock.sleep(700);
    waitForIdleSync();
  }

  private View findEmojiSection (View view, int index) {
    if (view.getVisibility() != View.VISIBLE || view.getAlpha() == 0f) return null;
    if (view instanceof EmojiSectionView && ((EmojiSectionView) view).getSection().index == index) return view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View found = findEmojiSection(group.getChildAt(i), index);
        if (found != null) return found;
      }
    }
    return null;
  }

  private void pushConfiguration () throws Exception {
    DeviceTokenRetriever missing = new DeviceTokenRetriever("local-fixture") {
      @Override public boolean isAvailable (Context context) { return true; }
      @Override protected boolean performInitialization (Context context) {
        return initializationFailed(ERROR_CONFIGURATION_MISSING, null);
      }
      @Override protected void fetchDeviceToken (Context context, TokenRetrieverListener listener) {
        throw new AssertionError("Uninitialized retriever attempted a network request");
      }
    };
    final String[] failure = new String[1];
    final int[] callbacks = new int[1];
    TokenRetrieverListener listener = new TokenRetrieverListener() {
      @Override public void onTokenRetrievalSuccess (TdApi.DeviceToken token) { throw new AssertionError("Unexpected push token"); }
      @Override public void onTokenRetrievalError (String errorKey, Throwable error) { callbacks[0]++; failure[0] = errorKey; }
    };
    missing.retrieveDeviceToken(getTargetContext(), listener);
    require(callbacks[0] == 1 && DeviceTokenRetriever.ERROR_INITIALIZATION.equals(failure[0]), "Uninitialized retriever did not report its error");
    require(!missing.initialize(getTargetContext()), "Missing configuration initialized successfully");
    missing.retrieveDeviceToken(getTargetContext(), listener);
    require(callbacks[0] == 2 && DeviceTokenRetriever.ERROR_CONFIGURATION_MISSING.equals(failure[0]), "Initialization reason was lost");
    require(!DeviceTokenRetriever.canRetry(failure[0]) && !DeviceTokenRetriever.canRetry(DeviceTokenRetriever.ERROR_SERVICE_UNAVAILABLE), "Permanent push error would be retried");
    require(DeviceTokenRetriever.canRetry("SERVICE_NOT_AVAILABLE"), "Transient Firebase error cannot retry");
    if (getTargetContext().getResources().getIdentifier("google_app_id", "string", getTargetContext().getPackageName()) == 0) {
      CountDownLatch done = new CountDownLatch(1);
      TdlibNotificationUtils.getDeviceToken(getTargetContext(), 3, new TokenRetrieverListener() {
        @Override public void onTokenRetrievalSuccess (TdApi.DeviceToken token) { failure[0] = "unexpected-success"; done.countDown(); }
        @Override public void onTokenRetrievalError (String errorKey, Throwable error) { failure[0] = errorKey; done.countDown(); }
      });
      require(done.await(2, TimeUnit.SECONDS), "Missing configuration entered delayed retries");
      require(DeviceTokenRetriever.ERROR_CONFIGURATION_MISSING.equals(failure[0]), "Missing build configuration was reported as a generic initialization failure");
    }
  }

  private void withReadGate (Check check) throws Exception {
    require(Settings.instance().isExperimentEnabled(Settings.EXPERIMENT_FLAG_ALLOW_EXPERIMENTS), "Enable experiments for the Ghost fixture");
    require((MoexConfig.instance().getGhostModeFlags() & MoexConfig.GHOST_NO_READ) != 0, "Enable Ghost no-read for the fixture");
    boolean before = MoexConfig.ghostMode;
    // Change the in-memory read gate only, without notifying privacy sessions.
    MoexConfig.ghostMode = true;
    try { check.run(); } finally { MoexConfig.ghostMode = before; }
  }

  private void forumCounters () throws Exception {
    GhostReadStore.clearAccount(FIXTURE_ACCOUNT);
    GhostReadStore.clearAccount(FIXTURE_ACCOUNT + 1);
    try {
      withReadGate(() -> {
        TdApi.MessageTopic first = new TdApi.MessageTopicForum(11);
        TdApi.MessageTopic second = new TdApi.MessageTopicForum(22);
        GhostReadStore.Inbox parent = new GhostReadStore.Inbox(0, 18, 90);
        GhostReadStore.record(FIXTURE_ACCOUNT, FIXTURE_CHAT, first, new GhostReadStore.Inbox(10, 7, 70), 70, 7, 0);
        GhostReadStore.record(FIXTURE_ACCOUNT, FIXTURE_CHAT, second, new GhostReadStore.Inbox(20, 5, 90), 40, 2, -1);
        require(GhostReadStore.unreadCount(FIXTURE_ACCOUNT, FIXTURE_CHAT, null, parent) == 9, "Parent badge did not subtract both topics");
        require(GhostReadStore.unreadCount(FIXTURE_ACCOUNT + 1, FIXTURE_CHAT, null, parent) == 18, "Topic state leaked across accounts");
        // Evict only fixture cache entries; the next access must use native LevelDB.
        Field cache = GhostReadStore.class.getDeclaredField("states");
        cache.setAccessible(true);
        ((Map<?, ?>) cache.get(null)).keySet().removeIf(key -> key.toString().startsWith("ghost_read_" + FIXTURE_ACCOUNT + "_"));
        require(GhostReadStore.unreadCount(FIXTURE_ACCOUNT, FIXTURE_CHAT, null, parent) == 9, "Persisted topic counts were lost");
        GhostReadStore.serverRead(FIXTURE_ACCOUNT, FIXTURE_CHAT, first, 70);
        require(GhostReadStore.unreadCount(FIXTURE_ACCOUNT, FIXTURE_CHAT, null, new GhostReadStore.Inbox(0, 11, 90)) == 9, "Server topic read was deducted twice");
        GhostReadStore.clear(FIXTURE_ACCOUNT, FIXTURE_CHAT, null);
        require(GhostReadStore.forumTopicIds(FIXTURE_ACCOUNT, FIXTURE_CHAT).length == 0, "Parent clear left topic markers");
      });
    } finally {
      GhostReadStore.clearAccount(FIXTURE_ACCOUNT);
      GhostReadStore.clearAccount(FIXTURE_ACCOUNT + 1);
    }
  }

  private void ghostContentReads () throws Exception {
    require(tdlib.chat(FIXTURE_CHAT) == null, "Fixture chat identifier must be unused");
    GhostReadStore.clear(tdlib.id(), FIXTURE_CHAT, null);
    GhostReadStore.clearAccount(FIXTURE_ACCOUNT);
    final TGMessage[] parsed = new TGMessage[3];
    final WallpaperView[] wallpaper = new WallpaperView[1];
    final MessageView[] serviceView = new MessageView[1];
    try {
      withReadGate(() -> {
        TdApi.MessageTopic first = new TdApi.MessageTopicForum(11), second = new TdApi.MessageTopicForum(22);
        TdApi.Message pin = message(100, FIXTURE_CHAT);
        pin.content = new TdApi.MessagePinMessage(50);
        pin.containsUnreadMention = true;
        pin.topicId = first;
        TdApi.Message reacted = message(50, FIXTURE_CHAT);
        reacted.isOutgoing = true;
        reacted.topicId = first;
        TdApi.UnreadReaction old = new TdApi.UnreadReaction(new TdApi.ReactionTypeEmoji("❤"), new TdApi.MessageSenderUser(tdlib.myUserId()), false);
        reacted.unreadReactions = new TdApi.UnreadReaction[] {old};
        TdApi.Message title = message(150, FIXTURE_CHAT);
        title.content = new TdApi.MessageChatChangeTitle("Local fixture title");
        title.topicId = first;
        TdApi.Chat chat = new TdApi.Chat();
        chat.id = FIXTURE_CHAT;
        chat.type = new TdApi.ChatTypeBasicGroup(-FIXTURE_CHAT);
        chat.title = "Local ghost fixture";
        chat.positions = new TdApi.ChatPosition[0];
        chat.notificationSettings = new TdApi.ChatNotificationSettings();
        chat.lastMessage = title;
        MessagesController controller = new MessagesController(activity, tdlib) {
          // The service-message render has no live chat viewport or video layer.
          @Override public void checkRoundVideo (int first, int last, boolean onScroll) { }
        };
        attachEmptyAdapter(controller, new RecyclerView(activity));
        wallpaper[0] = new WallpaperView(activity, controller.getManager(), tdlib);
        field(MessagesController.class, "wallpaperView").set(controller, wallpaper[0]);
        TGChat row = new TGChat(controller, new TdApi.ChatListMain(), chat, false);
        row.updateChatUnreadMentionCount(chat.id, 1);
        row.updateChatUnreadReactionCount(chat.id, 1);
        parsed[0] = TGMessage.valueOf(controller.getManager(), pin, chat, null, (TdApi.ChatAdministrator) null);
        parsed[1] = TGMessage.valueOf(controller.getManager(), reacted, chat, null, (TdApi.ChatAdministrator) null);
        parsed[2] = TGMessage.valueOf(controller.getManager(), title, chat, null, (TdApi.ChatAdministrator) null);
        require(row.hasUnreadMentions() && row.hasUnreadReactions(), "Fixture badges were not initially unread");
        require(parsed[0].checkIsUnread(true) && parsed[1].containsUnreadReactions(), "Fixture content was not initially unread");
        require(parsed[2].checkIsUnread(false), "Title service message was not initially unread");
        require(!GhostReadStore.recordViewedContent(tdlib.id(), first, pin, new TdApi.MessageSourceSearch()), "Search preview marked a pin read");
        require(GhostReadStore.recordViewedContent(tdlib.id(), first, pin, new TdApi.MessageSourceForumTopicHistory()), "Pin view did not record its mention");
        require(GhostReadStore.recordViewedContent(tdlib.id(), first, reacted, new TdApi.MessageSourceForumTopicHistory()), "Reaction view was not recorded");
        GhostReadStore.record(tdlib.id(), FIXTURE_CHAT, null, new GhostReadStore.Inbox(0, 2, 150), 150, 2, 0);
        require(!parsed[0].checkIsUnread(true) && !parsed[0].containsUnreadMention(), "Pin stayed unread after viewing");
        require(!parsed[2].checkIsUnread(false), "Title service message stayed unread after viewing");
        require(!parsed[1].containsUnreadReactions(), "Viewed reaction stayed unread in the message UI");
        row.updateLocalReadState();
        require(!row.hasUnreadMentions() && !row.hasUnreadReactions(), "Chat-list badges did not use local content reads");
        require(GhostReadStore.unreadMentionCount(tdlib.id(), FIXTURE_CHAT, first, 1) == 0, "Topic mention badge stayed unread");
        require(GhostReadStore.unreadReactionCount(tdlib.id(), FIXTURE_CHAT, first, 1) == 0, "Topic reaction badge stayed unread");
        require(GhostReadStore.unreadReactionCount(tdlib.id(), FIXTURE_CHAT, second, 1) == 1, "Another topic lost an unread reaction");
        require(GhostReadStore.unreadReactionCount(FIXTURE_ACCOUNT, FIXTURE_CHAT, first, 1) == 1, "Local reaction state leaked to another account");
        require(pin.containsUnreadMention && reacted.unreadReactions.length == 1 && chat.lastReadInboxMessageId == 0,
          "Local reads mutated the server message or inbox");
        ((Map<?, ?>) field(GhostReadStore.class, "contentStates").get(null)).remove("ghost_read_" + tdlib.id() + "_" + FIXTURE_CHAT + "_content");
        require(!GhostReadStore.hasUnreadMention(tdlib.id(), pin) && GhostReadStore.unreadReactions(tdlib.id(), reacted).length == 0,
          "Content read state did not survive a native database reload");
        TdApi.UnreadReaction added = new TdApi.UnreadReaction(new TdApi.ReactionTypeEmoji("👍"), old.senderId, false);
        reacted.unreadReactions = new TdApi.UnreadReaction[] {old, added};
        GhostReadStore.serverContentRead(tdlib.id(), FIXTURE_CHAT, reacted.id, false, reacted.unreadReactions);
        require(parsed[1].containsUnreadReactions() && row.hasUnreadReactions(), "New reaction on an old message was hidden");
        require(GhostReadStore.unreadReactions(tdlib.id(), reacted).length == 1, "Already viewed reactions became unread again");
        GhostReadStore.recordViewedContent(tdlib.id(), first, reacted, new TdApi.MessageSourceForumTopicHistory());
        require(!parsed[1].containsUnreadReactions() && !row.hasUnreadReactions(), "New reaction did not become locally read");
        GhostReadStore.serverContentRead(tdlib.id(), FIXTURE_CHAT, reacted.id, false, new TdApi.UnreadReaction[0]);
        reacted.unreadReactions = new TdApi.UnreadReaction[] {old};
        require(parsed[1].containsUnreadReactions(), "The same reaction stayed hidden after a server read and a new event");
        GhostReadStore.serverContentRead(tdlib.id(), FIXTURE_CHAT, pin.id, true, null);
        require(GhostReadStore.unreadMentionCount(tdlib.id(), FIXTURE_CHAT, first, 0) == 0, "Server acknowledgement double-deducted the mention");
        TdApi.ViewMessages request = GhostReadPolicy.viewRequest(FIXTURE_CHAT, new long[] {pin.id, reacted.id, title.id}, new TdApi.MessageSourceForumTopicHistory(), true, true);
        require(!request.forceRead && request.source instanceof TdApi.MessageSourceOther, "Ghost view could send a read-bearing request");
        parsed[0].buildLayout(720);
        MessageView view = new MessageView(activity);
        serviceView[0] = view;
        view.setManager(controller.getManager());
        view.setParentOnMeasureDisabled(true);
        view.setMessage(parsed[0]);
        FrameLayout stage = new FrameLayout(activity);
        stage.addView(view);
        Bitmap render = renderPoll(stage, parsed[0]);
        require(render.getHeight() > 0, "Pin service message failed to render");
        saveBitmap(render, "ratogramx-smoke-ghost-service.png");
        render.recycle();
      });
    } finally {
      if (serviceView[0] != null) serviceView[0].performDestroy();
      for (TGMessage message : parsed) if (message != null) message.onDestroy();
      if (wallpaper[0] != null) wallpaper[0].performDestroy();
      GhostReadStore.clear(tdlib.id(), FIXTURE_CHAT, null);
      GhostReadStore.clearAccount(FIXTURE_ACCOUNT);
    }
  }

  private void localNotifications () throws Exception {
    require(tdlib.myUserId() != 0, "An authorized development account is required");
    GhostReadStore.clear(tdlib.id(), FIXTURE_CHAT, null);
    try {
      TdApi.Message read = message(100, FIXTURE_CHAT);
      read.topicId = new TdApi.MessageTopicForum(11);
      TdApi.Message unread = message(200, FIXTURE_CHAT);
      unread.topicId = new TdApi.MessageTopicForum(22);
      TdApi.Notification first = new TdApi.Notification(1, 1, true, new TdApi.NotificationTypeNewMessage(read, false));
      TdApi.Notification second = new TdApi.Notification(2, 2, true, new TdApi.NotificationTypeNewMessage(unread, false));
      TdlibNotificationGroup group = new TdlibNotificationGroup(tdlib, new TdApi.NotificationGroup(FIXTURE_ACCOUNT, new TdApi.NotificationGroupTypeMessages(), FIXTURE_CHAT, 2, new TdApi.Notification[] {first, second}));
      require(!group.isHidden(), "Unread notifications started hidden");
      withReadGate(() -> GhostReadStore.record(tdlib.id(), FIXTURE_CHAT, read.topicId, new GhostReadStore.Inbox(0, 1, 100), 100, 1, 0));
      require(new TdlibNotification(tdlib, first, group).isHidden(), "Read topic notification stayed visible");
      require(!new TdlibNotification(tdlib, second, group).isHidden(), "Another topic notification was hidden");
      require(!group.isHidden(), "Partially unread group was hidden");
      GhostReadStore.rememberViewedIds(tdlib.id(), FIXTURE_CHAT, new long[] {100});
      TdApi.Notification push = new TdApi.Notification(3, 3, true, new TdApi.NotificationTypeNewPushMessage(100, new TdApi.MessageSenderUser(tdlib.myUserId()), "Local fixture", false, new TdApi.PushMessageContentText("fixture", false)));
      require(new TdlibNotification(tdlib, push, group).isHidden(), "Delayed push for a viewed topic reappeared");
      TdlibNotificationGroup onlyRead = new TdlibNotificationGroup(tdlib, new TdApi.NotificationGroup(FIXTURE_ACCOUNT + 1, new TdApi.NotificationGroupTypeMessages(), FIXTURE_CHAT, 1, new TdApi.Notification[] {first}));
      require(onlyRead.isHidden(), "Fully read notification group stayed visible");
    } finally { GhostReadStore.clear(tdlib.id(), FIXTURE_CHAT, null); }
  }

  private void notificationIcons () {
    Context context = getTargetContext();
    NotificationManager manager = context.getSystemService(NotificationManager.class);
    manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Local emulator checks", NotificationManager.IMPORTANCE_LOW));
    String original = AppIconManager.getSelectedIconId();
    try {
      Bitmap previous = null;
      for (AppIconManager.Icon selected : AppIconManager.getIcons()) {
        // Exercise the notification cache without changing launcher components.
        Settings.instance().putString("ratogramx_app_icon", selected.id);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL).setContentTitle("Ratogram local check").setContentText("Notification icon fixture").setSilent(true);
        AppIconManager.setNotificationSmallIcon(builder, context);
        Bitmap large = AppIconManager.notificationIcon(context);
        require(large != null && large.getWidth() > 0, "Selected notification icon is empty");
        if (previous != null) require(!previous.sameAs(large), "Notification cache retained the previous colored icon");
        previous = large;
        Notification notification = builder.setLargeIcon(large).build();
        require(notification.getSmallIcon() != null && notification.getSmallIcon().getType() == Icon.TYPE_BITMAP, "Android did not receive a bitmap status icon");
        manager.notify(CHANNEL, FIXTURE_ACCOUNT, notification);
        boolean found = false;
        for (StatusBarNotification item : manager.getActiveNotifications()) {
          if (CHANNEL.equals(item.getTag()) && item.getId() == FIXTURE_ACCOUNT) {
            found = true;
            require(item.getNotification().getLargeIcon() != null, "Android lost the colored fallback");
          }
        }
        require(found, "Android rejected the notification");
      }
    } finally {
      Settings.instance().putString("ratogramx_app_icon", original);
      manager.cancel(CHANNEL, FIXTURE_ACCOUNT);
      manager.deleteNotificationChannel(CHANNEL);
    }
  }

  private void pollRendering () throws Exception {
    require(tdlib.myUserId() != 0, "An authorized development account is required");
    File image = new File(getTargetContext().getCacheDir(), "ratogramx-smoke-photo.png");
    Bitmap source = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
    source.eraseColor(Color.BLUE);
    try (FileOutputStream output = new FileOutputStream(image)) { source.compress(Bitmap.CompressFormat.PNG, 100, output); }
    source.recycle();
    final TGMessagePoll[] parsed = new TGMessagePoll[1];
    final MessageView[] view = new MessageView[1];
    final FrameLayout[] parent = new FrameLayout[1];
    final boolean[] ownsPopup = new boolean[1];
    try {
      onUi(() -> {
        MessagesController controller = new MessagesController(activity, tdlib) {
          // This offscreen message has no RecyclerView thumbnail to animate from.
          @Override public void modifyMediaArguments (Object cause, MediaViewController.Args args) { }
          @Override public void checkRoundVideo (int first, int last, boolean onScroll) { }
        };
        attachEmptyAdapter(controller, new RecyclerView(activity));
        view[0] = new MessageView(activity);
        TdApi.Poll poll = new TdApi.Poll();
        poll.id = FIXTURE_ACCOUNT;
        poll.question = text("Local media poll");
        poll.type = new TdApi.PollTypeRegular();
        poll.isAnonymous = true;
        poll.isClosed = true;
        poll.canSeeResults = true;
        poll.recentVoterIds = new TdApi.MessageSender[0];
        poll.optionOrder = new int[0];
        poll.countryCodes = new String[0];
        TdApi.File file = new TdApi.File();
        // A negative ID keeps the native viewer on its local image path.
        file.id = -FIXTURE_ACCOUNT;
        file.local = new TdApi.LocalFile();
        file.local.path = image.getAbsolutePath();
        file.local.isDownloadingCompleted = true;
        file.remote = new TdApi.RemoteFile();
        TdApi.Photo photo = new TdApi.Photo(false, null, new TdApi.PhotoSize[] {new TdApi.PhotoSize("x", file, 320, 180, new int[0])});
        poll.options = new TdApi.PollOption[] {
          option("photo", "Photo answer", new TdApi.PollMediaPhoto(photo, null)),
          option("link", "Link answer", new TdApi.PollMediaLink("https://example.com", null)),
          option("map", "Location answer", new TdApi.PollMediaLocation(new TdApi.Location(55, 37, 0)))
        };
        TdApi.Message message = message(1000, tdlib.myUserId());
        message.content = new TdApi.MessagePoll(poll, text("Description below the question"), null, false);
        parsed[0] = (TGMessagePoll) TGMessage.valueOf(controller.getManager(), message);
        parsed[0].buildLayout(720);
        require(parsed[0].getHeight() > 400 && parsed[0].getHeight() < 4000, "Poll media layout has invalid height");
        parent[0] = new FrameLayout(activity);
        parent[0].addView(view[0]);
        view[0].setManager(controller.getManager());
        view[0].setParentOnMeasureDisabled(true);
        view[0].setUseComplexReceiver();
        view[0].setMessage(parsed[0]);
        activity.addToRoot(parent[0], false);
        view[0].getComplexReceiver().attach();
      });
      final boolean[] photoVisible = new boolean[1];
      long deadline = SystemClock.uptimeMillis() + 10_000;
      while (!photoVisible[0] && SystemClock.uptimeMillis() < deadline) {
        SystemClock.sleep(200);
        onUi(() -> {
          Bitmap render = renderPoll(parent[0], parsed[0]);
          for (int y = 0; y < render.getHeight(); y += 4) for (int x = 0; x < render.getWidth(); x += 4) {
            if (render.getPixel(x, y) == Color.BLUE) photoVisible[0] = true;
          }
          saveBitmap(render, "ratogramx-smoke-poll.png");
          render.recycle();
        });
      }
      require(photoVisible[0], "Poll option photo did not load on Android");
      onUi(() -> {
        Object[] options = (Object[]) field(TGMessagePoll.class, "options").get(parsed[0]);
        Object media = field(options[0].getClass(), "media").get(options[0]);
        MediaWrapper wrapper = (MediaWrapper) field(media.getClass(), "wrapper").get(media);
        require(activity.getCurrentPopupWindow() == null, "Close other popups before the media fixture");
        ownsPopup[0] = true;
        activity.removeFromRoot(parent[0]);
        require(parsed[0].onClick(view[0], wrapper), "Poll photo click was ignored");
        PopupLayout popup = activity.getCurrentPopupWindow();
        require(popup != null && popup.getBoundController() instanceof MediaViewController, "Poll photo did not open the native viewer");
        require(!((MediaViewController) popup.getBoundController()).shouldDisallowScreenshots(), "Local poll fixture was unexpectedly protected");
      });
      boolean viewerPhotoVisible = false;
      awaitFrame();
      deadline = SystemClock.uptimeMillis() + 10_000;
      while (!viewerPhotoVisible && SystemClock.uptimeMillis() < deadline) {
        SystemClock.sleep(200);
        Bitmap viewerScreenshot = getUiAutomation().takeScreenshot();
        require(viewerScreenshot != null, "Cannot capture poll media viewer");
        for (int y = 0; y < viewerScreenshot.getHeight(); y += 4) for (int x = 0; x < viewerScreenshot.getWidth(); x += 4) {
          if (viewerScreenshot.getPixel(x, y) == Color.BLUE) viewerPhotoVisible = true;
        }
        saveBitmap(viewerScreenshot, "ratogramx-smoke-poll-viewer.png");
        viewerScreenshot.recycle();
      }
      if (!viewerPhotoVisible) onUi(() -> {
        PopupLayout popup = activity.getCurrentPopupWindow();
        View content = popup.getBoundView();
        View mediaView = (View) field(MediaViewController.class, "mediaView").get(popup.getBoundController());
        require(false, "The native viewer did not display the poll photo: content=" + content.getWidth() + "x" + content.getHeight() +
          ", alpha=" + mediaView.getAlpha() + ", windowFlags=" + activity.getWindow().getAttributes().flags);
      });
      onUi(() -> activity.getCurrentPopupWindow().hideWindow(false));
      waitForIdleSync();
      onUi(() -> {
        TdApi.Poll old = ((TdApi.MessagePoll) parsed[0].getMessage().content).poll;
        TdApi.Poll updated = new TdApi.Poll(old.id, old.question, old.options.clone(), old.totalVoterCount, old.recentVoterIds, old.canGetVoters, old.canSeeResults, old.isAnonymous, old.allowsMultipleAnswers, old.allowsRevoting, old.membersOnly, old.countryCodes, new int[] {2, 0, 1}, old.type, old.openPeriod, old.closeDate, old.isClosed, old.voteRestrictionReason);
        updated.options[0] = option("photo", "Photo removed", null);
        int before = parsed[0].getHeight();
        parsed[0].replaceMessageContent(parsed[0].getChatId(), parsed[0].getId(), new TdApi.MessagePoll(updated, text("Updated description"), null, false));
        parsed[0].buildLayout(720);
        require(parsed[0].getHeight() < before - 100, "Media-only update kept the old option height");
        Field order = TGMessagePoll.class.getDeclaredField("displayOrder");
        order.setAccessible(true);
        require(Arrays.equals((int[]) order.get(parsed[0]), updated.optionOrder), "Poll option order was not updated");
        view[0].setMessage(parsed[0]);
        Bitmap render = renderPoll(parent[0], parsed[0]);
        saveBitmap(render, "ratogramx-smoke-poll-updated.png");
        render.recycle();
      });
    } finally {
      onUi(() -> {
        PopupLayout popup = activity.getCurrentPopupWindow();
        if (ownsPopup[0] && popup != null && popup.getBoundController() instanceof MediaViewController) popup.hideWindow(false);
        if (parent[0] != null) activity.removeFromRoot(parent[0]);
        if (view[0] != null) view[0].performDestroy();
        if (parsed[0] != null) parsed[0].onDestroy();
      });
      if (!image.delete()) image.deleteOnExit();
    }
  }

  private Bitmap renderPoll (FrameLayout parent, TGMessage parsed) {
    parent.measure(exact(720), exact(parsed.getHeight()));
    parent.layout(0, 0, 720, parsed.getHeight());
    Bitmap render = Bitmap.createBitmap(720, parsed.getHeight(), Bitmap.Config.ARGB_8888);
    parent.draw(new Canvas(render));
    return render;
  }

  private void attachEmptyAdapter (MessagesController controller, RecyclerView recycler) {
    LinearLayoutManager layout = new LinearLayoutManager(activity);
    recycler.setLayoutManager(layout);
    controller.getManager().modifyRecycler(activity, recycler, layout);
  }

  private void saveBitmap (Bitmap bitmap, String name) throws Exception {
    try (FileOutputStream output = new FileOutputStream(new File(getTargetContext().getCacheDir(), name))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); }
  }

  private void awaitFrame () throws Exception {
    CountDownLatch committed = new CountDownLatch(1);
    onUi(() -> {
      View decor = activity.getWindow().getDecorView();
      decor.getViewTreeObserver().registerFrameCommitCallback(committed::countDown);
      decor.invalidate();
    });
    require(committed.await(10, TimeUnit.SECONDS), "Android did not commit the fixture frame");
  }

  private static Field field (Class<?> owner, String name) throws Exception {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  private static Method method (Class<?> owner, String name, Class<?>... arguments) throws Exception {
    Method method = owner.getDeclaredMethod(name, arguments);
    method.setAccessible(true);
    return method;
  }

  private final class VideoFixtureController extends MessagesController {
    final FrameLayout root = new FrameLayout(activity);
    final FrameLayout messageLayer = new FrameLayout(activity);
    final MessagesRecyclerView messages = new MessagesRecyclerView(activity) {
      @Override protected void onLayout (boolean changed, int left, int top, int right, int bottom) { }
    };
    final LinearLayout composer = new LinearLayout(activity);
    final EditText input = new EditText(activity);
    int panelHeight, imeOffset;

    VideoFixtureController () {
      super(activity, EmulatorSmokeInstrumentation.this.tdlib);
      TdApi.Chat chat = new TdApi.Chat();
      chat.id = tdlib.myUserId();
      chat.type = new TdApi.ChatTypePrivate(tdlib.myUserId());
      setArguments(new Arguments(PREVIEW_MODE_FONT_SIZE, null, chat));
      attachEmptyAdapter(this, messages);
      // Only the viewport geometry is needed; no live message adapter is attached.
      while (messages.getItemDecorationCount() > 0) messages.removeItemDecorationAt(0);
      root.setBackgroundColor(Color.DKGRAY);
      composer.setBackgroundColor(Color.DKGRAY);
      input.setHint("Local video fixture");
      composer.addView(input, new LinearLayout.LayoutParams(-1, 96));
      root.addView(messages, new FrameLayout.LayoutParams(-1, -1));
      root.addView(messageLayer, new FrameLayout.LayoutParams(-1, -1));
      root.addView(composer, new FrameLayout.LayoutParams(-1, 96, Gravity.BOTTOM));
    }

    void updateKeyboardBounds () {
      int ime = root.getRootWindowInsets().getInsets(WindowInsets.Type.ime()).bottom;
      int keyboardTop = activity.getWindow().getDecorView().getHeight() - ime;
      int[] position = new int[2];
      root.getLocationInWindow(position);
      // Respect a resized parent as well as an overlay that extends behind the IME.
      updateBounds(Math.max(0, position[1] + root.getHeight() - keyboardTop));
    }

    void updateBounds (int ime) {
      imeOffset = ime;
      FrameLayout.LayoutParams inputParams = (FrameLayout.LayoutParams) composer.getLayoutParams();
      inputParams.bottomMargin = Math.max(ime, panelHeight);
      composer.setLayoutParams(inputParams);
      FrameLayout.LayoutParams messagesParams = (FrameLayout.LayoutParams) messages.getLayoutParams();
      // Match the real chat's ABOVE-composer viewport without a second bottom inset.
      messagesParams.height = Math.max(0, root.getHeight() - inputParams.bottomMargin - inputParams.height);
      messages.setLayoutParams(messagesParams);
      FrameLayout.LayoutParams layerParams = (FrameLayout.LayoutParams) messageLayer.getLayoutParams();
      layerParams.height = messagesParams.height;
      messageLayer.setLayoutParams(layerParams);
    }

    @Override public boolean isFocused () { return true; }
    @Override public void checkRoundVideo (int first, int last, boolean onScroll) { }
    @Override public int getTopOffset () { return 0; }
    @Override public int getInputOffset (boolean excludeTranslation) { return composer.getHeight(); }
    @Override public LinearLayout getBottomWrap () { return composer; }
    @Override public MessagesRecyclerView getMessagesView () { return messages; }
  }

  private void roundVideo () throws Exception {
    File movie = new File(getTargetContext().getCacheDir(), "ratogramx-smoke-round.mp4");
    try (InputStream input = getContext().getAssets().open("round-smoke.mp4"); FileOutputStream output = new FileOutputStream(movie)) {
      byte[] buffer = new byte[8192];
      int count;
      while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    }
    RoundVideoController player = activity.getRoundVideoController();
    final VideoFixtureController[] controller = new VideoFixtureController[1];
    final MessageViewGroup[] group = new MessageViewGroup[1];
    final TGMessage[] parsed = new TGMessage[1];
    final ExoPlayer[] exo = new ExoPlayer[1];
    Class<?> base = RoundVideoController.class.getSuperclass();
    boolean[] prepared = new boolean[1];
    try {
      onUi(() -> {
        require(field(base, "object").get(player) == null && player.getRootView() == null, "Finish current playback before the fixture");
        controller[0] = new VideoFixtureController();
        FrameLayout.LayoutParams chatPosition = new FrameLayout.LayoutParams(-1, -1);
        chatPosition.topMargin = HeaderView.getSize(true);
        controller[0].root.setLayoutParams(chatPosition);
        activity.addToNavigation(controller[0].root);
        TdApi.File file = new TdApi.File();
        file.id = FIXTURE_ACCOUNT;
        file.local = new TdApi.LocalFile();
        file.local.path = movie.getAbsolutePath();
        file.local.isDownloadingCompleted = true;
        file.remote = new TdApi.RemoteFile();
        TdApi.Message message = message(2000, tdlib.myUserId());
        message.content = new TdApi.MessageVideoNote(new TdApi.VideoNote(5, new byte[0], 320, null, null, null, file), false, false);
        parsed[0] = TGMessage.valueOf(controller[0].getManager(), message);
        MessageView view = new MessageView(activity);
        view.setManager(controller[0].getManager());
        view.setUseReceivers();
        group[0] = new MessageViewGroup(activity);
        group[0].initWithView(view, controller[0].getManager(), null);
        view.setMessage(parsed[0]);
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(-1, -2);
        position.topMargin = 500;
        controller[0].messageLayer.addView(group[0], position);
        method(RoundVideoController.class, "preparePlayerIfNeeded").invoke(player);
        prepared[0] = true;
        field(base, "object").set(player, message);
        field(base, "tdlib").set(player, tdlib);
        field(RoundVideoController.class, "targetView").set(player, group[0]);
        field(RoundVideoController.class, "targetController").set(player, controller[0]);
        View main = (View) field(RoundVideoController.class, "mainPlayerView").get(player);
        main.setAlpha(1f);
        exo[0] = (ExoPlayer) field(RoundVideoController.class, "exoPlayer").get(player);
        exo[0].setMediaSource(U.newMediaSource(movie));
        exo[0].setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ONE);
        exo[0].prepare();
        exo[0].setPlayWhenReady(true);
      });
      SystemClock.sleep(1500);
      onUi(() -> method(RoundVideoController.class, "layoutPlayer").invoke(player));
      boolean[] rendered = new boolean[1];
      long deadline = SystemClock.uptimeMillis() + 15_000;
      while (!rendered[0] && SystemClock.uptimeMillis() < deadline) {
        SystemClock.sleep(200);
        onUi(() -> {
          require(exo[0].getPlayerError() == null, "Local round-video decoder failed");
          TextureView texture = (TextureView) field(RoundVideoController.class, "mainTextureView").get(player);
          Bitmap frame = texture.isAvailable() ? texture.getBitmap(64, 64) : null;
          if (frame != null) {
            for (int y = 0; y < 64; y += 4) for (int x = 0; x < 64; x += 4) if ((frame.getPixel(x, y) & 0xffffff) != 0) rendered[0] = true;
            frame.recycle();
          }
        });
      }
      require(rendered[0], "Round-video decoder did not render a frame");
      onUi(() -> positionVideoAtComposer(controller[0], group[0]));
      SystemClock.sleep(300);
      onUi(() -> {
        method(RoundVideoController.class, "layoutPlayer").invoke(player);
        assertComposerClip(player, controller[0]);
      });
      awaitFrame();
      Bitmap videoScreenshot = getUiAutomation().takeScreenshot();
      require(videoScreenshot != null, "Cannot capture video fixture");
      saveBitmap(videoScreenshot, "ratogramx-smoke-round.png");
      videoScreenshot.recycle();
      onUi(() -> {
        controller[0].input.requestFocus();
        activity.getSystemService(InputMethodManager.class).showSoftInput(controller[0].input, InputMethodManager.SHOW_IMPLICIT);
      });
      boolean[] keyboard = new boolean[1];
      deadline = SystemClock.uptimeMillis() + 8000;
      while (!keyboard[0] && SystemClock.uptimeMillis() < deadline) {
        SystemClock.sleep(200);
        onUi(() -> keyboard[0] = activity.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.ime()));
      }
      require(keyboard[0], "Android keyboard did not open for the playback check");
      SystemClock.sleep(600);
      onUi(() -> controller[0].updateKeyboardBounds());
      SystemClock.sleep(300);
      onUi(() -> positionVideoAtComposer(controller[0], group[0]));
      SystemClock.sleep(300);
      onUi(() -> {
        int ime = activity.getWindow().getDecorView().getRootWindowInsets().getInsets(WindowInsets.Type.ime()).bottom;
        int[] composerPosition = new int[2];
        controller[0].composer.getLocationInWindow(composerPosition);
        require(ime > 0 && composerPosition[1] + controller[0].composer.getHeight() <= activity.getWindow().getDecorView().getHeight() - ime,
          "Fixture composer did not settle above the Android keyboard");
        method(RoundVideoController.class, "layoutPlayer").invoke(player);
        assertComposerClip(player, controller[0]);
      });
      awaitFrame();
      onUi(() -> require(activity.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.ime()), "Android keyboard closed before capture"));
      Bitmap keyboardScreenshot = getUiAutomation().takeScreenshot();
      require(keyboardScreenshot != null, "Cannot capture keyboard fixture");
      saveBitmap(keyboardScreenshot, "ratogramx-smoke-round-keyboard.png");
      keyboardScreenshot.recycle();
      onUi(() -> {
        activity.getSystemService(InputMethodManager.class).hideSoftInputFromWindow(controller[0].input.getWindowToken(), 0);
      });
      SystemClock.sleep(800);
      onUi(() -> {
        controller[0].panelHeight = 450;
        controller[0].updateBounds(0);
      });
      SystemClock.sleep(400);
      onUi(() -> positionVideoAtComposer(controller[0], group[0]));
      SystemClock.sleep(300);
      onUi(() -> {
        method(RoundVideoController.class, "layoutPlayer").invoke(player);
        assertComposerClip(player, controller[0]);
      });
    } finally {
      onUi(() -> {
        if (controller[0] != null) {
          activity.getSystemService(InputMethodManager.class).hideSoftInputFromWindow(controller[0].input.getWindowToken(), 0);
        }
        if (prepared[0]) {
          field(base, "object").set(player, null);
          field(RoundVideoController.class, "targetView").set(player, null);
          field(RoundVideoController.class, "targetController").set(player, null);
          method(RoundVideoController.class, "closePlayer", int.class).invoke(player, 0);
        }
        if (controller[0] != null) activity.removeFromNavigation(controller[0].root);
        if (group[0] != null) group[0].performDestroy();
        if (parsed[0] != null) parsed[0].onDestroy();
      });
      if (!movie.delete()) movie.deleteOnExit();
    }
  }

  private void assertComposerClip (RoundVideoController player, VideoFixtureController controller) throws Exception {
    RectFrameLayout clip = player.getRootView();
    int[] rootPosition = new int[2], composerPosition = new int[2];
    clip.getLocationInWindow(rootPosition);
    controller.composer.getLocationInWindow(composerPosition);
    int bottom = field(RectFrameLayout.class, "bottom").getInt(clip);
    int top = field(RectFrameLayout.class, "top").getInt(clip);
    require(rootPosition[1] + clip.getHeight() - bottom <= composerPosition[1], "Round-video pixels can overlap the composer");
    require(rootPosition[1] + clip.getHeight() - bottom >= composerPosition[1] - 1, "Round video was clipped above the composer edge");
    require(top + bottom < clip.getHeight(), "Visible round-video region became empty");
    View main = (View) field(RoundVideoController.class, "mainPlayerView").get(player);
    int[] videoPosition = new int[2];
    main.getLocationInWindow(videoPosition);
    require(videoPosition[1] < composerPosition[1] && videoPosition[1] + main.getHeight() > composerPosition[1], "Video fixture did not cross the composer edge");
  }

  private void positionVideoAtComposer (VideoFixtureController controller, MessageViewGroup group) {
    controller.updateBounds(controller.imeOffset);
    FrameLayout.LayoutParams position = (FrameLayout.LayoutParams) group.getLayoutParams();
    position.topMargin = Math.max(0, controller.composer.getTop() - group.getMessage().getContentY() - group.getHeight() / 2);
    group.setLayoutParams(position);
  }

  private TdApi.Message message (long id, long chatId) {
    TdApi.Message message = new TdApi.Message();
    message.id = id;
    message.chatId = chatId;
    message.senderId = new TdApi.MessageSenderUser(tdlib.myUserId());
    message.date = (int) (System.currentTimeMillis() / 1000);
    message.authorSignature = "";
    message.canBeSaved = true;
    message.content = new TdApi.MessageText(text("Local fixture"), null, null);
    return message;
  }

  private static TdApi.FormattedText text (String value) { return new TdApi.FormattedText(value, new TdApi.TextEntity[0]); }

  private static TdApi.PollOption option (String id, String label, TdApi.PollMedia media) {
    TdApi.PollOption option = new TdApi.PollOption();
    option.id = id;
    option.text = text(label);
    option.media = media;
    option.recentVoterIds = new TdApi.MessageSender[0];
    return option;
  }
}
