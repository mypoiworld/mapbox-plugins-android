package com.mapbox.mapboxsdk.plugins.cluster.clustering;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;

import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.plugins.cluster.MarkerManager;
import com.mapbox.mapboxsdk.plugins.cluster.clustering.algo.Algorithm;
import com.mapbox.mapboxsdk.plugins.cluster.clustering.algo.NonHierarchicalDistanceBasedAlgorithm;
import com.mapbox.mapboxsdk.plugins.cluster.clustering.algo.PreCachingAlgorithmDecorator;
import com.mapbox.mapboxsdk.plugins.cluster.clustering.view.ClusterRenderer;
import com.mapbox.mapboxsdk.plugins.cluster.clustering.view.DefaultClusterRenderer;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import timber.log.Timber;

/**
 * Groups many items on a mapboxMap based on zoom level.
 * <p/>
 * ClusterManager should be added to the mapboxMap as an: <ul> <li>{@link MapboxMap.OnCameraIdleListener}</li>
 * <li>{@link MapboxMap.OnMarkerClickListener}</li> </ul>
 * <p>
 * Inspired by https://github.com/googlemaps/android-maps-utils.
 * </p>
 */
public class ClusterManager<T extends ClusterItem> implements MapboxMap.OnCameraIdleListener,
  MapboxMap.OnMarkerClickListener, MapboxMap.OnInfoWindowClickListener {

  private final MarkerManager markerManager;
  private final MarkerManager.Collection markers;
  private final MarkerManager.Collection clusterMarkers;

  private Algorithm<T> algorithm;
  private final ReadWriteLock algorithmLock = new ReentrantReadWriteLock();
  private ClusterRenderer<T> renderer;

  private MapboxMap mapboxMap;
  private CameraPosition previousCameraPosition;
  private ClusterTask clusterTask;
  private final ReadWriteLock clusterTaskLock = new ReentrantReadWriteLock();

  private OnClusterItemClickListener<T> onClusterItemClickListener;
  private OnClusterInfoWindowClickListener<T> onClusterInfoWindowClickListener;
  private OnClusterItemInfoWindowClickListener<T> onClusterItemInfoWindowClickListener;
  private OnClusterClickListener<T> onClusterClickListener;

  public ClusterManager(Context context, MapboxMap map) {
    this(context, map, new MarkerManager(map));
  }

  public ClusterManager(Context context, MapboxMap map, MarkerManager markerManager) {
    this.mapboxMap = map;
    this.markerManager = markerManager;
    clusterMarkers = markerManager.newCollection();
    markers = markerManager.newCollection();
    renderer = new DefaultClusterRenderer<>(context, map, this);
    algorithm = new PreCachingAlgorithmDecorator<>(new NonHierarchicalDistanceBasedAlgorithm<T>());
    clusterTask = new ClusterTask();
    renderer.onAdd();
  }

  public MarkerManager.Collection getMarkerCollection() {
    return markers;
  }

  public MarkerManager.Collection getClusterMarkerCollection() {
    return clusterMarkers;
  }

  public MarkerManager getMarkerManager() {
    return markerManager;
  }

  public void setRenderer(ClusterRenderer<T> view) {
    renderer.setOnClusterClickListener(null);
    renderer.setOnClusterItemClickListener(null);
    clusterMarkers.clear();
    markers.clear();
    renderer.onRemove();
    renderer = view;
    renderer.onAdd();
    renderer.setOnClusterClickListener(onClusterClickListener);
    renderer.setOnClusterInfoWindowClickListener(onClusterInfoWindowClickListener);
    renderer.setOnClusterItemClickListener(onClusterItemClickListener);
    renderer.setOnClusterItemInfoWindowClickListener(onClusterItemInfoWindowClickListener);
    cluster();
  }

  public void setAlgorithm(Algorithm<T> algorithm) {
    algorithmLock.writeLock().lock();
    try {
      if (this.algorithm != null) {
        algorithm.addItems(this.algorithm.getItems());
      }
      this.algorithm = new PreCachingAlgorithmDecorator<T>(algorithm);
    } finally {
      algorithmLock.writeLock().unlock();
    }
    cluster();
  }

  public void setAnimation(boolean animate) {
    renderer.setAnimation(animate);
  }

  public ClusterRenderer<T> getRenderer() {
    return renderer;
  }

  public Algorithm<T> getAlgorithm() {
    return algorithm;
  }

  public void clearItems() {
    algorithmLock.writeLock().lock();
    try {
      algorithm.clearItems();
    } finally {
      algorithmLock.writeLock().unlock();
    }
  }

  public void addItems(Collection<T> items) {
    algorithmLock.writeLock().lock();
    try {
      algorithm.addItems(items);
    } finally {
      algorithmLock.writeLock().unlock();
    }

  }

  public void addItem(T myItem) {
    algorithmLock.writeLock().lock();
    try {
      algorithm.addItem(myItem);
    } finally {
      algorithmLock.writeLock().unlock();
    }
  }

  public void removeItem(T item) {
    algorithmLock.writeLock().lock();
    try {
      algorithm.removeItem(item);
    } finally {
      algorithmLock.writeLock().unlock();
    }
  }

  /**
   * Force a re-cluster. You may want to call this after adding new item(s).
   */
  public void cluster() {
    clusterTaskLock.writeLock().lock();
    try {
      // Attempt to cancel the in-flight request.
      clusterTask.cancel(true);
      clusterTask = new ClusterTask();
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
        clusterTask.execute((float) mapboxMap.getCameraPosition().zoom);
      } else {
        clusterTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (float) mapboxMap.getCameraPosition().zoom);
      }
    } finally {
      clusterTaskLock.writeLock().unlock();
    }
  }

  /**
   * Might re-cluster.
   */
  @Override
  public void onCameraIdle() {
    Timber.d("OnCamerIdle");
    if (renderer instanceof MapboxMap.OnCameraIdleListener) {
      ((MapboxMap.OnCameraIdleListener) renderer).onCameraIdle();
    }

    // Don't re-compute clusters if the mapboxMap has just been panned/tilted/rotated.
    CameraPosition position = mapboxMap.getCameraPosition();
    if (previousCameraPosition != null && previousCameraPosition.zoom == position.zoom) {
      return;
    }
    previousCameraPosition = mapboxMap.getCameraPosition();
    Timber.e("OnCluster");
    cluster();
  }

  @Override
  public boolean onInfoWindowClick(@NonNull Marker marker) {
    getMarkerManager().onInfoWindowClick(marker);
    return true;
  }

  @Override
  public boolean onMarkerClick(Marker marker) {
    return getMarkerManager().onMarkerClick(marker);
  }

  /**
   * Runs the clustering algorithm in a background thread, then re-paints when results come back.
   */
  private class ClusterTask extends AsyncTask<Float, Void, Set<? extends Cluster<T>>> {
    @Override
    protected Set<? extends Cluster<T>> doInBackground(Float... zoom) {
      algorithmLock.readLock().lock();
      try {
        return algorithm.getClusters(zoom[0]);
      } finally {
        algorithmLock.readLock().unlock();
      }
    }

    @Override
    protected void onPostExecute(Set<? extends Cluster<T>> clusters) {
      renderer.onClustersChanged(clusters);
    }
  }

  /**
   * Sets a callback that's invoked when a Cluster is tapped. Note: For this listener to function,
   * the ClusterManager must be added as a click listener to the mapboxMap.
   */
  public void setOnClusterClickListener(OnClusterClickListener<T> listener) {
    onClusterClickListener = listener;
    renderer.setOnClusterClickListener(listener);
  }

  /**
   * Sets a callback that's invoked when a Cluster is tapped. Note: For this listener to function,
   * the ClusterManager must be added as a info window click listener to the mapboxMap.
   */
  public void setOnClusterInfoWindowClickListener(OnClusterInfoWindowClickListener<T> listener) {
    onClusterInfoWindowClickListener = listener;
    renderer.setOnClusterInfoWindowClickListener(listener);
  }

  /**
   * Sets a callback that's invoked when an individual ClusterItem is tapped. Note: For this
   * listener to function, the ClusterManager must be added as a click listener to the mapboxMap.
   */
  public void setOnClusterItemClickListener(OnClusterItemClickListener<T> listener) {
    onClusterItemClickListener = listener;
    renderer.setOnClusterItemClickListener(listener);
  }

  /**
   * Sets a callback that's invoked when an individual ClusterItem's Info Window is tapped. Note: For this
   * listener to function, the ClusterManager must be added as a info window click listener to the mapboxMap.
   */
  public void setOnClusterItemInfoWindowClickListener(OnClusterItemInfoWindowClickListener<T> listener) {
    onClusterItemInfoWindowClickListener = listener;
    renderer.setOnClusterItemInfoWindowClickListener(listener);
  }

  /**
   * Called when a Cluster is clicked.
   */
  public interface OnClusterClickListener<T extends ClusterItem> {
    public boolean onClusterClick(Cluster<T> cluster);
  }

  /**
   * Called when a Cluster's Info Window is clicked.
   */
  public interface OnClusterInfoWindowClickListener<T extends ClusterItem> {
    public void onClusterInfoWindowClick(Cluster<T> cluster);
  }

  /**
   * Called when an individual ClusterItem is clicked.
   */
  public interface OnClusterItemClickListener<T extends ClusterItem> {
    public boolean onClusterItemClick(T item);
  }

  /**
   * Called when an individual ClusterItem's Info Window is clicked.
   */
  public interface OnClusterItemInfoWindowClickListener<T extends ClusterItem> {
    public void onClusterItemInfoWindowClick(T item);
  }
}
