package com.mapbox.mapboxsdk.plugins.testapp.activity;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;

import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.plugins.icongenerator.IconGenerator;
import com.mapbox.mapboxsdk.plugins.testapp.R;

import butterknife.BindView;
import butterknife.ButterKnife;

import static android.graphics.Typeface.BOLD;
import static android.graphics.Typeface.ITALIC;
import static android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;

/**
 * Activity showcasing IconGenerator plugin integration.
 */
public class IconGeneratorActivity extends AppCompatActivity implements OnMapReadyCallback {

  @BindView(R.id.mapView)
  MapView mapView;

  private MapboxMap mapboxMap;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_traffic);
    ButterKnife.bind(this);
    mapView.onCreate(savedInstanceState);
    mapView.getMapAsync(this);
  }

  @Override
  public void onMapReady(final MapboxMap mapboxMap) {
    this.mapboxMap = mapboxMap;
    startDemo();
  }

  private void startDemo() {
    mapboxMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(-33.8696, 151.2094), 10));

    IconGenerator iconFactory = new IconGenerator(this);
    addIcon(iconFactory, "Default", new LatLng(-33.8696, 151.2094));

    iconFactory.setColor(Color.CYAN);
    addIcon(iconFactory, "Custom color", new LatLng(-33.9360, 151.2070));

    iconFactory.setRotation(90);
    iconFactory.setStyle(IconGenerator.STYLE_RED);
    addIcon(iconFactory, "Rotated 90 degrees", new LatLng(-33.8858, 151.096));

    iconFactory.setContentRotation(-90);
    iconFactory.setStyle(IconGenerator.STYLE_PURPLE);
    addIcon(iconFactory, "Rotate=90, ContentRotate=-90", new LatLng(-33.9992, 151.098));

    iconFactory.setRotation(0);
    iconFactory.setContentRotation(90);
    iconFactory.setStyle(IconGenerator.STYLE_GREEN);
    addIcon(iconFactory, "ContentRotate=90", new LatLng(-33.7677, 151.244));

    iconFactory.setRotation(0);
    iconFactory.setContentRotation(0);
    iconFactory.setStyle(IconGenerator.STYLE_ORANGE);
    addIcon(iconFactory, makeCharSequence(), new LatLng(-33.77720, 151.12412));

    Drawable drawable = new ColorDrawable(Color.TRANSPARENT);
    drawable.setBounds(dpToPixels(48), dpToPixels(48), dpToPixels(48), dpToPixels(48));
    iconFactory.setBackground(drawable);
    iconFactory.setContentView(LayoutInflater.from(this).inflate(R.layout.marker_custom_view, null));
    addIcon(iconFactory, "custom view", new LatLng(-33.891312, 151.278189));
  }

  public int dpToPixels(float dp) {
    return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
  }

  private void addIcon(IconGenerator iconFactory, CharSequence text, LatLng position) {
    MarkerOptions markerOptions = new MarkerOptions().
      icon(IconFactory.getInstance(this).fromBitmap(iconFactory.makeIcon(text))).position(position);
//      .anchor(iconFactory.getAnchorU(), iconFactory.getAnchorV());
    mapboxMap.addMarker(markerOptions);
  }

  private CharSequence makeCharSequence() {
    String prefix = "Mixing ";
    String suffix = "different fonts";
    String sequence = prefix + suffix;
    SpannableStringBuilder ssb = new SpannableStringBuilder(sequence);
    ssb.setSpan(new StyleSpan(ITALIC), 0, prefix.length(), SPAN_EXCLUSIVE_EXCLUSIVE);
    ssb.setSpan(new StyleSpan(BOLD), prefix.length(), sequence.length(), SPAN_EXCLUSIVE_EXCLUSIVE);
    return ssb;
  }

  @Override
  protected void onStart() {
    super.onStart();
    mapView.onStart();
  }

  @Override
  protected void onResume() {
    super.onResume();
    mapView.onResume();
  }

  @Override
  protected void onPause() {
    super.onPause();
    mapView.onPause();
  }

  @Override
  protected void onStop() {
    super.onStop();
    mapView.onStop();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    mapView.onSaveInstanceState(outState);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    mapView.onDestroy();
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    mapView.onLowMemory();
  }
}
