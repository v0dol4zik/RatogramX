package moe.kirao.mgx.utils;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

import tgx.td.Td;

/** Cloud source messages with bounded, account-owned, disposable rendering data. */
public final class SavedRichMessages {
  public interface Callback<T> { void onResult (@Nullable T value, @Nullable TdApi.Error error); }
  private static final int MAX_CACHE = 16, MAX_CACHED_SOURCE = 262144, MAX_PENDING = 128, MAX_ACTIVE = 4;
  private final Tdlib tdlib;
  private final Map<String, RichMessageDocument> cache = new LinkedHashMap<>(16, .75f, true);
  private final Map<String, List<Callback<RichMessageDocument>>> pending = new LinkedHashMap<>();
  private final ArrayDeque<Runnable> queued = new ArrayDeque<>();
  private final Set<String> saves = new HashSet<>();
  private final Set<MessageListener> sending = new HashSet<>();
  private int cachedSource, active;

  public SavedRichMessages (Tdlib tdlib) { this.tdlib = tdlib; }

  private static TdApi.Error invalid () { return new TdApi.Error(400, "RICH_MESSAGE_SOURCE_UNAVAILABLE"); }

  private String key (TdApi.Message message) {
    String source = message.content instanceof TdApi.MessageText ? ((TdApi.MessageText) message.content).text.text :
      ((TdApi.MessageDocument) message.content).document.document.id + ":" + ((TdApi.MessageDocument) message.content).document.document.remote.id;
    return tdlib.myUserId() + ":" + message.id + ":" + message.editDate + ":" + SavedRichMessageCodec.isRtl(message.content) + ":" + source;
  }

  public void load (TdApi.Message message, Callback<RichMessageDocument> callback) {
    if (!SavedRichMessageCodec.recognizes(message, tdlib.myUserId())) { UI.post(() -> callback.onResult(null, invalid())); return; }
    TdApi.Message snapshot = Td.copyOf(message);
    String key = key(snapshot);
    Runnable request = () -> Background.instance().post(() -> loadSource(key, snapshot));
    synchronized (this) {
      RichMessageDocument cached = cache.get(key);
      if (cached != null) { UI.post(() -> callback.onResult(cached, null)); return; }
      List<Callback<RichMessageDocument>> waiting = pending.get(key);
      if (waiting != null) { waiting.add(callback); return; }
      if (pending.size() >= MAX_PENDING) { UI.post(() -> callback.onResult(null, invalid())); return; }
      waiting = new ArrayList<>(); waiting.add(callback); pending.put(key, waiting);
      if (active >= MAX_ACTIVE) { queued.add(request); return; }
      active++;
    }
    request.run();
  }

  private void loadSource (String key, TdApi.Message message) {
    if (!SavedRichMessageCodec.recognizes(message, tdlib.myUserId())) { complete(key, null, invalid()); return; }
    long userId = tdlib.myUserId();
    if (message.content instanceof TdApi.MessageText) {
      prepareSource(key, userId, ((TdApi.MessageText) message.content).text.text, SavedRichMessageCodec.isRtl(message.content));
    } else {
      TdApi.MessageDocument content = (TdApi.MessageDocument) message.content;
      boolean rtl = SavedRichMessageCodec.isRtl(content);
      TdApi.File file = content.document.document;
      if (file.local.isDownloadingCompleted && new File(file.local.path).isFile()) readSource(key, userId, file, rtl);
      else if (file.local.isDownloadingCompleted) {
        tdlib.send(new TdApi.DeleteFile(file.id), (ok, error) -> {
          if (error != null) complete(key, null, error);
          else downloadSource(key, userId, file.id, rtl);
        });
      } else downloadSource(key, userId, file.id, rtl);
    }
  }

  private void downloadSource (String key, long userId, int fileId, boolean rtl) {
    tdlib.send(new TdApi.DownloadFile(fileId, 16, 0, SavedRichMessageCodec.MAX_FILE_BYTES + 1, true), (downloaded, error) -> {
      if (error != null || !downloaded.local.isDownloadingCompleted) complete(key, null, error != null ? error : invalid());
      else readSource(key, userId, downloaded, rtl);
    });
  }

  private void readSource (String key, long userId, TdApi.File file, boolean rtl) {
    Background.instance().post(() -> {
      try {
        File path = new File(file.local.path);
        if (!path.isFile() || path.length() > SavedRichMessageCodec.MAX_FILE_BYTES) throw new IllegalArgumentException();
        StringBuilder source = new StringBuilder();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))) {
          char[] buffer = new char[4096]; int count;
          while ((count = reader.read(buffer)) != -1) {
            if (source.length() + count > RichMessageMarkdown.MAX_SOURCE) throw new IllegalArgumentException();
            source.append(buffer, 0, count);
          }
        }
        prepareSource(key, userId, source.toString(), rtl);
      } catch (Exception e) { complete(key, null, invalid()); }
    });
  }

  private void prepareSource (String key, long userId, String text, boolean rtl) {
    Background.instance().post(() -> {
      try {
        RichMessageMarkdown.Source source = new RichMessageMarkdown.Source(text);
        loadPhotos(key, userId, source, rtl, new ArrayList<>(SavedRichMessageCodec.photoNames(text)), 0);
      } catch (IllegalArgumentException e) { complete(key, null, invalid()); }
    });
  }

  private void loadPhotos (String key, long userId, RichMessageMarkdown.Source source, boolean rtl, List<String> names, int index) {
    if (tdlib.myUserId() != userId) { complete(key, null, invalid()); return; }
    if (index == names.size()) {
      Background.instance().post(() -> {
        try { complete(key, RichMessageMarkdown.parse(source.text, source, rtl), null); }
        catch (IllegalArgumentException e) { complete(key, null, invalid()); }
      });
      return;
    }
    String name = names.get(index);
    long id;
    try { id = SavedRichMessageCodec.photoMessageId(name); }
    catch (IllegalArgumentException e) { complete(key, null, invalid()); return; }
    tdlib.send(new TdApi.GetMessage(userId, id), (message, error) -> {
      if (error != null || message.chatId != userId || !(message.content instanceof TdApi.MessagePhoto)) { complete(key, null, invalid()); return; }
      TdApi.MessagePhoto photo = (TdApi.MessagePhoto) message.content;
      RichMessageDocument document = RichMessageDocument.fromRichMessage(new TdApi.RichMessage(new TdApi.PageBlock[] {
        new TdApi.PageBlockPhoto(photo.photo, new TdApi.PageBlockCaption(new TdApi.RichTextPlain(""), null), "", photo.hasSpoiler)
      }, false, true));
      RichMessageDocument.Block block = document.blocks.get(0); block.extra = name;
      source.photos.put(name, block);
      loadPhotos(key, userId, source, rtl, names, index + 1);
    });
  }

  private void complete (String key, @Nullable RichMessageDocument document, @Nullable TdApi.Error error) {
    List<Callback<RichMessageDocument>> callbacks;
    Runnable next;
    synchronized (this) {
      callbacks = pending.remove(key);
      if (callbacks == null) return;
      if (document != null) {
        RichMessageDocument previous = cache.put(key, document);
        if (previous != null) cachedSource -= previous.markdownSource.length();
        cachedSource += document.markdownSource.length();
        Iterator<RichMessageDocument> iterator = cache.values().iterator();
        while (cache.size() > MAX_CACHE || cachedSource > MAX_CACHED_SOURCE) {
          cachedSource -= iterator.next().markdownSource.length(); iterator.remove();
        }
      }
      next = queued.poll();
      if (next == null) active--;
    }
    UI.post(() -> { for (Callback<RichMessageDocument> callback : callbacks) callback.onResult(document, error); });
    if (next != null) next.run();
  }

  public void save (RichMessageDocument document, @Nullable TdApi.Message previous, @Nullable LocalRichMessageStore.Entry legacy, Callback<TdApi.Message> callback) {
    long userId = tdlib.myUserId();
    if (userId == 0 || previous != null && !RichMessagePolicy.isLocalId(previous.id) && !SavedRichMessageCodec.recognizes(previous, userId)) {
      UI.post(() -> callback.onResult(null, invalid())); return;
    }
    String operation = userId + ":" + (legacy != null ? legacy.id : previous != null ? previous.id : 0);
    synchronized (this) {
      if (!saves.add(operation)) { UI.post(() -> callback.onResult(null, invalid())); return; }
    }
    Callback<TdApi.Message> finish = (message, error) -> {
      synchronized (this) { saves.remove(operation); }
      callback.onResult(message, error);
    };
    if (legacy != null) {
      new LocalRichMessageStore(tdlib.id(), userId).syncedMessage(legacy.id, (messageId, error) -> {
        if (error != null) { finish.onResult(null, invalid()); return; }
        if (messageId == 0) beginSave(document, previous, legacy, userId, finish);
        else tdlib.send(new TdApi.GetMessage(userId, messageId), (message, getError) -> {
          if (getError != null && getError.code != 404) { UI.post(() -> finish.onResult(null, getError)); return; }
          beginSave(document, message, legacy, userId, finish);
        });
      });
    } else beginSave(document, previous, null, userId, finish);
  }

  private void beginSave (RichMessageDocument document, @Nullable TdApi.Message previous, @Nullable LocalRichMessageStore.Entry legacy, long userId, Callback<TdApi.Message> callback) {
    if (tdlib.myUserId() != userId) { UI.post(() -> callback.onResult(null, invalid())); return; }
    if (previous != null && !RichMessagePolicy.isLocalId(previous.id)) {
      if (!SavedRichMessageCodec.recognizes(previous, userId) || previous.sendingState != null) { UI.post(() -> callback.onResult(null, invalid())); return; }
      tdlib.send(new TdApi.GetMessageProperties(userId, previous.id), (properties, error) -> {
        if (error != null || !properties.canBeEdited) { UI.post(() -> callback.onResult(null, error != null ? error : invalid())); return; }
        prepareSave(document, previous, legacy, userId, callback);
      });
    } else prepareSave(document, previous, legacy, userId, callback);
  }

  private void prepareSave (RichMessageDocument document, @Nullable TdApi.Message previous, @Nullable LocalRichMessageStore.Entry legacy, long userId, Callback<TdApi.Message> callback) {
    RichMessageDocument copy = document.copy();
    Background.instance().post(() -> {
      try {
        copy.validate();
        RichMessageMarkdown.Source source = RichMessageMarkdown.source(copy);
        publishPhotos(userId, copy, source, new ArrayList<>(SavedRichMessageCodec.photoNames(source.text)), 0,
          new LinkedHashMap<>(), previous != null && !RichMessagePolicy.isLocalId(previous.id) ? previous : null, legacy, callback);
      } catch (IllegalArgumentException e) { UI.post(() -> callback.onResult(null, invalid())); }
    });
  }

  private void publishPhotos (long userId, RichMessageDocument document, RichMessageMarkdown.Source source, List<String> names, int index,
                              Map<String, String> replacements, @Nullable TdApi.Message previous, @Nullable LocalRichMessageStore.Entry legacy, Callback<TdApi.Message> callback) {
    if (tdlib.myUserId() != userId) { UI.post(() -> callback.onResult(null, invalid())); return; }
    if (index == names.size()) {
      Background.instance().post(() -> {
        try { publishSource(userId, SavedRichMessageCodec.replacePhotos(source.text, replacements), document.isRtl, previous, legacy, callback); }
        catch (Exception e) { UI.post(() -> callback.onResult(null, invalid())); }
      });
      return;
    }
    String name = names.get(index);
    RichMessageDocument.Block block = source.photos.get(name);
    if (block == null) { UI.post(() -> callback.onResult(null, invalid())); return; }
    if (block.photoPath.length() == 0) {
      try { SavedRichMessageCodec.photoMessageId(name); }
      catch (IllegalArgumentException e) { UI.post(() -> callback.onResult(null, invalid())); return; }
      publishPhotos(userId, document, source, names, index + 1, replacements, previous, legacy, callback);
    } else {
      TdApi.InputPhoto photo = new TdApi.InputPhoto(new TdApi.InputFileLocal(block.photoPath), null, null, new int[0], block.width, block.height);
      sendConfirmed(userId, new TdApi.InputMessagePhoto(photo, new TdApi.FormattedText(Lang.getString(R.string.RichMessageSavedPhoto) + "\n" + SavedRichMessageCodec.excerpt(source.text, 128), null), false, null, false), (message, error) -> {
        if (error != null) { UI.post(() -> callback.onResult(null, error)); return; }
        replacements.put(name, SavedRichMessageCodec.photoName(message.id));
        publishPhotos(userId, document, source, names, index + 1, replacements, previous, legacy, callback);
      });
    }
  }

  private void publishSource (long userId, String source, boolean rtl, @Nullable TdApi.Message previous,
                              @Nullable LocalRichMessageStore.Entry legacy, Callback<TdApi.Message> callback) throws Exception {
    if (userId != tdlib.myUserId()) { UI.post(() -> callback.onResult(null, invalid())); return; }
    boolean file = source.length() > (tdlib.maxMessageTextLength() > 0 ? tdlib.maxMessageTextLength() : 4096) || previous != null && previous.content instanceof TdApi.MessageDocument;
    TdApi.InputMessageContent content;
    if (file) {
      File directory = new File(org.thunderdog.challegram.unsorted.AppContext.get().getCacheDir(), "rich-message-exports");
      if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException();
      File temporary = new File(directory, SavedRichMessageCodec.FILE_PREFIX + UUID.randomUUID() + ".md");
      try (FileOutputStream out = new FileOutputStream(temporary)) { out.write(source.getBytes(StandardCharsets.UTF_8)); }
      content = new TdApi.InputMessageDocument(new TdApi.InputDocument(new TdApi.InputFileLocal(temporary.getAbsolutePath()), null, true),
        SavedRichMessageCodec.marked(SavedRichMessageCodec.excerpt(source, 512), rtl));
    } else {
      content = new TdApi.InputMessageText(SavedRichMessageCodec.marked(source, rtl), new TdApi.LinkPreviewOptions(true, "", false, false, false), false);
    }
    Callback<TdApi.Message> finish = (message, error) -> {
      // TDLib can retain the export path as the uploaded file's local copy.
      if (error != null) { UI.post(() -> callback.onResult(null, error)); return; }
      if (legacy != null) {
        LocalRichMessageStore store = new LocalRichMessageStore(tdlib.id(), userId);
        store.recordSyncedMessage(legacy.id, message.id, (recorded, recordError) -> {
          if (recordError != null) { callback.onResult(null, invalid()); return; }
          store.delete(legacy.id, (removed, cleanupError) -> {
            if (cleanupError != null) UI.showToast(R.string.RichMessageSyncCleanupError, android.widget.Toast.LENGTH_LONG);
            callback.onResult(message, null);
          });
        });
      } else UI.post(() -> callback.onResult(message, null));
    };
    if (previous == null) sendConfirmed(userId, content, finish);
    else if (previous.content instanceof TdApi.MessageText && file) {
      // Telegram cannot turn a text message into a document in place.
      sendConfirmed(userId, content, (message, error) -> {
        if (error != null) { finish.onResult(null, error); return; }
        tdlib.send(new TdApi.DeleteMessages(userId, new long[] {previous.id}, true), (ok, deleteError) -> {
          if (deleteError != null) UI.post(() -> UI.showToast(R.string.RichMessageReplaceCleanupError, android.widget.Toast.LENGTH_LONG));
          finish.onResult(message, null);
        });
      });
    } else {
      TdApi.Function<TdApi.Message> edit = file ? new TdApi.EditMessageMedia(userId, previous.id, null, content) : new TdApi.EditMessageText(userId, previous.id, null, content);
      tdlib.send(edit, (message, error) -> {
        if (error != null && "MESSAGE_NOT_MODIFIED".equals(error.message)) finish.onResult(previous, null);
        else finish.onResult(message, error);
      });
    }
  }

  private void sendConfirmed (long userId, TdApi.InputMessageContent content, Callback<TdApi.Message> callback) {
    if (tdlib.myUserId() != userId) { callback.onResult(null, invalid()); return; }
    tdlib.send(new TdApi.SendMessage(userId, null, null, Td.newSendOptions(), null, content), (message, error) -> {
      if (error != null) { callback.onResult(null, error); return; }
      if (message.sendingState == null) { callback.onResult(message, null); return; }
      if (message.sendingState instanceof TdApi.MessageSendingStateFailed) { callback.onResult(null, invalid()); return; }
      long pendingId = message.id;
      MessageListener listener = new MessageListener() {
        private void finish (@Nullable TdApi.Message sent, @Nullable TdApi.Error error) {
          synchronized (SavedRichMessages.this) { if (!sending.remove(this)) return; }
          tdlib.listeners().unsubscribeFromMessageUpdates(userId, this);
          callback.onResult(sent, tdlib.myUserId() == userId ? error : invalid());
        }
        @Override public void onMessageSendSucceeded (TdApi.Message sent, long oldId) {
          if (oldId != pendingId) return;
          finish(sent, null);
        }
        @Override public void onMessageSendFailed (TdApi.Message failed, long oldId, TdApi.Error error) {
          if (oldId != pendingId) return;
          finish(null, error);
        }
        @Override public void onMessagesDeleted (long chatId, long[] messageIds) {
          if (chatId != userId) return;
          for (long id : messageIds) if (id == pendingId) { finish(null, invalid()); break; }
        }
      };
      // TDLib listeners are weak; retain the operation until its final server identity arrives.
      synchronized (this) { sending.add(listener); }
      tdlib.listeners().subscribeToMessageUpdates(userId, listener);
    });
  }
}
