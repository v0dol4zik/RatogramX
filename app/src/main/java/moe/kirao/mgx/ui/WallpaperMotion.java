package moe.kirao.mgx.ui;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.Screen;

/** Small, filtered parallax offset; the owning controller controls its lifetime. */
public final class WallpaperMotion implements SensorEventListener {
  private final View view;
  private final @Nullable SensorManager manager;
  private final @Nullable Sensor sensor;
  private boolean registered, initialized;
  private float x, y, baseX, baseY;

  public WallpaperMotion (View view) {
    this.view = view;
    manager = (SensorManager) view.getContext().getSystemService(Context.SENSOR_SERVICE);
    Sensor gravity = manager != null ? manager.getDefaultSensor(Sensor.TYPE_GRAVITY) : null;
    sensor = gravity != null ? gravity : manager != null ? manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
  }

  public void setEnabled (boolean enabled) {
    enabled = enabled && sensor != null && manager != null;
    if (registered == enabled) return;
    if (enabled) {
      registered = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
    } else {
      manager.unregisterListener(this);
      registered = false;
    }
    initialized = false;
    x = y = 0;
    view.invalidate();
  }

  public float getX () { return x * Screen.dp(10f); }
  public float getY () { return y * Screen.dp(10f); }

  @Override public void onSensorChanged (SensorEvent event) {
    if (!registered) return;
    float gravityX = event.values[0], gravityY = event.values[1];
    WindowManager windows = (WindowManager) view.getContext().getSystemService(Context.WINDOW_SERVICE);
    int rotation = windows != null ? windows.getDefaultDisplay().getRotation() : Surface.ROTATION_0;
    float rotatedX = gravityX, rotatedY = gravityY;
    switch (rotation) {
      case Surface.ROTATION_90: rotatedX = -gravityY; rotatedY = gravityX; break;
      case Surface.ROTATION_180: rotatedX = -gravityX; rotatedY = -gravityY; break;
      case Surface.ROTATION_270: rotatedX = gravityY; rotatedY = -gravityX; break;
    }
    if (!initialized) { baseX = rotatedX; baseY = rotatedY; initialized = true; }
    x += (Math.max(-1f, Math.min(1f, (rotatedX - baseX) / 4f)) - x) * .15f;
    y += (Math.max(-1f, Math.min(1f, (rotatedY - baseY) / 4f)) - y) * .15f;
    view.invalidate();
  }

  @Override public void onAccuracyChanged (Sensor sensor, int accuracy) { }
}
