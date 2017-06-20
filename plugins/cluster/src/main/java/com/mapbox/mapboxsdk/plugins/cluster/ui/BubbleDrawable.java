package com.mapbox.mapboxsdk.plugins.cluster.ui;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;

import com.mapbox.mapboxsdk.plugins.cluster.R;

/**
 * Draws a bubble with a shadow, filled with any color.
 * <p>
 * Inspired by https://github.com/googlemaps/android-maps-utils.
 * </p>
 */
class BubbleDrawable extends Drawable {

  private final Drawable shadow;
  private final Drawable mask;
  private int color = Color.WHITE;

  public BubbleDrawable(Resources res) {
    mask = res.getDrawable(R.drawable.amu_bubble_mask);
    shadow = res.getDrawable(R.drawable.amu_bubble_shadow);
  }

  public void setColor(int color) {
    this.color = color;
  }

  @Override
  public void draw(@NonNull Canvas canvas) {
    mask.draw(canvas);
    canvas.drawColor(color, PorterDuff.Mode.SRC_IN);
    shadow.draw(canvas);
  }

  @Override
  public void setAlpha(int alpha) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setColorFilter(ColorFilter cf) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }

  @Override
  public void setBounds(int left, int top, int right, int bottom) {
    mask.setBounds(left, top, right, bottom);
    shadow.setBounds(left, top, right, bottom);
  }

  @Override
  public void setBounds(@NonNull Rect bounds) {
    mask.setBounds(bounds);
    shadow.setBounds(bounds);
  }

  @Override
  public boolean getPadding(@NonNull Rect padding) {
    return mask.getPadding(padding);
  }
}
