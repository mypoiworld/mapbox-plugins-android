package com.mapbox.mapboxsdk.plugins.cluster.clustering.algo;

import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.plugins.cluster.clustering.Cluster;
import com.mapbox.mapboxsdk.plugins.cluster.clustering.ClusterItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A cluster whose center is determined upon creation.
 * <p>
 * Inspired by https://github.com/googlemaps/android-maps-utils.
 * </p>
 */
public class StaticCluster<T extends ClusterItem> implements Cluster<T> {

  private final LatLng center;
  private final List<T> items = new ArrayList<T>();

  public StaticCluster(LatLng center) {
    this.center = center;
  }

  public boolean add(T t) {
    return items.add(t);
  }

  @Override
  public LatLng getPosition() {
    return center;
  }

  public boolean remove(T t) {
    return items.remove(t);
  }

  @Override
  public Collection<T> getItems() {
    return items;
  }

  @Override
  public int getSize() {
    return items.size();
  }

  @Override
  public String toString() {
    return "StaticCluster{" +
      "center=" + center +
      ", items.size=" + items.size() +
      '}';
  }

  @Override
  public int hashCode() {
    return center.hashCode() + items.hashCode();
  }

  ;

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof StaticCluster<?>)) {
      return false;
    }

    return ((StaticCluster<?>) other).center.equals(center)
      && ((StaticCluster<?>) other).items.equals(items);
  }

}