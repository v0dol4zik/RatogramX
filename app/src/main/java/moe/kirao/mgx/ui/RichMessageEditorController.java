package moe.kirao.mgx.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.MediaToReplacePickerManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.core.Media;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.telegram.RightId;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.EditBaseController;
import org.thunderdog.challegram.ui.InstantViewController;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import me.vkryl.android.widget.FrameLayoutFix;
import moe.kirao.mgx.utils.LocalRichMessageStore;
import moe.kirao.mgx.utils.RichMessageDocument;
import moe.kirao.mgx.utils.RichMessagePolicy;
import tgx.td.Td;

public final class RichMessageEditorController extends EditBaseController<RichMessageEditorController.Args> implements View.OnClickListener, Menu {
  public static final class Args {
    public final MessagesController parent;
    public final RichMessagePolicy.Mode mode;
    public final long chatId, userId;
    public final @Nullable TdApi.Message source;
    public final @Nullable LocalRichMessageStore.Entry local;
    public final RichMessageDocument document;

    public Args (MessagesController parent, RichMessagePolicy.Mode mode, @Nullable TdApi.Message source,
                 @Nullable LocalRichMessageStore.Entry local, RichMessageDocument document) {
      this.parent = parent; this.mode = mode; this.source = source; this.local = local;
      this.document = document.copy(); this.chatId = parent.getChatId(); this.userId = parent.tdlib().myUserId();
    }
  }

  private RichMessageDocument document;
  private SettingsAdapter adapter;
  private MediaToReplacePickerManager picker;
  private boolean dirty, preparingPhoto;

  public RichMessageEditorController (Context context, Tdlib tdlib) { super(context, tdlib); }
  @Override public int getId () { return R.id.controller_richMessageEditor; }
  @Override public CharSequence getName () { return Lang.getString(R.string.RichMessageEditor); }
  @Override protected int getMenuId () { return R.id.menu_more; }

  @Override public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_more) {
      header.addViewButton(menu, this, getHeaderIconColorId());
      header.addMoreButton(menu, this);
    }
  }

  @Override public void onMenuItemPressed (int id, View view) {
    if (isDoneInProgress() || preparingPhoto) return;
    if (id == R.id.menu_btn_view) preview();
    else if (id == R.id.menu_btn_more) {
      showOptions(new int[] {R.id.btn_richAddBlock, R.id.btn_richRtl},
        new String[] {Lang.getString(R.string.RichMessageAddBlock), Lang.getString(R.string.RichMessageRtl)}, (v, option) -> {
          if (option == R.id.btn_richAddBlock) addBlockMenu();
          else { document.isRtl = !document.isRtl; dirty = true; rebuild(); }
          return true;
        });
    }
  }

  @Override protected void onCreateView (Context context, FrameLayoutFix content, RecyclerView recycler) {
    document = getArgumentsStrict().document.copy();
    adapter = new SettingsAdapter(this) {
      @Override protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup input) {
        input.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        Views.setSingleLine(input.getEditText(), false);
        input.setMaxLength(RichMessageDocument.MAX_TEXT);
      }
    };
    adapter.setTextChangeListener((id, item, input) -> {
      RichMessageDocument.Block block = (RichMessageDocument.Block) item.getData();
      if (block == null) return;
      String value = input.getText().toString();
      if (id == R.id.input_richLanguage) {
        if (!block.extra.equals(value)) { block.extra = value; dirty = true; }
      } else if (!block.text.equals(value)) { block.text = value; dirty = true; }
      item.setStringValue(value);
    });
    recycler.setAdapter(adapter);
    recycler.setPadding(0, 0, 0, org.thunderdog.challegram.tool.Screen.dp(88));
    recycler.setClipToPadding(false);
    setDoneVisible(true, false);
    if (getArgumentsStrict().mode == RichMessagePolicy.Mode.TELEGRAM && getArgumentsStrict().source == null) setDoneIcon(R.drawable.baseline_send_24);
    rebuild();
  }

  private int label (int type) {
    return switch (type) {
      case RichMessageDocument.HEADING -> R.string.RichMessageHeading;
      case RichMessageDocument.LIST -> R.string.RichMessageList;
      case RichMessageDocument.QUOTE -> R.string.RichMessageQuote;
      case RichMessageDocument.CODE -> R.string.RichMessageCode;
      case RichMessageDocument.PHOTO -> R.string.Photo;
      default -> R.string.RichMessageParagraph;
    };
  }

  private void rebuild () {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, getArgumentsStrict().mode == RichMessagePolicy.Mode.LOCAL ? R.string.RichMessageLocalInfo : R.string.RichMessageTelegramInfo));
    for (RichMessageDocument.Block block : document.blocks) {
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_richBlock, R.drawable.baseline_more_vert_24,
        Lang.getString(label(block.type)) + (block.type == RichMessageDocument.HEADING ? " · H" + block.size : ""), false).setData(block));
      items.add(new ListItem(ListItem.TYPE_EDITTEXT, R.id.input_richText, 0,
        block.type == RichMessageDocument.LIST ? R.string.RichMessageListHint : block.type == RichMessageDocument.PHOTO ? R.string.Caption : label(block.type)).setData(block).setStringValue(block.text));
      if (block.type == RichMessageDocument.CODE) items.add(new ListItem(ListItem.TYPE_EDITTEXT, R.id.input_richLanguage, 0, R.string.RichMessageLanguage).setData(block).setStringValue(block.extra));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    }
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_richAddBlock, R.drawable.baseline_add_24, R.string.RichMessageAddBlock));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_richPreview, R.drawable.baseline_visibility_24, R.string.RichMessagePreview));
    adapter.setItems(items, false);
  }

  @Override public void onClick (View view) {
    if (isDoneInProgress() || preparingPhoto) return;
    if (view.getId() == R.id.btn_richAddBlock) addBlockMenu();
    else if (view.getId() == R.id.btn_richPreview) preview();
    else if (view.getId() == R.id.btn_richBlock && view.getTag() instanceof ListItem) blockMenu((RichMessageDocument.Block) ((ListItem) view.getTag()).getData());
  }

  private void addBlockMenu () {
    showOptions(new int[] {R.id.btn_richParagraph, R.id.btn_richHeading, R.id.btn_richList, R.id.btn_richQuote, R.id.btn_richCode, R.id.btn_richPhoto},
      new String[] {Lang.getString(R.string.RichMessageParagraph), Lang.getString(R.string.RichMessageHeading), Lang.getString(R.string.RichMessageList), Lang.getString(R.string.RichMessageQuote), Lang.getString(R.string.RichMessageCode), Lang.getString(R.string.Photo)}, (v, id) -> {
        if (document.blocks.size() >= RichMessageDocument.MAX_BLOCKS) { UI.showToast(R.string.RichMessageInvalid, Toast.LENGTH_SHORT); return true; }
        int type = id == R.id.btn_richHeading ? RichMessageDocument.HEADING : id == R.id.btn_richList ? RichMessageDocument.LIST :
          id == R.id.btn_richQuote ? RichMessageDocument.QUOTE : id == R.id.btn_richCode ? RichMessageDocument.CODE : id == R.id.btn_richPhoto ? RichMessageDocument.PHOTO : RichMessageDocument.PARAGRAPH;
        if (type == RichMessageDocument.PHOTO) pickPhoto();
        else { document.blocks.add(new RichMessageDocument.Block(type)); dirty = true; rebuild(); }
        return true;
      });
  }

  private void blockMenu (RichMessageDocument.Block block) {
    int index = document.blocks.indexOf(block);
    if (index < 0) return;
    List<Integer> ids = new ArrayList<>(); List<String> names = new ArrayList<>();
    if (block.type == RichMessageDocument.HEADING) { ids.add(R.id.btn_richHeading); names.add(Lang.getString(R.string.RichMessageHeadingSize, block.size)); }
    if (block.type == RichMessageDocument.LIST) { ids.add(R.id.btn_richOrdered); names.add(Lang.getString(R.string.RichMessageOrdered)); }
    if (index > 0) { ids.add(R.id.btn_richMoveUp); names.add(Lang.getString(R.string.RichMessageMoveUp)); }
    if (index + 1 < document.blocks.size()) { ids.add(R.id.btn_richMoveDown); names.add(Lang.getString(R.string.RichMessageMoveDown)); }
    ids.add(R.id.btn_delete); names.add(Lang.getString(R.string.Delete));
    int[] options = new int[ids.size()]; for (int i = 0; i < options.length; i++) options[i] = ids.get(i);
    showOptions(options, names.toArray(new String[0]), (v, id) -> {
      if (id == R.id.btn_delete) document.blocks.remove(block);
      else if (id == R.id.btn_richMoveUp) Collections.swap(document.blocks, index, index - 1);
      else if (id == R.id.btn_richMoveDown) Collections.swap(document.blocks, index, index + 1);
      else if (id == R.id.btn_richHeading) block.size = block.size % 6 + 1;
      else if (id == R.id.btn_richOrdered) block.ordered = !block.ordered;
      dirty = true; rebuild(); return true;
    });
  }

  private boolean validateDocument () {
    try { document.validate(); return true; }
    catch (IllegalArgumentException e) { UI.showToast(R.string.RichMessageInvalid, Toast.LENGTH_LONG); return false; }
  }

  private void preview () {
    if (!validateDocument()) return;
    hideSoftwareKeyboard();
    TdApi.Message message = new LocalRichMessageStore.Entry(RichMessagePolicy.localId(1), (int) (System.currentTimeMillis() / 1000), 0, document).toMessage(tdlib.myUserId());
    InstantViewController viewer = new InstantViewController(context(), tdlib);
    viewer.setArguments(InstantViewController.Args.forRichMessage(message, document.output(), false));
    navigateTo(viewer);
  }

  @Override protected boolean onDoneClick () {
    if (isDoneInProgress() || preparingPhoto || !validateDocument()) return true;
    Args args = getArgumentsStrict();
    if (args.userId != tdlib.myUserId() || args.parent.isDestroyed() || args.parent.getChatId() != args.chatId) return true;
    if (args.mode == RichMessagePolicy.Mode.LOCAL) {
      if (args.chatId != tdlib.myUserId() || args.local == null && !Settings.instance().isLocalPremiumExperimentEnabled()) {
        UI.showToast(R.string.RichMessageAccessChanged, Toast.LENGTH_LONG); return true;
      }
      setInProgress(true);
      new LocalRichMessageStore(tdlib.id(), args.userId).save(args.local != null ? args.local.id : 0, document, (entry, error) -> {
        if (isDestroyed()) return;
        setInProgress(false);
        if (error != null) UI.showToast(R.string.RichMessageLocalError, Toast.LENGTH_LONG);
        else {
          dirty = false;
          if (args.local == null) args.parent.revealLocalRichMessage(); else args.parent.refreshLocalRichMessages();
          navigateBack();
        }
      });
    } else {
      if (!tdlib.hasTelegramPremium() || !args.parent.canWriteMessages()) { UI.showToast(R.string.RichMessageAccessChanged, Toast.LENGTH_LONG); return true; }
      TdApi.InputMessageRichMessage content = document.input();
      if (args.source == null) {
        setInProgress(true);
        if (!args.parent.sendRichMessage(content, message -> UI.post(() -> {
          if (isDestroyed()) return;
          setInProgress(false);
          if (message != null) { dirty = false; navigateBack(); }
        }))) setInProgress(false);
      } else {
        setInProgress(true);
        tdlib.send(new TdApi.GetMessageProperties(args.chatId, args.source.id), (properties, error) -> UI.post(() -> {
          if (isDestroyed()) return;
          if (error != null || properties == null || !RichMessagePolicy.canEdit(false, tdlib.hasTelegramPremium(), args.parent.canWriteMessages(), properties.canBeEdited)) {
            setInProgress(false); UI.showToast(R.string.RichMessageAccessChanged, Toast.LENGTH_LONG); return;
          }
          tdlib.send(new TdApi.EditMessageText(args.chatId, args.source.id, args.source.replyMarkup, content), (message, editError) -> UI.post(() -> {
            if (isDestroyed()) return;
            setInProgress(false);
            if (editError != null) UI.showError(editError);
            else { dirty = false; navigateBack(); }
          }));
        }));
      }
    }
    return true;
  }

  private void pickPhoto () {
    if (picker == null) picker = new MediaToReplacePickerManager(this);
    picker.openMediaView(file -> {
      if (file.imageGalleryFile == null || file.imageGalleryFile.isVideo()) { UI.showToast(R.string.RichMessagePhotoError, Toast.LENGTH_SHORT); return; }
      preparingPhoto = true;
      Media.instance().post(() -> {
        RichMessageDocument.Block photo = null;
        try { photo = preparePhoto(file.imageGalleryFile.getFilePath()); } catch (Exception ignored) { }
        RichMessageDocument.Block result = photo;
        UI.post(() -> {
          if (isDestroyed()) return;
          preparingPhoto = false;
          if (result == null) UI.showToast(R.string.RichMessagePhotoError, Toast.LENGTH_LONG);
          else { document.blocks.add(result); dirty = true; rebuild(); }
        });
      });
    }, getArgumentsStrict().chatId, null, false, null, 1 << RightId.SEND_PHOTOS, false);
  }

  private RichMessageDocument.Block preparePhoto (String path) throws IOException {
    BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(path, options);
    if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Not an image");
    options.inSampleSize = 1;
    while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 1600) options.inSampleSize *= 2;
    options.inJustDecodeBounds = false;
    Bitmap bitmap = BitmapFactory.decodeFile(path, options);
    if (bitmap == null) throw new IOException("Cannot decode image");
    try {
      int orientation = new ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
      Matrix matrix = new Matrix();
      if (orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_TRANSPOSE) matrix.postRotate(90);
      else if (orientation == ExifInterface.ORIENTATION_ROTATE_180 || orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL) matrix.postRotate(180);
      else if (orientation == ExifInterface.ORIENTATION_ROTATE_270 || orientation == ExifInterface.ORIENTATION_TRANSVERSE) matrix.postRotate(270);
      if (orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL || orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL || orientation == ExifInterface.ORIENTATION_TRANSPOSE || orientation == ExifInterface.ORIENTATION_TRANSVERSE) matrix.postScale(-1, 1);
      if (!matrix.isIdentity()) {
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) { bitmap.recycle(); bitmap = rotated; }
      }
      File directory = new File(context().getCacheDir(), "rich-editor");
      if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot prepare image");
      File output = new File(directory, UUID.randomUUID() + ".jpg");
      try (FileOutputStream stream = new FileOutputStream(output)) {
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)) throw new IOException("Cannot encode image");
      }
      RichMessageDocument.Block block = new RichMessageDocument.Block(RichMessageDocument.PHOTO);
      block.photoPath = output.getAbsolutePath(); block.width = bitmap.getWidth(); block.height = bitmap.getHeight();
      return block;
    } finally { bitmap.recycle(); }
  }

  @Override public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (isDoneInProgress() || preparingPhoto) return true;
    if (dirty) { if (commit) showUnsavedChangesPromptBeforeLeaving(null); return true; }
    return super.performOnBackPressed(fromTop, commit);
  }

  @Override public boolean canSlideBackFrom (NavigationController navigation, float x, float y) { return !dirty && !isDoneInProgress() && !preparingPhoto; }
}
