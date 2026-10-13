package moe.kirao.mgx.utils;

/** Local wallpaper adjustments. Only the blur on/off flag is sent to TDLib. */
public final class WallpaperAppearance {
  public static final int BLEND_NORMAL = 0, BLEND_MULTIPLY = 1, BLEND_SCREEN = 2;
  private static final int[] BLUR_RADII = {0, 1, 3, 7, 15};
  public static final WallpaperAppearance DEFAULT = new WallpaperAppearance(0, 0, 0, 0, BLEND_NORMAL);

  public final int dim, blurRadius, tintColor, tintIntensity, tintBlend;

  public WallpaperAppearance (int dim, int blurRadius, int tintColor, int tintIntensity, int tintBlend) {
    this.dim = clamp(dim, 0, 80);
    this.blurRadius = blurRadiusForStep(blurStep(blurRadius));
    this.tintColor = tintColor & 0xffffff;
    this.tintIntensity = clamp(tintIntensity, 0, 80);
    this.tintBlend = clamp(tintBlend, BLEND_NORMAL, BLEND_SCREEN);
  }

  public static WallpaperAppearance forBlurred (boolean blurred) {
    return blurred ? new WallpaperAppearance(0, 3, 0, 0, BLEND_NORMAL) : DEFAULT;
  }

  public static int clamp (int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  public static int blurStep (int radius) {
    int nearest = 0;
    for (int i = 1; i < BLUR_RADII.length; i++) {
      if (Math.abs((long) radius - BLUR_RADII[i]) < Math.abs((long) radius - BLUR_RADII[nearest])) nearest = i;
    }
    return nearest;
  }

  public static int blurRadiusForStep (int step) {
    return BLUR_RADII[clamp(step, 0, BLUR_RADII.length - 1)];
  }

  public WallpaperAppearance withBlur (boolean blurred) {
    return new WallpaperAppearance(dim, blurred ? Math.max(3, blurRadius) : 0, tintColor, tintIntensity, tintBlend);
  }

  @Override public boolean equals (Object other) {
    if (!(other instanceof WallpaperAppearance)) return false;
    WallpaperAppearance value = (WallpaperAppearance) other;
    return dim == value.dim && blurRadius == value.blurRadius && tintColor == value.tintColor &&
      tintIntensity == value.tintIntensity && tintBlend == value.tintBlend;
  }

  @Override public int hashCode () {
    return (((dim * 31 + blurRadius) * 31 + tintColor) * 31 + tintIntensity) * 31 + tintBlend;
  }
}
