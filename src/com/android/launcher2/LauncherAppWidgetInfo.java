/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.appwidget.AppWidgetHostView;
import android.content.ComponentName;
import android.content.ContentValues;

/**
 * 启动器里的widget，有可能被添加也有可能将被添加
 */
class LauncherAppWidgetInfo extends ItemInfo {

	/**
	 * 尚未实例化（添加到桌面）
	 */
	static final int NO_ID = -1;

	/**
	 * Identifier for this widget when talking with
	 * {@link android.appwidget.AppWidgetManager} for updates.
	 */
	int appWidgetId = NO_ID;

	ComponentName providerName;

	// TODO: Are these necessary here?
	int minWidth = -1;
	int minHeight = -1;

	private boolean mHasNotifiedInitialWidgetSizeChanged;

	/**
	 * View that holds this widget after it's been created. This view isn't
	 * created until Launcher knows it's needed.
	 */
	AppWidgetHostView hostView = null;

	LauncherAppWidgetInfo(int appWidgetId, ComponentName providerName) {
		itemType = LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
		this.appWidgetId = appWidgetId;
		this.providerName = providerName;

		// Since the widget isn't instantiated yet, we don't know these values.
		// Set them to -1
		// to indicate that they should be calculated based on the layout and
		// minWidth/minHeight
		spanX = -1;
		spanY = -1;
	}

	@Override
	void onAddToDatabase(ContentValues values) {
		super.onAddToDatabase(values);
		values.put(LauncherSettings.Favorites.APPWIDGET_ID, appWidgetId);
	}

	/**
	 * When we bind the widget, we should notify the widget that the size has
	 * changed if we have not done so already (only really for default workspace
	 * widgets).
	 */
	void onBindAppWidget(Launcher launcher) {
		if (!mHasNotifiedInitialWidgetSizeChanged) {
			notifyWidgetSizeChanged(launcher);
		}
	}

	/**
	 * 通知发生了大小变化
	 */
	void notifyWidgetSizeChanged(Launcher launcher) {
		AppWidgetResizeFrame.updateWidgetSizeRanges(hostView, launcher, spanX,
				spanY);
		mHasNotifiedInitialWidgetSizeChanged = true;
	}

	@Override
	public String toString() {
		return "AppWidget(id=" + Integer.toString(appWidgetId) + ")";
	}

	@Override
	void unbind() {
		super.unbind();
		hostView = null;
	}
}
