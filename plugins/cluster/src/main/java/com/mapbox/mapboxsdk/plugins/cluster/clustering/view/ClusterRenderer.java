package com.mapbox.mapboxsdk.plugins.cluster.clustering.view;

import com.mapbox.mapboxsdk.plugins.cluster.clustering.Cluster;
import com.mapbox.mapboxsdk.plugins.cluster.clustering.ClusterItem;
import com.mapbox.mapboxsdk.plugins.cluster.clustering.ClusterManager;

import java.util.Set;

/**
 * Renders clusters.
 * <p>
 * Inspired by https://github.com/googlemaps/android-maps-utils.
 * </p>
 */
public interface ClusterRenderer<T extends ClusterItem> {

  /**
   * Called when the view needs to be updated because new clusters need to be displayed.
   *
   * @param clusters the clusters to be displayed.
   */
  void onClustersChanged(Set<? extends Cluster<T>> clusters);

  void setOnClusterClickListener(ClusterManager.OnClusterClickListener<T> listener);

  void setOnClusterInfoWindowClickListener(ClusterManager.OnClusterInfoWindowClickListener<T> listener);

  void setOnClusterItemClickListener(ClusterManager.OnClusterItemClickListener<T> listener);

  void setOnClusterItemInfoWindowClickListener(ClusterManager.OnClusterItemInfoWindowClickListener<T> listener);

  /**
   * Called to set animation on or off
   */
  void setAnimation(boolean animate);

  /**
   * Called when the view is added.
   */
  void onAdd();

  /**
   * Called when the view is removed.
   */
  void onRemove();
}