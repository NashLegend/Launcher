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

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 桌面上的一个小条目，有可能是应用、快捷方式、文件夹、插件
 */
class ItemInfo {

	static final int NO_ID = -1;

	/**
	 * The id in the settings database for this item 数据库中的id
	 */
	long id = NO_ID;

	/**
	 * 类型，有可能是应用、快捷方式、文件夹、插件
	 * {@link LauncherSettings.Favorites#ITEM_TYPE_APPLICATION},
	 * {@link LauncherSettings.Favorites#ITEM_TYPE_SHORTCUT},
	 * {@link LauncherSettings.Favorites#ITEM_TYPE_FOLDER}, or
	 * {@link LauncherSettings.Favorites#ITEM_TYPE_APPWIDGET}.
	 */
	int itemType;

	/**
	 * 放这个item的窗口，对于桌面来说，这个是
	 * {@link LauncherSettings.Favorites#CONTAINER_DESKTOP}
	 * 
	 * 对于“所有程序”界面来说是 {@link #NO_ID} (因为不用保存在数据库里)
	 * 
	 * 对于用户文件夹来说是文件夹id
	 */
	long container = NO_ID;

	/**
	 * 所在屏幕index
	 */
	int screen = -1;

	/**
	 * 在屏幕上的x位置
	 */
	int cellX = -1;

	/**
	 * 在屏幕上的y位置
	 */
	int cellY = -1;

	/**
	 * 宽度
	 */
	int spanX = 1;

	/**
	 * 高度
	 */
	int spanY = 1;

	/**
	 * 最小宽度
	 */
	int minSpanX = 1;

	/**
	 * 最小高度
	 */
	int minSpanY = 1;

	/**
	 * 这个item是否需要在数据库中升级
	 */
	boolean requiresDbUpdate = false;

	/**
	 * 标题
	 */
	CharSequence title;

	/**
	 * 在拖放操作中的位置
	 */
	int[] dropPos = null;

	ItemInfo() {
	}

	ItemInfo(ItemInfo info) {
		id = info.id;
		cellX = info.cellX;
		cellY = info.cellY;
		spanX = info.spanX;
		spanY = info.spanY;
		screen = info.screen;
		itemType = info.itemType;
		container = info.container;
		// tempdebug:
		LauncherModel.checkItemInfo(this);
	}

	/**
	 * 返回包名，不存在则为空
	 */
	static String getPackageName(Intent intent) {
		if (intent != null) {
			String packageName = intent.getPackage();
			if (packageName == null && intent.getComponent() != null) {
				packageName = intent.getComponent().getPackageName();
			}
			if (packageName != null) {
				return packageName;
			}
		}
		return "";
	}

	/**
	 * 保存数据到数据库
	 * 
	 * @param values
	 */
	void onAddToDatabase(ContentValues values) {
		values.put(LauncherSettings.BaseLauncherColumns.ITEM_TYPE, itemType);
		values.put(LauncherSettings.Favorites.CONTAINER, container);
		values.put(LauncherSettings.Favorites.SCREEN, screen);
		values.put(LauncherSettings.Favorites.CELLX, cellX);
		values.put(LauncherSettings.Favorites.CELLY, cellY);
		values.put(LauncherSettings.Favorites.SPANX, spanX);
		values.put(LauncherSettings.Favorites.SPANY, spanY);
	}

	/**
	 * 修改坐标
	 */
	void updateValuesWithCoordinates(ContentValues values, int cellX, int cellY) {
		values.put(LauncherSettings.Favorites.CELLX, cellX);
		values.put(LauncherSettings.Favorites.CELLY, cellY);
	}

	static byte[] flattenBitmap(Bitmap bitmap) {
		// 把图片转换成byte[]
		int size = bitmap.getWidth() * bitmap.getHeight() * 4;
		ByteArrayOutputStream out = new ByteArrayOutputStream(size);
		try {
			bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
			out.flush();
			out.close();
			return out.toByteArray();
		} catch (IOException e) {
			Log.w("Favorite", "Could not write icon");
			return null;
		}
	}

	static void writeBitmap(ContentValues values, Bitmap bitmap) {
		if (bitmap != null) {
			byte[] data = flattenBitmap(bitmap);
			values.put(LauncherSettings.Favorites.ICON, data);
		}
	}

	/**
	 * It is very important that sub-classes implement this if they contain any
	 * references to the activity (anything in the view hierarchy etc.). If not,
	 * leaks can result since ItemInfo objects persist across rotation and can
	 * hence leak by holding stale references to the old view hierarchy /
	 * activity.
	 * 如果子类持有任何activity的引用，那么一定要引用它，否则可能内存泄漏
	 */
	void unbind() {
	}

	@Override
	public String toString() {
		return "Item(id=" + this.id + " type=" + this.itemType + " container="
				+ this.container + " screen=" + screen + " cellX=" + cellX
				+ " cellY=" + cellY + " spanX=" + spanX + " spanY=" + spanY
				+ " dropPos=" + dropPos + ")";
	}
}
