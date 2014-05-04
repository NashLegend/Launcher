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

import java.util.ArrayList;

import android.content.ContentValues;

/**
 * 桌面上的文件夹，里面是应用程序快捷方式
 */
class FolderInfo extends ItemInfo {

	/**
	 * 是否处于打开状态
	 */
	boolean opened;

	/**
	 * The apps and shortcuts
	 */
	ArrayList<ShortcutInfo> contents = new ArrayList<ShortcutInfo>();

	ArrayList<FolderListener> listeners = new ArrayList<FolderListener>();

	FolderInfo() {
		itemType = LauncherSettings.Favorites.ITEM_TYPE_FOLDER;
	}

	/**
	 * 添加一个快捷方式
	 * 
	 * @param item
	 */
	public void add(ShortcutInfo item) {
		contents.add(item);
		for (int i = 0; i < listeners.size(); i++) {
			listeners.get(i).onAdd(item);
		}
		itemsChanged();
	}

	/**
	 * 删除一个app，不修改数据库
	 * 
	 * @param item
	 */
	public void remove(ShortcutInfo item) {
		contents.remove(item);
		for (int i = 0; i < listeners.size(); i++) {
			listeners.get(i).onRemove(item);
		}
		itemsChanged();
	}

	/**
	 * 修改标题
	 */
	public void setTitle(CharSequence title) {
		this.title = title;
		for (int i = 0; i < listeners.size(); i++) {
			listeners.get(i).onTitleChanged(title);
		}
	}

	@Override
	void onAddToDatabase(ContentValues values) {
		super.onAddToDatabase(values);
		values.put(LauncherSettings.Favorites.TITLE, title.toString());
	}

	void addListener(FolderListener listener) {
		listeners.add(listener);
	}

	void removeListener(FolderListener listener) {
		if (listeners.contains(listener)) {
			listeners.remove(listener);
		}
	}

	void itemsChanged() {
		for (int i = 0; i < listeners.size(); i++) {
			listeners.get(i).onItemsChanged();
		}
	}

	@Override
	void unbind() {
		super.unbind();
		listeners.clear();
	}

	interface FolderListener {
		public void onAdd(ShortcutInfo item);

		public void onRemove(ShortcutInfo item);

		public void onTitleChanged(CharSequence title);

		public void onItemsChanged();
	}
}
