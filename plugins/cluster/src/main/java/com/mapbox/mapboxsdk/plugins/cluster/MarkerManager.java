package com.mapbox.mapboxsdk.plugins.cluster;

import android.view.View;

import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.maps.MapboxMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// TODO: add OnMarkerDragListener

/**
 * Keeps track of collections of markers on the map. Delegates all Marker-related events to each
 * collection's individually managed listeners.
 * <p/>
 * All marker operations (adds and removes) should occur via its collection class. That is, don't
 * add a marker via a collection, then remove it via Marker.remove()
 * <p>
 * Inspired by https://github.com/googlemaps/android-maps-utils.
 * </p>
 */
public class MarkerManager implements MapboxMap.OnInfoWindowClickListener, MapboxMap.OnMarkerClickListener /**,MapboxMap.OnMarkerDragListener**/, MapboxMap.InfoWindowAdapter {
  private final MapboxMap map;

  private final Map<String, Collection> namedcollections = new HashMap<>();
  private final Map<Marker, Collection> allMarkers = new HashMap<>();

  public MarkerManager(MapboxMap map) {
    this.map = map;
  }

  public Collection newCollection() {
    return new Collection();
  }

  /**
   * Create a new named collection, which can later be looked up by {@link #getCollection(String)}
   *
   * @param id a unique id for this collection.
   */
  public Collection newCollection(String id) {
    if (namedcollections.get(id) != null) {
      throw new IllegalArgumentException("collection id is not unique: " + id);
    }
    Collection collection = new Collection();
    namedcollections.put(id, collection);
    return collection;
  }

  /**
   * Gets a named collection that was created by {@link #newCollection(String)}
   *
   * @param id the unique id for this collection.
   */
  public Collection getCollection(String id) {
    return namedcollections.get(id);
  }

  @Override
  public View getInfoWindow(Marker marker) {
    Collection collection = allMarkers.get(marker);
    if (collection != null && collection.infoWindowAdapter != null) {
      return collection.infoWindowAdapter.getInfoWindow(marker);
    }
    return null;
  }

  // UNSUPPORTED
  //    @Override
  //    public View getInfoContents(Marker marker) {
  //        Collection collection = allMarkers.get(marker);
  //        if (collection != null && collection.infoWindowAdapter != null) {
  //            return collection.infoWindowAdapter.getInfoContents(marker);
  //        }
  //        return null;
  //    }

  @Override
  public boolean onInfoWindowClick(Marker marker) {
    Collection collection = allMarkers.get(marker);
    if (collection != null && collection.infoWindowClickListener != null) {
      collection.infoWindowClickListener.onInfoWindowClick(marker);
    }
    return true;
  }

  @Override
  public boolean onMarkerClick(Marker marker) {
    Collection collection = allMarkers.get(marker);
    if (collection != null && collection.markerClickListener != null) {
      return collection.markerClickListener.onMarkerClick(marker);
    }
    return false;
  }

  // UNSUPPORTED
  //    @Override
  //    public void onMarkerDragStart(Marker marker) {
  //        Collection collection = allMarkers.get(marker);
  //        if (collection != null && collection.mMarkerDragListener != null) {
  //            collection.mMarkerDragListener.onMarkerDragStart(marker);
  //        }
  //    }
  //
  //    @Override
  //    public void onMarkerDrag(Marker marker) {
  //        Collection collection = allMarkers.get(marker);
  //        if (collection != null && collection.mMarkerDragListener != null) {
  //            collection.mMarkerDragListener.onMarkerDrag(marker);
  //        }
  //    }
  //
  //    @Override
  //    public void onMarkerDragEnd(Marker marker) {
  //        Collection collection = allMarkers.get(marker);
  //        if (collection != null && collection.mMarkerDragListener != null) {
  //            collection.mMarkerDragListener.onMarkerDragEnd(marker);
  //        }
  //    }

  /**
   * Removes a marker from its collection.
   *
   * @param marker the marker to remove.
   * @return true if the marker was removed.
   */
  public boolean remove(Marker marker) {
    Collection collection = allMarkers.get(marker);
    return collection != null && collection.remove(marker);
  }

  public class Collection {
    private final Set<Marker> markers = new HashSet<Marker>();
    private MapboxMap.OnInfoWindowClickListener infoWindowClickListener;
    private MapboxMap.OnMarkerClickListener markerClickListener;
    //private MapboxMap.OnMarkerDragListener mMarkerDragListener;
    private MapboxMap.InfoWindowAdapter infoWindowAdapter;

    public Collection() {
    }

    public Marker addMarker(MarkerOptions opts) {
      Marker marker = map.addMarker(opts);
      markers.add(marker);
      allMarkers.put(marker, Collection.this);
      return marker;
    }

    public boolean remove(Marker marker) {
      if (markers.remove(marker)) {
        allMarkers.remove(marker);
        marker.remove();
        return true;
      }
      return false;
    }

    public void clear() {
      for (Marker marker : markers) {
        marker.remove();
        allMarkers.remove(marker);
      }
      markers.clear();
    }

    public java.util.Collection<Marker> getMarkers() {
      return Collections.unmodifiableCollection(markers);
    }

    public void setOnInfoWindowClickListener(MapboxMap.OnInfoWindowClickListener infoWindowClickListener) {
      this.infoWindowClickListener = infoWindowClickListener;
    }

    public void setOnMarkerClickListener(MapboxMap.OnMarkerClickListener markerClickListener) {
      this.markerClickListener = markerClickListener;
    }

    // UNSUPPORTED
    //    public void setOnMarkerDragListener(MapboxMap.OnMarkerDragListener markerDragListener) {
    //      mMarkerDragListener = markerDragListener;
    //    }

    public void setOnInfoWindowAdapter(MapboxMap.InfoWindowAdapter infoWindowAdapter) {
      this.infoWindowAdapter = infoWindowAdapter;
    }
  }

}
