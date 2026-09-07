package moe.kirao.mgx;

import moe.kirao.mgx.utils.DownloadedFilePredicate;
import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DownloadedFilePredicateTest {
  @Test public void nullAndIncompleteFilesAreNotDownloaded() {
    assertFalse(DownloadedFilePredicate.isDownloaded(null));
    TdApi.File file = new TdApi.File();
    file.local = new TdApi.LocalFile();
    file.local.isDownloadingCompleted = false;
    assertFalse(DownloadedFilePredicate.isDownloaded(file));
  }

  @Test public void completedLocalFileIsDownloaded() {
    TdApi.File file = new TdApi.File();
    file.local = new TdApi.LocalFile();
    file.local.isDownloadingCompleted = true;
    assertTrue(DownloadedFilePredicate.isDownloaded(file));
  }
}
