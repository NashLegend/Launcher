/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher2;

import android.app.Application;
import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.os.Handler;

import com.android.launcher.R;

import java.lang.ref.WeakReference;

/**
 * 整个app的application,主要负责一些广播和侦听的添加
 */
public class LauncherApplication extends Application {
	public LauncherModel mModel;
	public IconCache mIconCache;
	private static boolean sIsScreenLarge;
	private static float sScreenDensity;
	private static int sLongPressTimeout = 300;
	private static final String sSharedPreferencesKey = "com.android.launcher2.prefs";
	WeakReference<LauncherProvider> mLauncherProvider;

	@Override
	public void onCreate() {
		super.onCreate();

		// 创建图标缓存之前取得 sIsScreenXLarge 和 sScreenDensity
		// 屏幕宽度大于600则属于大屏幕
		sIsScreenLarge = getResources().getBoolean(R.bool.is_large_screen);
		sScreenDensity = getResources().getDisplayMetrics().density;

		mIconCache = new IconCache(this);
		mModel = new LauncherModel(this, mIconCache);

		// Register intent receivers
		// 注册广播接收器
		IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
		filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
		filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
		filter.addDataScheme("package");
		registerReceiver(mModel, filter);
		filter = new IntentFilter();
		filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
		filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
		filter.addAction(Intent.ACTION_LOCALE_CHANGED);
		filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
		registerReceiver(mModel, filter);
		filter = new IntentFilter();
		filter.addAction(SearchManager.INTENT_GLOBAL_SEARCH_ACTIVITY_CHANGED);
		registerReceiver(mModel, filter);
		filter = new IntentFilter();
		filter.addAction(SearchManager.INTENT_ACTION_SEARCHABLES_CHANGED);
		registerReceiver(mModel, filter);

		// Register for changes to the favorites
		// 数据变动侦听
		ContentResolver resolver = getContentResolver();
		resolver.registerContentObserver(
				LauncherSettings.Favorites.CONTENT_URI, true,
				mFavoritesObserver);
	}

	/**
	 * 不能保证这个函数会被执行到。执行到的话，取消对一堆intent的注册的数据的侦听
	 */
	@Override
	public void onTerminate() {
		super.onTerminate();

		unregisterReceiver(mModel);

		ContentResolver resolver = getContentResolver();
		resolver.unregisterContentObserver(mFavoritesObserver);
	}

	/**
	 * Receives notifications whenever the user favorites have changed.
	 * 每当user favorites发生变化时都会收到通知并执行这个onChange……
	 */
	private final ContentObserver mFavoritesObserver = new ContentObserver(
			new Handler()) {
		@Override
		public void onChange(boolean selfChange) {
			// If the database has ever changed, then we really need to force a
			// reload of the
			// workspace on the next load
			mModel.resetLoadedState(false, true);
			mModel.startLoaderFromBackground();
		}
	};

	LauncherModel setLauncher(Launcher launcher) {
		mModel.initialize(launcher);
		return mModel;
	}

	IconCache getIconCache() {
		return mIconCache;
	}

	LauncherModel getModel() {
		return mModel;
	}

	void setLauncherProvider(LauncherProvider provider) {
		mLauncherProvider = new WeakReference<LauncherProvider>(provider);
	}

	LauncherProvider getLauncherProvider() {
		return mLauncherProvider.get();
	}

	public static String getSharedPreferencesKey() {
		return sSharedPreferencesKey;
	}

	public static boolean isScreenLarge() {
		return sIsScreenLarge;
	}

	public static boolean isScreenLandscape(Context context) {
		return context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
	}

	public static float getScreenDensity() {
		return sScreenDensity;
	}

	public static int getLongPressTimeout() {
		return sLongPressTimeout;
	}
}
