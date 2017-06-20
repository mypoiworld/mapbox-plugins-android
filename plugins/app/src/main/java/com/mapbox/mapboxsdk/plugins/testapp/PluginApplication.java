package com.mapbox.mapboxsdk.plugins.testapp;

import android.app.Application;

import com.mapbox.mapboxsdk.Mapbox;
import com.squareup.leakcanary.LeakCanary;

import timber.log.Timber;

public class PluginApplication extends Application {

  @Override
  public void onCreate() {
    super.onCreate();
    if (LeakCanary.isInAnalyzerProcess(this)) {
      // This process is dedicated to LeakCanary for heap analysis.
      // You should not init your app in this process.
      return;
    }

    if (BuildConfig.DEBUG) {
      Timber.plant(new Timber.DebugTree());
    }

    LeakCanary.install(this);
    Mapbox.getInstance(this, getString(R.string.mapbox_access_token));
  }
}
