/*
 * This file is a part of Ratogram X, a Telegram X fork.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.thunderdog.challegram;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Launcher icon aliases point here instead of {@link MainActivity}.
 *
 * The activity relinquishes task identity, so the task is recorded as started by
 * {@link MainActivity}. Otherwise disabling the alias that started the task while
 * switching the app icon makes the system remove the whole task.
 */
public class LauncherActivity extends Activity {
  @Override
  protected void onCreate (Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Intent source = getIntent();
    Intent intent = new Intent(this, MainActivity.class)
      .setAction(Intent.ACTION_MAIN)
      .addCategory(Intent.CATEGORY_LAUNCHER);
    if (source != null) {
      intent.setDataAndType(source.getData(), source.getType());
      intent.setClipData(source.getClipData());
      intent.addFlags(source.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION |
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION));
      if (source.getExtras() != null) {
        intent.putExtras(source.getExtras());
      }
    }
    startActivity(intent);
    finish();
    overridePendingTransition(0, 0);
  }
}
