package com.mapbox.mapboxsdk.plugins.cluster.clustering.algo;

import android.support.v4.util.LruCache;

import com.mapbox.mapboxsdk.plugins.cluster.clustering.Cluster;
import com.mapbox.mapboxsdk.plugins.cluster.clustering.ClusterItem;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Optimistically fetch clusters for adjacent zoom levels, caching them as necessary.
 * <p>
 * Inspired by https://github.com/googlemaps/android-maps-utils.
 * </p>
 */
public class PreCachingAlgorithmDecorator<T extends ClusterItem> implements Algorithm<T> {

  private final Algorithm<T> algorithm;

  // TODO: evaluate maxSize parameter for LruCache.
  private final LruCache<Integer, Set<? extends Cluster<T>>> cache = new LruCache<Integer, Set<? extends Cluster<T>>>(5);
  private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();

  public PreCachingAlgorithmDecorator(Algorithm<T> algorithm) {
    this.algorithm = algorithm;
  }

  public void addItem(T item) {
    algorithm.addItem(item);
    clearCache();
  }

  @Override
  public void addItems(Collection<T> items) {
    algorithm.addItems(items);
    clearCache();
  }

  @Override
  public void clearItems() {
    algorithm.clearItems();
    clearCache();
  }

  public void removeItem(T item) {
    algorithm.removeItem(item);
    clearCache();
  }

  private void clearCache() {
    cache.evictAll();
  }

  @Override
  public Set<? extends Cluster<T>> getClusters(double zoom) {
    int discreteZoom = (int) zoom;
    Set<? extends Cluster<T>> results = getClustersInternal(discreteZoom);
    // TODO: Check if requests are already in-flight.
    if (cache.get(discreteZoom + 1) == null) {
      new Thread(new PrecacheRunnable(discreteZoom + 1)).start();
    }
    if (cache.get(discreteZoom - 1) == null) {
      new Thread(new PrecacheRunnable(discreteZoom - 1)).start();
    }
    return results;
  }

  @Override
  public Collection<T> getItems() {
    return algorithm.getItems();
  }

  private Set<? extends Cluster<T>> getClustersInternal(int discreteZoom) {
    Set<? extends Cluster<T>> results;
    cacheLock.readLock().lock();
    results = cache.get(discreteZoom);
    cacheLock.readLock().unlock();

    if (results == null) {
      cacheLock.writeLock().lock();
      results = cache.get(discreteZoom);
      if (results == null) {
        results = algorithm.getClusters(discreteZoom);
        cache.put(discreteZoom, results);
      }
      cacheLock.writeLock().unlock();
    }
    return results;
  }

  private class PrecacheRunnable implements Runnable {
    private final int mZoom;

    public PrecacheRunnable(int zoom) {
      mZoom = zoom;
    }

    @Override
    public void run() {
      try {
        // Wait between 500 - 1000 ms.
        Thread.sleep((long) (Math.random() * 500 + 500));
      } catch (InterruptedException e) {
        // ignore. keep going.
      }
      getClustersInternal(mZoom);
    }
  }

}
