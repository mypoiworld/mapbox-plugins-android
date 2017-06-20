package com.mapbox.mapboxsdk.plugins.cluster.quadtree;

import com.mapbox.mapboxsdk.plugins.cluster.geometry.Bounds;
import com.mapbox.mapboxsdk.plugins.cluster.geometry.Point;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A quad tree which tracks items with a Point geometry.
 * See http://en.wikipedia.org/wiki/Quadtree for details on the data structure.
 * This class is not thread safe.
 * <p>
 * Inspired by https://github.com/googlemaps/android-maps-utils.
 * </p>
 */
public class PointQuadTree<T extends PointQuadTree.Item> {
  public interface Item {
    public Point getPoint();
  }

  /**
   * The bounds of this quad.
   */
  private final Bounds bounds;

  /**
   * The depth of this quad in the tree.
   */
  private final int depth;

  /**
   * Maximum number of elements to store in a quad before splitting.
   */
  private final static int MAX_ELEMENTS = 50;

  /**
   * The elements inside this quad, if any.
   */
  private List<T> items;

  /**
   * Maximum depth.
   */
  private final static int MAX_DEPTH = 40;

  /**
   * Child quads.
   */
  private List<PointQuadTree<T>> children;

  /**
   * Creates a new quad tree with specified bounds.
   *
   * @param minX
   * @param maxX
   * @param minY
   * @param maxY
   */
  public PointQuadTree(double minX, double maxX, double minY, double maxY) {
    this(new Bounds(minX, maxX, minY, maxY));
  }

  public PointQuadTree(Bounds bounds) {
    this(bounds, 0);
  }

  private PointQuadTree(double minX, double maxX, double minY, double maxY, int depth) {
    this(new Bounds(minX, maxX, minY, maxY), depth);
  }

  private PointQuadTree(Bounds bounds, int depth) {
    this.bounds = bounds;
    this.depth = depth;
  }

  /**
   * Insert an item.
   */
  public void add(T item) {
    Point point = item.getPoint();
    if (this.bounds.contains(point.x, point.y)) {
      insert(point.x, point.y, item);
    }
  }

  private void insert(double x, double y, T item) {
    if (this.children != null) {
      if (y < bounds.midY) {
        if (x < bounds.midX) { // top left
          children.get(0).insert(x, y, item);
        } else { // top right
          children.get(1).insert(x, y, item);
        }
      } else {
        if (x < bounds.midX) { // bottom left
          children.get(2).insert(x, y, item);
        } else {
          children.get(3).insert(x, y, item);
        }
      }
      return;
    }
    if (items == null) {
      items = new ArrayList<T>();
    }
    items.add(item);
    if (items.size() > MAX_ELEMENTS && depth < MAX_DEPTH) {
      split();
    }
  }

  /**
   * Split this quad.
   */
  private void split() {
    children = new ArrayList<PointQuadTree<T>>(4);
    children.add(new PointQuadTree<T>(bounds.minX, bounds.midX, bounds.minY, bounds.midY, depth + 1));
    children.add(new PointQuadTree<T>(bounds.midX, bounds.maxX, bounds.minY, bounds.midY, depth + 1));
    children.add(new PointQuadTree<T>(bounds.minX, bounds.midX, bounds.midY, bounds.maxY, depth + 1));
    children.add(new PointQuadTree<T>(bounds.midX, bounds.maxX, bounds.midY, bounds.maxY, depth + 1));

    List<T> items = this.items;
    this.items = null;

    for (T item : items) {
      // re-insert items into child quads.
      insert(item.getPoint().x, item.getPoint().y, item);
    }
  }

  /**
   * Remove the given item from the set.
   *
   * @return whether the item was removed.
   */
  public boolean remove(T item) {
    Point point = item.getPoint();
    if (this.bounds.contains(point.x, point.y)) {
      return remove(point.x, point.y, item);
    } else {
      return false;
    }
  }

  private boolean remove(double x, double y, T item) {
    if (this.children != null) {
      if (y < bounds.midY) {
        if (x < bounds.midX) { // top left
          return children.get(0).remove(x, y, item);
        } else { // top right
          return children.get(1).remove(x, y, item);
        }
      } else {
        if (x < bounds.midX) { // bottom left
          return children.get(2).remove(x, y, item);
        } else {
          return children.get(3).remove(x, y, item);
        }
      }
    } else {
      if (items == null) {
        return false;
      } else {
        return items.remove(item);
      }
    }
  }

  /**
   * Removes all points from the quadTree
   */
  public void clear() {
    children = null;
    if (items != null) {
      items.clear();
    }
  }

  /**
   * Search for all items within a given bounds.
   */
  public Collection<T> search(Bounds searchBounds) {
    final List<T> results = new ArrayList<T>();
    search(searchBounds, results);
    return results;
  }

  private void search(Bounds searchBounds, Collection<T> results) {
    if (!bounds.intersects(searchBounds)) {
      return;
    }

    if (this.children != null) {
      for (PointQuadTree<T> quad : children) {
        quad.search(searchBounds, results);
      }
    } else if (items != null) {
      if (searchBounds.contains(bounds)) {
        results.addAll(items);
      } else {
        for (T item : items) {
          if (searchBounds.contains(item.getPoint())) {
            results.add(item);
          }
        }
      }
    }
  }
}
