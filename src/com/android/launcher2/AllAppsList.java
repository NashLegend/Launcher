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
import java.util.List;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

/**
 * 存储"所有程序"的app的列表
 */
class AllAppsList {
	public static final int DEFAULT_APPLICATIONS_NUMBER = 42;

	/** The list off all apps. */
	public ArrayList<ApplicationInfo> data = new ArrayList<ApplicationInfo>(
			DEFAULT_APPLICATIONS_NUMBER);
	/** 上一次notify()之后安装的所有程序 */
	public ArrayList<ApplicationInfo> added = new ArrayList<ApplicationInfo>(
			DEFAULT_APPLICATIONS_NUMBER);
	/** 上一次notify()之后删除的所有程序 */
	public ArrayList<ApplicationInfo> removed = new ArrayList<ApplicationInfo>();
	/** 上一次notify()之后改变的所有程序 */
	public ArrayList<ApplicationInfo> modified = new ArrayList<ApplicationInfo>();

	private IconCache mIconCache;

	/**
	 * Boring constructor.
	 */
	public AllAppsList(IconCache iconCache) {
		mIconCache = iconCache;
	}

	/**
	 * 向列表中添加app，并在notify()调用时把它放到队列中。 如果列表中已经存在，那么不管他
	 */
	public void add(ApplicationInfo info) {
		if (findActivity(data, info.componentName)) {
			return;
		}
		data.add(info);
		added.add(info);
	}

	/**
	 * 清空全部列表
	 */
	public void clear() {
		data.clear();
		// TODO: do we clear these too?
		added.clear();
		removed.clear();
		modified.clear();
	}

	/**
	 * 返回程序数量
	 */
	public int size() {
		return data.size();
	}

	public ApplicationInfo get(int index) {
		return data.get(index);
	}

	/**
	 * Add the icons for the supplied apk called packageName. 为指定名称的apk添加图标
	 */
	public void addPackage(Context context, String packageName) {
		final List<ResolveInfo> matches = findActivitiesForPackage(context,
				packageName);

		if (matches.size() > 0) {
			for (ResolveInfo info : matches) {
				add(new ApplicationInfo(context.getPackageManager(), info,
						mIconCache, null));
			}
		}
	}

	/**
	 * Remove the apps for the given apk identified by packageName. 移除指定名称的app
	 */
	public void removePackage(String packageName) {
		final List<ApplicationInfo> data = this.data;
		for (int i = data.size() - 1; i >= 0; i--) {
			ApplicationInfo info = data.get(i);
			final ComponentName component = info.intent.getComponent();
			if (packageName.equals(component.getPackageName())) {
				removed.add(info);
				data.remove(i);
			}
		}
		// This is more aggressive than it needs to be.
		mIconCache.flush();
	}

	/**
	 * 重建列表
	 */
	public void updatePackage(Context context, String packageName) {
		final List<ResolveInfo> matches = findActivitiesForPackage(context,
				packageName);
		if (matches.size() > 0) {
			// Find disabled/removed activities and remove them from data and
			// add them
			// to the removed list.
			for (int i = data.size() - 1; i >= 0; i--) {
				final ApplicationInfo applicationInfo = data.get(i);
				final ComponentName component = applicationInfo.intent
						.getComponent();
				if (packageName.equals(component.getPackageName())) {
					if (!findActivity(matches, component)) {
						removed.add(applicationInfo);
						mIconCache.remove(component);
						data.remove(i);
					}
				}
			}

			// Find enabled activities and add them to the adapter
			// Also updates existing activities with new labels/icons
			int count = matches.size();
			for (int i = 0; i < count; i++) {
				final ResolveInfo info = matches.get(i);
				ApplicationInfo applicationInfo = findApplicationInfoLocked(
						info.activityInfo.applicationInfo.packageName,
						info.activityInfo.name);
				if (applicationInfo == null) {
					add(new ApplicationInfo(context.getPackageManager(), info,
							mIconCache, null));
				} else {
					mIconCache.remove(applicationInfo.componentName);
					mIconCache.getTitleAndIcon(applicationInfo, info, null);
					modified.add(applicationInfo);
				}
			}
		} else {
			// Remove all data for this package.
			for (int i = data.size() - 1; i >= 0; i--) {
				final ApplicationInfo applicationInfo = data.get(i);
				final ComponentName component = applicationInfo.intent
						.getComponent();
				if (packageName.equals(component.getPackageName())) {
					removed.add(applicationInfo);
					mIconCache.remove(component);
					data.remove(i);
				}
			}
		}
	}

	/**
	 * 返回指定名称的MAIN/LAUNCHER activities，不可能返回null
	 */
	private static List<ResolveInfo> findActivitiesForPackage(Context context,
			String packageName) {
		final PackageManager packageManager = context.getPackageManager();

		final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
		mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
		mainIntent.setPackage(packageName);

		final List<ResolveInfo> apps = packageManager.queryIntentActivities(
				mainIntent, 0);
		return apps != null ? apps : new ArrayList<ResolveInfo>();
	}

	/**
	 * 返回ResolveInfo列表中是否有这个app
	 */
	private static boolean findActivity(List<ResolveInfo> apps,
			ComponentName component) {
		final String className = component.getClassName();
		for (ResolveInfo info : apps) {
			final ActivityInfo activityInfo = info.activityInfo;
			if (activityInfo.name.equals(className)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 返回ApplicationInfo列表中是否有这个app
	 */
	private static boolean findActivity(ArrayList<ApplicationInfo> apps,
			ComponentName component) {
		final int N = apps.size();
		for (int i = 0; i < N; i++) {
			final ApplicationInfo info = apps.get(i);
			if (info.componentName.equals(component)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 找到符合指定的包名和类名的ApplicationInfo。
	 */
	private ApplicationInfo findApplicationInfoLocked(String packageName,
			String className) {
		for (ApplicationInfo info : data) {
			final ComponentName component = info.intent.getComponent();
			if (packageName.equals(component.getPackageName())
					&& className.equals(component.getClassName())) {
				return info;
			}
		}
		return null;
	}
}
