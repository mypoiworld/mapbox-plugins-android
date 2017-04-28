package com.mapbox.androidsdk.plugins.building;

import android.graphics.Color;
import android.support.annotation.ColorInt;
import android.support.annotation.FloatRange;
import android.support.annotation.NonNull;

import com.mapbox.mapboxsdk.constants.MapboxConstants;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.style.functions.Function;
import com.mapbox.mapboxsdk.style.functions.stops.IdentityStops;
import com.mapbox.mapboxsdk.style.layers.FillExtrusionLayer;
import com.mapbox.mapboxsdk.style.light.Light;

import static com.mapbox.mapboxsdk.style.layers.Filter.eq;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillExtrusionBase;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillExtrusionColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillExtrusionHeight;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillExtrusionOpacity;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.visibility;

/**
 * The building plugin allows to add 3d buildings FillExtrusionLayer to the Mapbox Android SDK v5.1.0.
 * <p>
 * Initialise this plugin in the {@link com.mapbox.mapboxsdk.maps.OnMapReadyCallback#onMapReady(MapboxMap)} and provide
 * a valid instance of {@link MapView} and {@link MapboxMap}.
 * </p>
 * <p>
 * Use {@link #setVisibility(boolean)}} to show buildings from this plugin.
 * Use {@link #setColor(int)} to change the color of the buildings from this plugin.
 * Use {@link #setOpacity(float)} to change the opacity of the buildings from this plugin.
 * </p>
 */
public final class BuildingPlugin {

  private static final String LAYER_ID = "3d-buildings";

  private FillExtrusionLayer fillExtrusionLayer;
  private boolean visible = false;
  private int color = Color.LTGRAY;
  private float opacity = 0.6f;
  private float minZoomLevel = 15.0f;
  private Light light;

  /**
   * Create a building plugin.
   *
   * @param mapView   the MapView to apply the building plugin to
   * @param mapboxMap the MapboxMap to apply building plugin with
   */
  public BuildingPlugin(@NonNull MapView mapView, @NonNull final MapboxMap mapboxMap) {
    initLayer(mapboxMap);
    mapView.addOnMapChangedListener(new MapView.OnMapChangedListener() {
      @Override
      public void onMapChanged(int change) {
        if (change == MapView.DID_FINISH_LOADING_STYLE && mapboxMap.getLayer(LAYER_ID) == null) {
          initLayer(mapboxMap);
        }
      }
    });
  }

  /**
   * Initialises and adds the fill extrusion layer used by this plugin.
   *
   * @param mapboxMap the MapboxMap instance to add the layer to
   */
  private void initLayer(MapboxMap mapboxMap) {
    light = mapboxMap.getLight();
    fillExtrusionLayer = new FillExtrusionLayer(LAYER_ID, "composite");
    fillExtrusionLayer.setSourceLayer("building");
    fillExtrusionLayer.setFilter(eq("extrude", "true"));
    fillExtrusionLayer.setMinZoom(minZoomLevel);
    fillExtrusionLayer.setProperties(
      visibility(visible ? "visible" : "none"),
      fillExtrusionColor(color),
      fillExtrusionHeight(Function.property("height", new IdentityStops<Float>())),
      fillExtrusionBase(Function.property("min_height", new IdentityStops<Float>())),
      fillExtrusionOpacity(opacity)
    );
    mapboxMap.addLayer(fillExtrusionLayer);
  }

  /**
   * Toggles the visibility of the building layer.
   *
   * @param visible true for visible, false for none
   */
  public void setVisibility(boolean visible) {
    this.visible = visible;
    fillExtrusionLayer.setProperties(visibility(visible ? "visible" : "none"));
  }

  /**
   * Change the building opacity.
   * <p>
   * Calls into changing the fill extrusion fill opacity.
   * </p>
   */
  public void setOpacity(@FloatRange(from = 0.0f, to = 1.0f) float opacity) {
    this.opacity = opacity;
    fillExtrusionLayer.setProperties(fillExtrusionOpacity(opacity));
  }

  /**
   * Change the building color.
   * <p>
   * Calls into changing the fill extrusion fill color.
   * </p>
   */
  public void setColor(@ColorInt int color) {
    this.color = color;
    fillExtrusionLayer.setProperties(fillExtrusionColor(color));
  }

  /**
   * Change the building min zoom level. This is the minimum zoom level where buildings will start to show.
   * <p>
   * Note that this method is used to limit showing buildings at higher zoom levels.
   * </p>
   */
  public void setMinZoomLevel(@FloatRange(from = MapboxConstants.MINIMUM_ZOOM, to = MapboxConstants.MAXIMUM_ZOOM)
                                float minZoomLevel) {
    this.minZoomLevel = minZoomLevel;
    fillExtrusionLayer.setMinZoom(minZoomLevel);
  }

  /**
   * Get the light source that is illuminating the building.
   *
   * @return the light source
   */
  public Light getLight() {
    return light;
  }
}


