package com.mapbox.mapboxsdk.plugins.cluster.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;

/**
 * Resembles a square shaped TextView.
 * <p>
 * Inspired by https://github.com/googlemaps/android-maps-utils.
 * </p>
 */
public class SquareTextView extends AppCompatTextView {

  private int offsetTop = 0;
  private int offsetLeft = 0;

  public SquareTextView(Context context) {
    super(context);
  }

  public SquareTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public SquareTextView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();
    int dimension = Math.max(width, height);
    if (width > height) {
      offsetTop = width - height;
      offsetLeft = 0;
    } else {
      offsetTop = 0;
      offsetLeft = height - width;
    }
    setMeasuredDimension(dimension, dimension);
  }

  @Override
  public void draw(Canvas canvas) {
    canvas.translate(offsetLeft / 2, offsetTop / 2);
    super.draw(canvas);
  }

}
