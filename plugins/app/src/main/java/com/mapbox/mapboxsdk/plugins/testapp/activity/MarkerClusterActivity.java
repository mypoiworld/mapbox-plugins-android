package com.mapbox.mapboxsdk.plugins.testapp.activity;

import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.constants.Style;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.plugins.cluster.clustering.ClusterItem;
import com.mapbox.mapboxsdk.plugins.cluster.clustering.ClusterManager;
import com.mapbox.mapboxsdk.plugins.cluster.clustering.algo.GridBasedAlgorithm;
import com.mapbox.mapboxsdk.plugins.cluster.clustering.algo.NonHierarchicalDistanceBasedAlgorithm;
import com.mapbox.mapboxsdk.plugins.testapp.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Activity showcasing the marker cluster plugin.
 * <p>
 * Inspired by https://github.com/googlemaps/android-maps-utils.
 * </p>
 */
public class MarkerClusterActivity extends AppCompatActivity implements OnMapReadyCallback {

  @BindView(R.id.mapView)
  MapView mapView;

  private ClusterManager<MyItem> clusterManager;
  private boolean defaultAlgorithm = true;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_cluster);
    ButterKnife.bind(this);

    mapView.setStyleUrl(Style.DARK);
    mapView.onCreate(savedInstanceState);
    mapView.getMapAsync(this);
  }

  @Override
  public void onMapReady(MapboxMap mapboxMap) {
    // init cluster manager
    clusterManager = new ClusterManager<>(this, mapboxMap);
    mapboxMap.setOnCameraIdleListener(clusterManager);

    // move camera to London
    mapboxMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(51.503186, -0.126446), 10));

    // read out json file with marker data
    try {
      InputStream inputStream = getResources().openRawResource(R.raw.radar_search);
      List<MyItem> items = new MyItemReader().read(inputStream);
      clusterManager.addItems(items);
    } catch (JSONException exception) {
      Snackbar.make(
        findViewById(android.R.id.content),
        "Exception while loading cluster",
        Snackbar.LENGTH_LONG).show();
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.menu_change_algo) {
      // change the used cluster algorithm
      clusterManager.setAlgorithm(defaultAlgorithm ? new GridBasedAlgorithm<MyItem>()
        : new NonHierarchicalDistanceBasedAlgorithm<MyItem>());
      Snackbar.make(findViewById(android.R.id.content),
        String.format("Changing algorithm to %s", defaultAlgorithm ? "Grid based" : " Non-hierarchical distance based"),
        Snackbar.LENGTH_LONG)
        .show();
      defaultAlgorithm = !defaultAlgorithm;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_cluster, menu);
    return true;
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

  public static class MyItem implements ClusterItem {
    private final LatLng position;
    private String title;
    private String snippet;

    MyItem(double lat, double lng, String title, String snippet) {
      position = new LatLng(lat, lng);
      this.title = title;
      this.snippet = snippet;
    }

    @Override
    public LatLng getPosition() {
      return position;
    }

    @Override
    public String getTitle() {
      return title;
    }

    @Override
    public String getSnippet() {
      return snippet;
    }

    public void setTitle(String title) {
      this.title = title;
    }

  }

  public static class MyItemReader {

    private static final String REGEX_INPUT_BOUNDARY_BEGINNING = "\\A";

    List<MyItem> read(InputStream inputStream) throws JSONException {
      List<MyItem> items = new ArrayList<MyItem>();
      String json = new Scanner(inputStream).useDelimiter(REGEX_INPUT_BOUNDARY_BEGINNING).next();
      JSONArray array = new JSONArray(json);
      for (int i = 0; i < array.length(); i++) {
        String title = null;
        String snippet = null;
        JSONObject object = array.getJSONObject(i);
        double lat = object.getDouble("lat");
        double lng = object.getDouble("lng");
        if (!object.isNull("title")) {
          title = object.getString("title");
        }
        if (!object.isNull("snippet")) {
          snippet = object.getString("snippet");
        }
        items.add(new MyItem(lat, lng, title, snippet));
      }
      return items;
    }
  }
}