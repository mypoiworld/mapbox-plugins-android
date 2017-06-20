package com.mapbox.mapboxsdk.plugins.cluster.clustering.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.util.SparseArray;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.Projection;
import com.mapbox.mapboxsdk.plugins.cluster.MarkerManager;
import com.mapbox.mapboxsdk.plugins.cluster.R;
import com.mapbox.mapboxsdk.plugins.cluster.clustering.Cluster;
import com.mapbox.mapboxsdk.plugins.cluster.clustering.ClusterItem;
import com.mapbox.mapboxsdk.plugins.cluster.clustering.ClusterManager;
import com.mapbox.mapboxsdk.plugins.cluster.geometry.Point;
import com.mapbox.mapboxsdk.plugins.cluster.projection.SphericalMercatorProjection;
import com.mapbox.mapboxsdk.plugins.cluster.ui.IconGenerator;
import com.mapbox.mapboxsdk.plugins.cluster.ui.SquareTextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.mapbox.mapboxsdk.plugins.cluster.clustering.algo.NonHierarchicalDistanceBasedAlgorithm.MAX_DISTANCE_AT_ZOOM;

/**
 * The default view for a ClusterManager. Markers are animated in and out of clusters.
 * <p>
 * Inspired by https://github.com/googlemaps/android-maps-utils.
 * </p>
 */
public class DefaultClusterRenderer<T extends ClusterItem> implements ClusterRenderer<T> {

  private static final boolean SHOULD_ANIMATE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
  private final MapboxMap map;
  private final IconGenerator iconGenerator;
  private final ClusterManager<T> clusterManager;
  private final float density;
  private boolean animate;

  private static final int[] BUCKETS = {10, 20, 50, 100, 200, 500, 1000};
  private ShapeDrawable coloredCircleBackground;

  /**
   * Markers that are currently on the map.
   */
  private Set<MarkerWithPosition> markers = Collections.newSetFromMap(
    new ConcurrentHashMap<MarkerWithPosition, Boolean>());

  /**
   * Icons for each bucket.
   */
  private SparseArray<Icon> icons = new SparseArray<>();

  /**
   * Markers for single ClusterItems.
   */
  private MarkerCache<T> markerCache = new MarkerCache<T>();

  /**
   * If cluster size is less than this size, display individual markers.
   */
  private int minClusterSize = 4;

  /**
   * The currently displayed set of clusters.
   */
  private Set<? extends Cluster<T>> clusters;

  /**
   * Lookup between markers and the associated cluster.
   */
  private Map<Marker, Cluster<T>> markerToCluster = new HashMap<Marker, Cluster<T>>();
  private Map<Cluster<T>, Marker> clusterToMarker = new HashMap<Cluster<T>, Marker>();

  /**
   * The target zoom level for the current set of clusters.
   */
  private float zoom;

  private final ViewModifier viewModifier = new ViewModifier();

  private ClusterManager.OnClusterClickListener<T> clickListener;
  private ClusterManager.OnClusterInfoWindowClickListener<T> infoWindowClickListener;
  private ClusterManager.OnClusterItemClickListener<T> itemClickListener;
  private ClusterManager.OnClusterItemInfoWindowClickListener<T> itemInfoWindowClickListener;

  public DefaultClusterRenderer(Context context, MapboxMap map, ClusterManager<T> clusterManager) {
    this.map = map;
    animate = true;
    density = context.getResources().getDisplayMetrics().density;
    iconGenerator = new IconGenerator(context);
    iconGenerator.setContentView(makeSquareTextView(context));
    iconGenerator.setTextAppearance(R.style.amu_ClusterIcon_TextAppearance);
    iconGenerator.setBackground(makeClusterBackground());
    this.clusterManager = clusterManager;
  }

  @Override
  public void onAdd() {
    clusterManager.getMarkerCollection().setOnMarkerClickListener(new MapboxMap.OnMarkerClickListener() {
      @Override
      public boolean onMarkerClick(Marker marker) {
        return itemClickListener != null && itemClickListener.onClusterItemClick(markerCache.get(marker));
      }
    });

    clusterManager.getMarkerCollection().setOnInfoWindowClickListener(new MapboxMap.OnInfoWindowClickListener() {
      @Override
      public boolean onInfoWindowClick(Marker marker) {
        if (itemInfoWindowClickListener != null) {
          itemInfoWindowClickListener.onClusterItemInfoWindowClick(markerCache.get(marker));
        }
        return true;
      }
    });

    clusterManager.getClusterMarkerCollection().setOnMarkerClickListener(new MapboxMap.OnMarkerClickListener() {
      @Override
      public boolean onMarkerClick(Marker marker) {
        return clickListener != null && clickListener.onClusterClick(markerToCluster.get(marker));
      }
    });

    clusterManager.getClusterMarkerCollection().setOnInfoWindowClickListener(new MapboxMap.OnInfoWindowClickListener() {
      @Override
      public boolean onInfoWindowClick(Marker marker) {
        if (infoWindowClickListener != null) {
          infoWindowClickListener.onClusterInfoWindowClick(markerToCluster.get(marker));
        }
        return true;
      }
    });
  }

  @Override
  public void onRemove() {
    clusterManager.getMarkerCollection().setOnMarkerClickListener(null);
    clusterManager.getMarkerCollection().setOnInfoWindowClickListener(null);
    clusterManager.getClusterMarkerCollection().setOnMarkerClickListener(null);
    clusterManager.getClusterMarkerCollection().setOnInfoWindowClickListener(null);
  }

  private LayerDrawable makeClusterBackground() {
    coloredCircleBackground = new ShapeDrawable(new OvalShape());
    ShapeDrawable outline = new ShapeDrawable(new OvalShape());
    outline.getPaint().setColor(0x80ffffff); // Transparent white.
    LayerDrawable background = new LayerDrawable(new Drawable[] {outline, coloredCircleBackground});
    int strokeWidth = (int) (density * 3);
    background.setLayerInset(1, strokeWidth, strokeWidth, strokeWidth, strokeWidth);
    return background;
  }

  private SquareTextView makeSquareTextView(Context context) {
    SquareTextView squareTextView = new SquareTextView(context);
    ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    squareTextView.setLayoutParams(layoutParams);
    squareTextView.setId(R.id.amu_text);
    int twelveDpi = (int) (12 * density);
    squareTextView.setPadding(twelveDpi, twelveDpi, twelveDpi, twelveDpi);
    return squareTextView;
  }

  protected int getColor(int clusterSize) {
    final float hueRange = 220;
    final float sizeRange = 300;
    final float size = Math.min(clusterSize, sizeRange);
    final float hue = (sizeRange - size) * (sizeRange - size) / (sizeRange * sizeRange) * hueRange;
    return Color.HSVToColor(new float[] {
      hue, 1f, .6f
    });
  }

  protected String getClusterText(int bucket) {
    if (bucket < BUCKETS[0]) {
      return String.valueOf(bucket);
    }
    return String.valueOf(bucket) + "+";
  }

  /**
   * Gets the "bucket" for a particular cluster. By default, uses the number of points within the
   * cluster, bucketed to some set points.
   */
  protected int getBucket(Cluster<T> cluster) {
    int size = cluster.getSize();
    if (size <= BUCKETS[0]) {
      return size;
    }
    for (int i = 0; i < BUCKETS.length - 1; i++) {
      if (size < BUCKETS[i + 1]) {
        return BUCKETS[i];
      }
    }
    return BUCKETS[BUCKETS.length - 1];
  }

  public int getMinClusterSize() {
    return minClusterSize;
  }

  public void setMinClusterSize(int minClusterSize) {
    this.minClusterSize = minClusterSize;
  }

  /**
   * ViewModifier ensures only one re-rendering of the view occurs at a time, and schedules
   * re-rendering, which is performed by the RenderTask.
   */
  @SuppressLint("HandlerLeak")
  private class ViewModifier extends Handler {
    private static final int RUN_TASK = 0;
    private static final int TASK_FINISHED = 1;
    private boolean mViewModificationInProgress = false;
    private RenderTask mNextClusters = null;

    @Override
    public void handleMessage(Message msg) {
      if (msg.what == TASK_FINISHED) {
        mViewModificationInProgress = false;
        if (mNextClusters != null) {
          // Run the task that was queued up.
          sendEmptyMessage(RUN_TASK);
        }
        return;
      }
      removeMessages(RUN_TASK);

      if (mViewModificationInProgress) {
        // Busy - wait for the callback.
        return;
      }

      if (mNextClusters == null) {
        // Nothing to do.
        return;
      }
      Projection projection = map.getProjection();

      RenderTask renderTask;
      synchronized (this) {
        renderTask = mNextClusters;
        mNextClusters = null;
        mViewModificationInProgress = true;
      }

      renderTask.setCallback(new Runnable() {
        @Override
        public void run() {
          sendEmptyMessage(TASK_FINISHED);
        }
      });
      renderTask.setProjection(projection);
      renderTask.setMapZoom((float) map.getCameraPosition().zoom);
      new Thread(renderTask).start();
    }

    public void queue(Set<? extends Cluster<T>> clusters) {
      synchronized (this) {
        // Overwrite any pending cluster tasks - we don't care about intermediate states.
        mNextClusters = new RenderTask(clusters);
      }
      sendEmptyMessage(RUN_TASK);
    }
  }

  /**
   * Determine whether the cluster should be rendered as individual markers or a cluster.
   */
  protected boolean shouldRenderAsCluster(Cluster<T> cluster) {
    return cluster.getSize() > minClusterSize;
  }

  /**
   * Transforms the current view (represented by DefaultClusterRenderer.clusters and DefaultClusterRenderer.zoom) to a
   * new zoom level and set of clusters.
   * <p/>
   * This must be run off the UI thread. Work is coordinated in the RenderTask, then queued up to
   * be executed by a MarkerModifier.
   * <p/>
   * There are three stages for the render:
   * <p/>
   * 1. Markers are added to the map
   * <p/>
   * 2. Markers are animated to their final position
   * <p/>
   * 3. Any old markers are removed from the map
   * <p/>
   * When zooming in, markers are animated out from the nearest existing cluster. When zooming
   * out, existing clusters are animated to the nearest new cluster.
   */
  private class RenderTask implements Runnable {
    final Set<? extends Cluster<T>> clusters;
    private Runnable mCallback;
    private Projection mProjection;
    private SphericalMercatorProjection mSphericalMercatorProjection;
    private float mMapZoom;

    private RenderTask(Set<? extends Cluster<T>> clusters) {
      this.clusters = clusters;
    }

    /**
     * A callback to be run when all work has been completed.
     *
     * @param callback
     */
    public void setCallback(Runnable callback) {
      mCallback = callback;
    }

    public void setProjection(Projection projection) {
      this.mProjection = projection;
    }

    public void setMapZoom(float zoom) {
      this.mMapZoom = zoom;
      this.mSphericalMercatorProjection = new SphericalMercatorProjection(256 * Math.pow(2, Math.min(zoom, DefaultClusterRenderer.this.zoom)));
    }

    @SuppressLint("NewApi")
    public void run() {
      if (clusters.equals(DefaultClusterRenderer.this.clusters)) {
        mCallback.run();
        return;
      }

      final MarkerModifier markerModifier = new MarkerModifier();

      final float zoom = mMapZoom;
      final boolean zoomingIn = zoom > DefaultClusterRenderer.this.zoom;
      final float zoomDelta = zoom - DefaultClusterRenderer.this.zoom;

      final Set<MarkerWithPosition> markersToRemove = markers;
      final LatLngBounds visibleBounds = mProjection.getVisibleRegion().latLngBounds;
      // TODO: Add some padding, so that markers can animate in from off-screen.

      // Find all of the existing clusters that are on-screen. These are candidates for
      // markers to animate from.
      List<Point> existingClustersOnScreen = null;
      if (DefaultClusterRenderer.this.clusters != null && SHOULD_ANIMATE) {
        existingClustersOnScreen = new ArrayList<Point>();
        for (Cluster<T> c : DefaultClusterRenderer.this.clusters) {
          if (shouldRenderAsCluster(c) && visibleBounds.contains(c.getPosition())) {
            Point point = mSphericalMercatorProjection.toPoint(c.getPosition());
            existingClustersOnScreen.add(point);
          }
        }
      }

      // Create the new markers and animate them to their new positions.
      final Set<MarkerWithPosition> newMarkers = Collections.newSetFromMap(
        new ConcurrentHashMap<MarkerWithPosition, Boolean>());
      for (Cluster<T> c : clusters) {
        boolean onScreen = visibleBounds.contains(c.getPosition());
        if (zoomingIn && onScreen && SHOULD_ANIMATE) {
          Point point = mSphericalMercatorProjection.toPoint(c.getPosition());
          Point closest = findClosestCluster(existingClustersOnScreen, point);
          if (closest != null && animate) {
            LatLng animateTo = mSphericalMercatorProjection.toLatLng(closest);
            markerModifier.add(true, new CreateMarkerTask(c, newMarkers, animateTo));
          } else {
            markerModifier.add(true, new CreateMarkerTask(c, newMarkers, null));
          }
        } else {
          markerModifier.add(onScreen, new CreateMarkerTask(c, newMarkers, null));
        }
      }

      // Wait for all markers to be added.
      markerModifier.waitUntilFree();

      // Don't remove any markers that were just added. This is basically anything that had
      // a hit in the MarkerCache.
      markersToRemove.removeAll(newMarkers);

      // Find all of the new clusters that were added on-screen. These are candidates for
      // markers to animate from.
      List<Point> newClustersOnScreen = null;
      if (SHOULD_ANIMATE) {
        newClustersOnScreen = new ArrayList<Point>();
        for (Cluster<T> c : clusters) {
          if (shouldRenderAsCluster(c) && visibleBounds.contains(c.getPosition())) {
            Point p = mSphericalMercatorProjection.toPoint(c.getPosition());
            newClustersOnScreen.add(p);
          }
        }
      }

      // Remove the old markers, animating them into clusters if zooming out.
      for (final MarkerWithPosition marker : markersToRemove) {
        boolean onScreen = visibleBounds.contains(marker.position);
        // Don't animate when zooming out more than 3 zoom levels.
        // TODO: drop animation based on speed of device & number of markers to animate.
        if (!zoomingIn && zoomDelta > -3 && onScreen && SHOULD_ANIMATE) {
          final Point point = mSphericalMercatorProjection.toPoint(marker.position);
          final Point closest = findClosestCluster(newClustersOnScreen, point);
          if (closest != null && animate) {
            LatLng animateTo = mSphericalMercatorProjection.toLatLng(closest);
            markerModifier.animateThenRemove(marker, marker.position, animateTo);
          } else {
            markerModifier.remove(true, marker.marker);
          }
        } else {
          markerModifier.remove(onScreen, marker.marker);
        }
      }

      markerModifier.waitUntilFree();

      markers = newMarkers;
      DefaultClusterRenderer.this.clusters = clusters;
      DefaultClusterRenderer.this.zoom = zoom;

      mCallback.run();
    }
  }

  @Override
  public void onClustersChanged(Set<? extends Cluster<T>> clusters) {
    viewModifier.queue(clusters);
  }

  @Override
  public void setOnClusterClickListener(ClusterManager.OnClusterClickListener<T> listener) {
    clickListener = listener;
  }

  @Override
  public void setOnClusterInfoWindowClickListener(ClusterManager.OnClusterInfoWindowClickListener<T> listener) {
    infoWindowClickListener = listener;
  }

  @Override
  public void setOnClusterItemClickListener(ClusterManager.OnClusterItemClickListener<T> listener) {
    itemClickListener = listener;
  }

  @Override
  public void setOnClusterItemInfoWindowClickListener(ClusterManager.OnClusterItemInfoWindowClickListener<T> listener) {
    itemInfoWindowClickListener = listener;
  }

  @Override
  public void setAnimation(boolean animate) {
    this.animate = animate;
  }

  private static double distanceSquared(Point a, Point b) {
    return (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y);
  }

  private static Point findClosestCluster(List<Point> markers, Point point) {
    if (markers == null || markers.isEmpty()) {
      return null;
    }

    // TODO: make this configurable.
    double minDistSquared = MAX_DISTANCE_AT_ZOOM * MAX_DISTANCE_AT_ZOOM;
    Point closest = null;
    for (Point candidate : markers) {
      double dist = distanceSquared(candidate, point);
      if (dist < minDistSquared) {
        closest = candidate;
        minDistSquared = dist;
      }
    }
    return closest;
  }

  /**
   * Handles all markerWithPosition manipulations on the map. Work (such as adding, removing, or
   * animating a markerWithPosition) is performed while trying not to block the rest of the app's
   * UI.
   */
  @SuppressLint("HandlerLeak")
  private class MarkerModifier extends Handler implements MessageQueue.IdleHandler {
    private static final int BLANK = 0;

    private final Lock lock = new ReentrantLock();
    private final Condition busyCondition = lock.newCondition();

    private Queue<CreateMarkerTask> mCreateMarkerTasks = new LinkedList<CreateMarkerTask>();
    private Queue<CreateMarkerTask> mOnScreenCreateMarkerTasks = new LinkedList<CreateMarkerTask>();
    private Queue<Marker> mRemoveMarkerTasks = new LinkedList<Marker>();
    private Queue<Marker> mOnScreenRemoveMarkerTasks = new LinkedList<Marker>();
    private Queue<AnimationTask> mAnimationTasks = new LinkedList<AnimationTask>();

    /**
     * Whether the idle listener has been added to the UI thread's MessageQueue.
     */
    private boolean mListenerAdded;

    private MarkerModifier() {
      super(Looper.getMainLooper());
    }

    /**
     * Creates markers for a cluster some time in the future.
     *
     * @param priority whether this operation should have priority.
     */
    public void add(boolean priority, CreateMarkerTask c) {
      lock.lock();
      sendEmptyMessage(BLANK);
      if (priority) {
        mOnScreenCreateMarkerTasks.add(c);
      } else {
        mCreateMarkerTasks.add(c);
      }
      lock.unlock();
    }

    /**
     * Removes a markerWithPosition some time in the future.
     *
     * @param priority whether this operation should have priority.
     * @param m        the markerWithPosition to remove.
     */
    public void remove(boolean priority, Marker m) {
      lock.lock();
      sendEmptyMessage(BLANK);
      if (priority) {
        mOnScreenRemoveMarkerTasks.add(m);
      } else {
        mRemoveMarkerTasks.add(m);
      }
      lock.unlock();
    }

    /**
     * Animates a markerWithPosition some time in the future.
     *
     * @param marker the markerWithPosition to animate.
     * @param from   the position to animate from.
     * @param to     the position to animate to.
     */
    public void animate(MarkerWithPosition marker, LatLng from, LatLng to) {
      lock.lock();
      mAnimationTasks.add(new AnimationTask(marker, from, to));
      lock.unlock();
    }

    /**
     * Animates a markerWithPosition some time in the future, and removes it when the animation
     * is complete.
     *
     * @param marker the markerWithPosition to animate.
     * @param from   the position to animate from.
     * @param to     the position to animate to.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void animateThenRemove(MarkerWithPosition marker, LatLng from, LatLng to) {
      lock.lock();
      AnimationTask animationTask = new AnimationTask(marker, from, to);
      animationTask.removeOnAnimationComplete(clusterManager.getMarkerManager());
      mAnimationTasks.add(animationTask);
      lock.unlock();
    }

    @Override
    public void handleMessage(Message msg) {
      if (!mListenerAdded) {
        Looper.myQueue().addIdleHandler(this);
        mListenerAdded = true;
      }
      removeMessages(BLANK);

      lock.lock();
      try {

        // Perform up to 10 tasks at once.
        // Consider only performing 10 remove tasks, not adds and animations.
        // Removes are relatively slow and are much better when batched.
        for (int i = 0; i < 10; i++) {
          performNextTask();
        }

        if (!isBusy()) {
          mListenerAdded = false;
          Looper.myQueue().removeIdleHandler(this);
          // Signal any other threads that are waiting.
          busyCondition.signalAll();
        } else {
          // Sometimes the idle queue may not be called - schedule up some work regardless
          // of whether the UI thread is busy or not.
          // TODO: try to remove this.
          sendEmptyMessageDelayed(BLANK, 10);
        }
      } finally {
        lock.unlock();
      }
    }

    /**
     * Perform the next task. Prioritise any on-screen work.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void performNextTask() {
      if (!mOnScreenRemoveMarkerTasks.isEmpty()) {
        removeMarker(mOnScreenRemoveMarkerTasks.poll());
      } else if (!mAnimationTasks.isEmpty()) {
        mAnimationTasks.poll().perform();
      } else if (!mOnScreenCreateMarkerTasks.isEmpty()) {
        mOnScreenCreateMarkerTasks.poll().perform(this);
      } else if (!mCreateMarkerTasks.isEmpty()) {
        mCreateMarkerTasks.poll().perform(this);
      } else if (!mRemoveMarkerTasks.isEmpty()) {
        removeMarker(mRemoveMarkerTasks.poll());
      }
    }

    private void removeMarker(Marker m) {
      Cluster<T> cluster = markerToCluster.get(m);
      clusterToMarker.remove(cluster);
      markerCache.remove(m);
      markerToCluster.remove(m);
      clusterManager.getMarkerManager().remove(m);
    }

    /**
     * @return true if there is still work to be processed.
     */
    public boolean isBusy() {
      try {
        lock.lock();
        return !(mCreateMarkerTasks.isEmpty() && mOnScreenCreateMarkerTasks.isEmpty() &&
          mOnScreenRemoveMarkerTasks.isEmpty() && mRemoveMarkerTasks.isEmpty() &&
          mAnimationTasks.isEmpty()
        );
      } finally {
        lock.unlock();
      }
    }

    /**
     * Blocks the calling thread until all work has been processed.
     */
    public void waitUntilFree() {
      while (isBusy()) {
        // Sometimes the idle queue may not be called - schedule up some work regardless
        // of whether the UI thread is busy or not.
        // TODO: try to remove this.
        sendEmptyMessage(BLANK);
        lock.lock();
        try {
          if (isBusy()) {
            busyCondition.await();
          }
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        } finally {
          lock.unlock();
        }
      }
    }

    @Override
    public boolean queueIdle() {
      // When the UI is not busy, schedule some work.
      sendEmptyMessage(BLANK);
      return true;
    }
  }

  /**
   * A cache of markers representing individual ClusterItems.
   */
  private static class MarkerCache<T> {
    private Map<T, Marker> mCache = new HashMap<T, Marker>();
    private Map<Marker, T> mCacheReverse = new HashMap<Marker, T>();

    public Marker get(T item) {
      return mCache.get(item);
    }

    public T get(Marker m) {
      return mCacheReverse.get(m);
    }

    public void put(T item, Marker m) {
      mCache.put(item, m);
      mCacheReverse.put(m, item);
    }

    public void remove(Marker m) {
      T item = mCacheReverse.get(m);
      mCacheReverse.remove(m);
      mCache.remove(item);
    }
  }

  /**
   * Called before the marker for a ClusterItem is added to the map.
   */
  protected void onBeforeClusterItemRendered(T item, MarkerOptions markerOptions) {
  }

  /**
   * Called before the marker for a Cluster is added to the map.
   * The default implementation draws a circle with a rough count of the number of items.
   */
  protected void onBeforeClusterRendered(Cluster<T> cluster, MarkerOptions markerOptions) {
    int bucket = getBucket(cluster);
    Icon descriptor = icons.get(bucket);
    if (descriptor == null) {
      coloredCircleBackground.getPaint().setColor(getColor(bucket));
      descriptor = IconFactory.getInstance(Mapbox.getApplicationContext())
        .fromBitmap(iconGenerator.makeIcon(getClusterText(bucket)));
      icons.put(bucket, descriptor);
    }
    // TODO: consider adding anchor(.5, .5) (Individual markers will overlap more often)
    markerOptions.icon(descriptor);
  }

  /**
   * Called after the marker for a Cluster has been added to the map.
   */
  protected void onClusterRendered(Cluster<T> cluster, Marker marker) {
  }

  /**
   * Called after the marker for a ClusterItem has been added to the map.
   */
  protected void onClusterItemRendered(T clusterItem, Marker marker) {
  }

  /**
   * Get the marker from a ClusterItem
   *
   * @param clusterItem ClusterItem which you will obtain its marker
   * @return a marker from a ClusterItem or null if it does not exists
   */
  public Marker getMarker(T clusterItem) {
    return markerCache.get(clusterItem);
  }

  /**
   * Get the ClusterItem from a marker
   *
   * @param marker which you will obtain its ClusterItem
   * @return a ClusterItem from a marker or null if it does not exists
   */
  public T getClusterItem(Marker marker) {
    return markerCache.get(marker);
  }

  /**
   * Get the marker from a Cluster
   *
   * @param cluster which you will obtain its marker
   * @return a marker from a cluster or null if it does not exists
   */
  public Marker getMarker(Cluster<T> cluster) {
    return clusterToMarker.get(cluster);
  }

  /**
   * Get the Cluster from a marker
   *
   * @param marker which you will obtain its Cluster
   * @return a Cluster from a marker or null if it does not exists
   */
  public Cluster<T> getCluster(Marker marker) {
    return markerToCluster.get(marker);
  }

  /**
   * Creates markerWithPosition(s) for a particular cluster, animating it if necessary.
   */
  private class CreateMarkerTask {
    private final Cluster<T> cluster;
    private final Set<MarkerWithPosition> newMarkers;
    private final LatLng animateFrom;

    /**
     * @param c            the cluster to render.
     * @param markersAdded a collection of markers to append any created markers.
     * @param animateFrom  the location to animate the markerWithPosition from, or null if no
     *                     animation is required.
     */
    public CreateMarkerTask(Cluster<T> c, Set<MarkerWithPosition> markersAdded, LatLng animateFrom) {
      this.cluster = c;
      this.newMarkers = markersAdded;
      this.animateFrom = animateFrom;
    }

    private void perform(MarkerModifier markerModifier) {
      // Don't show small clusters. Render the markers inside, instead.
      if (!shouldRenderAsCluster(cluster)) {
        for (T item : cluster.getItems()) {
          Marker marker = markerCache.get(item);
          MarkerWithPosition markerWithPosition;
          if (marker == null) {
            MarkerOptions markerOptions = new MarkerOptions();
            if (animateFrom != null) {
              markerOptions.position(animateFrom);
            } else {
              markerOptions.position(item.getPosition());
            }
            if (!(item.getTitle() == null) && !(item.getSnippet() == null)) {
              markerOptions.title(item.getTitle());
              markerOptions.snippet(item.getSnippet());
            } else if (!(item.getSnippet() == null)) {
              markerOptions.title(item.getSnippet());
            } else if (!(item.getTitle() == null)) {
              markerOptions.title(item.getTitle());
            }
            onBeforeClusterItemRendered(item, markerOptions);
            marker = clusterManager.getMarkerCollection().addMarker(markerOptions);
            markerWithPosition = new MarkerWithPosition(marker);
            markerCache.put(item, marker);
            if (animateFrom != null) {
              markerModifier.animate(markerWithPosition, animateFrom, item.getPosition());
            }
          } else {
            markerWithPosition = new MarkerWithPosition(marker);
          }
          onClusterItemRendered(item, marker);
          newMarkers.add(markerWithPosition);
        }
        return;
      }

      Marker marker = clusterToMarker.get(cluster);
      MarkerWithPosition markerWithPosition;
      if (marker == null) {
        MarkerOptions markerOptions = new MarkerOptions().
          position(animateFrom == null ? cluster.getPosition() : animateFrom);
        onBeforeClusterRendered(cluster, markerOptions);
        marker = clusterManager.getClusterMarkerCollection().addMarker(markerOptions);
        markerToCluster.put(marker, cluster);
        clusterToMarker.put(cluster, marker);
        markerWithPosition = new MarkerWithPosition(marker);
        if (animateFrom != null) {
          markerModifier.animate(markerWithPosition, animateFrom, cluster.getPosition());
        }
      } else {
        markerWithPosition = new MarkerWithPosition(marker);
      }
      onClusterRendered(cluster, marker);
      newMarkers.add(markerWithPosition);
    }
  }

  /**
   * A Marker and its position. Marker.getPosition() must be called from the UI thread, so this
   * object allows lookup from other threads.
   */
  private static class MarkerWithPosition {
    private final Marker marker;
    private LatLng position;

    private MarkerWithPosition(Marker marker) {
      this.marker = marker;
      position = marker.getPosition();
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof MarkerWithPosition) {
        return marker.equals(((MarkerWithPosition) other).marker);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return marker.hashCode();
    }
  }

  private static final TimeInterpolator ANIMATION_INTERP = new DecelerateInterpolator();

  /**
   * Animates a markerWithPosition from one position to another. TODO: improve performance for
   * slow devices (e.g. Nexus S).
   */
  @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
  private class AnimationTask extends AnimatorListenerAdapter implements ValueAnimator.AnimatorUpdateListener {
    private final MarkerWithPosition markerWithPosition;
    private final Marker marker;
    private final LatLng from;
    private final LatLng to;
    private boolean mRemoveOnComplete;
    private MarkerManager mMarkerManager;

    private AnimationTask(MarkerWithPosition markerWithPosition, LatLng from, LatLng to) {
      this.markerWithPosition = markerWithPosition;
      this.marker = markerWithPosition.marker;
      this.from = from;
      this.to = to;
    }

    public void perform() {
      ValueAnimator valueAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
      valueAnimator.setInterpolator(ANIMATION_INTERP);
      valueAnimator.addUpdateListener(this);
      valueAnimator.addListener(this);
      valueAnimator.start();
    }

    @Override
    public void onAnimationEnd(Animator animation) {
      if (mRemoveOnComplete) {
        Cluster<T> cluster = markerToCluster.get(marker);
        clusterToMarker.remove(cluster);
        markerCache.remove(marker);
        markerToCluster.remove(marker);
        mMarkerManager.remove(marker);
      }
      markerWithPosition.position = to;
    }

    public void removeOnAnimationComplete(MarkerManager markerManager) {
      mMarkerManager = markerManager;
      mRemoveOnComplete = true;
    }

    @Override
    public void onAnimationUpdate(ValueAnimator valueAnimator) {
      float fraction = valueAnimator.getAnimatedFraction();
      double lat = (to.getLatitude() - from.getLatitude()) * fraction + from.getLatitude();
      double lngDelta = to.getLongitude() - from.getLongitude();

      // Take the shortest path across the 180th meridian.
      if (Math.abs(lngDelta) > 180) {
        lngDelta -= Math.signum(lngDelta) * 360;
      }
      double lng = lngDelta * fraction + from.getLongitude();
      LatLng position = new LatLng(lat, lng);
      marker.setPosition(position);
    }
  }
}
