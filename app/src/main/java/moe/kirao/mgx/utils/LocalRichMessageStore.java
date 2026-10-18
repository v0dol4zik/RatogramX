package moe.kirao.mgx.utils;

import androidx.annotation.Nullable;
import androidx.core.util.AtomicFile;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.AppContext;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Device-only rich messages. Every operation runs on the background queue. */
public final class LocalRichMessageStore {
  private static final int MAGIC = 0x5258524d, VERSION = 1, MAX_ENTRIES = 1000;
  private final File directory;
  private final int accountId;
  private final long userId;

  public LocalRichMessageStore (int accountId, long userId) {
    this(new File(AppContext.get().getFilesDir(), "rich-messages"), accountId, userId);
  }

  public LocalRichMessageStore (File root, int accountId, long userId) {
    if (accountId < 0 || userId <= 0) throw new IllegalArgumentException("Authorized account required");
    this.accountId = accountId; this.userId = userId;
    directory = new File(root, accountId + "-" + userId);
  }

  public static final class Entry {
    public final long id;
    public final int date, editDate;
    public final RichMessageDocument document;

    public Entry (long id, int date, int editDate, RichMessageDocument document) {
      this.id = id; this.date = date; this.editDate = editDate; this.document = document;
    }

    public TdApi.Message toMessage (long userId) {
      TdApi.Message message = new TdApi.Message();
      message.id = id; message.chatId = userId; message.senderId = new TdApi.MessageSenderUser(userId);
      message.isOutgoing = true; message.canBeSaved = true;
      message.date = date; message.editDate = editDate;
      message.authorSignature = ""; message.restrictionInfo = null;
      message.content = new TdApi.MessageRichMessage(document.output());
      return message;
    }
  }

  public interface Callback<T> { void onResult (@Nullable T result, @Nullable Exception error); }

  public void load (Callback<List<Entry>> callback) {
    Background.instance().post(() -> {
      try {
        List<Entry> entries = read();
        UI.post(() -> callback.onResult(entries, null));
      } catch (Exception e) { UI.post(() -> callback.onResult(null, e)); }
    });
  }

  public void save (long id, RichMessageDocument source, Callback<Entry> callback) {
    RichMessageDocument document = source.copy();
    Background.instance().post(() -> {
      List<File> copied = new ArrayList<>();
      try {
        document.validate();
        List<Entry> entries = read();
        Entry previous = null;
        long sequence = System.currentTimeMillis();
        for (Entry entry : entries) {
          sequence = Math.max(sequence, entry.id - Long.MIN_VALUE + 1);
          if (entry.id == id) previous = entry;
        }
        if (id != 0 && previous == null) throw new IOException("Local message no longer exists");
        if (id == 0 && entries.size() >= MAX_ENTRIES) throw new IOException("Local history is full");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create local history");
        for (RichMessageDocument.Block block : document.blocks) {
          if (block.type != RichMessageDocument.PHOTO) continue;
          File image = new File(block.photoPath).getCanonicalFile();
          if (!image.isFile() || image.length() > 10 * 1024 * 1024) throw new IOException("Image is unavailable");
          if (!directory.getCanonicalFile().equals(image.getParentFile())) {
            File target = new File(directory, UUID.randomUUID() + ".jpg");
            copied.add(target);
            try (FileInputStream in = new FileInputStream(image); FileOutputStream out = new FileOutputStream(target)) {
              byte[] buffer = new byte[16384]; int n;
              while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
              out.getFD().sync();
            }
            block.photoPath = target.getAbsolutePath();
          }
        }
        int now = (int) (System.currentTimeMillis() / 1000);
        Entry entry = new Entry(previous != null ? previous.id : RichMessagePolicy.localId(sequence), previous != null ? previous.date : now, previous != null ? now : 0, document);
        if (previous != null) entries.remove(previous);
        entries.add(0, entry);
        write(entries);
        pruneImages(entries);
        UI.post(() -> callback.onResult(entry, null));
      } catch (Exception e) {
        for (File file : copied) file.delete();
        UI.post(() -> callback.onResult(null, e));
      }
    });
  }

  public void delete (long id, Callback<Boolean> callback) {
    Background.instance().post(() -> {
      try {
        List<Entry> entries = read();
        boolean removed = entries.removeIf(e -> e.id == id);
        if (removed) { write(entries); pruneImages(entries); }
        UI.post(() -> callback.onResult(removed, null));
      } catch (Exception e) { UI.post(() -> callback.onResult(null, e)); }
    });
  }

  private AtomicFile index () { return new AtomicFile(new File(directory, "messages.bin")); }

  private List<Entry> read () throws IOException {
    List<Entry> entries = new ArrayList<>();
    AtomicFile file = index();
    if (!file.getBaseFile().exists() && !new File(directory, "messages.bin.bak").exists()) return entries;
    try (DataInputStream in = new DataInputStream(new BufferedInputStream(file.openRead()))) {
      if (in.readInt() != MAGIC || in.readInt() != VERSION || in.readInt() != accountId || in.readLong() != userId) throw new IOException("Invalid history header");
      int count = in.readInt();
      if (count < 0 || count > MAX_ENTRIES) throw new IOException("Invalid history size");
      Set<Long> ids = new HashSet<>();
      for (int i = 0; i < count; i++) {
        long id = in.readLong(); int date = in.readInt(), editDate = in.readInt();
        if (!RichMessagePolicy.isLocalId(id) || !ids.add(id)) throw new IOException("Invalid history ID");
        RichMessageDocument document = RichMessageDocument.read(in);
        for (RichMessageDocument.Block block : document.blocks) {
          if (block.type == RichMessageDocument.PHOTO && !directory.getCanonicalFile().equals(new File(block.photoPath).getCanonicalFile().getParentFile())) throw new IOException("Invalid image path");
        }
        entries.add(new Entry(id, date, editDate, document));
      }
      if (in.read() != -1) throw new IOException("Invalid history data");
    }
    entries.sort((a, b) -> Long.compare(b.id, a.id));
    return entries;
  }

  private void write (List<Entry> entries) throws IOException {
    if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create local history");
    AtomicFile file = index();
    FileOutputStream stream = file.startWrite();
    try {
      DataOutputStream out = new DataOutputStream(stream);
      out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(accountId); out.writeLong(userId); out.writeInt(entries.size());
      for (Entry entry : entries) {
        out.writeLong(entry.id); out.writeInt(entry.date); out.writeInt(entry.editDate); entry.document.write(out);
      }
      out.flush(); file.finishWrite(stream);
    } catch (Exception e) { file.failWrite(stream); throw e; }
  }

  private void pruneImages (List<Entry> entries) {
    Set<String> used = new HashSet<>();
    for (Entry entry : entries) for (RichMessageDocument.Block block : entry.document.blocks) if (block.type == RichMessageDocument.PHOTO) used.add(block.photoPath);
    File[] files = directory.listFiles();
    if (files != null) for (File file : files) if (file.getName().endsWith(".jpg") && !used.contains(file.getAbsolutePath())) file.delete();
  }
}
