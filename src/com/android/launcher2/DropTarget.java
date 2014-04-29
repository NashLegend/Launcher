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

import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.Log;

/**
 * Interface defining an object that can receive a drag. 接口，定义了一个可以接收拖动对象的对象
 */
public interface DropTarget {

	public static final String TAG = "DropTarget";

	class DragObject {
		public int x = -1;
		public int y = -1;

		/**
		 * X offset from the upper-left corner of the cell to where we touched.
		 * 触摸点到cell右上角的x位置
		 */
		public int xOffset = -1;

		/**
		 * Y offset from the upper-left corner of the cell to where we touched.
		 * 触摸点到cell右上角的y位置
		 */
		public int yOffset = -1;

		/**
		 * This indicates whether a drag is in final stages, either drop or
		 * cancel. It differentiates onDragExit, since this is called when the
		 * drag is ending, above the current drag target, or when the drag moves
		 * off the current drag object. 表示一个拖动操作是否在最后的状态，放下或者取消。和onDragExit不同，
		 * 这个只有在当前拖动对象上拖动终结时或者拖动出了当前对象时才会产生？
		 */
		public boolean dragComplete = false;

		/** 拖动的对象 */
		public DragView dragView = null;

		/** 与被拖动对象相关联的数据信息 */
		public Object dragInfo = null;

		/** 发起拖动的对象 */
		public DragSource dragSource = null;

		/** 顾名思义，调用拖动动画的Runnable */
		public Runnable postAnimationRunnable = null;

		/** 表示是否拖动操作已经被取消 */
		public boolean cancelled = false;

		/**
		 * drop animation不停止，就不删除DragView
		 */
		public boolean deferDragViewCleanupPostAnimation = true;

		public DragObject() {
		}
	}

	public static class DragEnforcer implements DragController.DragListener {
		int dragParity = 0;

		public DragEnforcer(Context context) {
			Launcher launcher = (Launcher) context;
			launcher.getDragController().addDragListener(this);
		}

		void onDragEnter() {
			dragParity++;
			if (dragParity != 1) {
				Log.e(TAG, "onDragEnter: Drag contract violated: " + dragParity);
			}
		}

		void onDragExit() {
			dragParity--;
			if (dragParity != 0) {
				Log.e(TAG, "onDragExit: Drag contract violated: " + dragParity);
			}
		}

		@Override
		public void onDragStart(DragSource source, Object info, int dragAction) {
			if (dragParity != 0) {
				Log.e(TAG, "onDragEnter: Drag contract violated: " + dragParity);
			}
		}

		@Override
		public void onDragEnd() {
			if (dragParity != 0) {
				Log.e(TAG, "onDragExit: Drag contract violated: " + dragParity);
			}
		}
	}

	/**
	 * 用来临时禁止接收某些对象
	 * 
	 * @return 是否能接收拖放对象
	 */
	boolean isDropEnabled();

	/**
	 * 处理一个放到DropTarget上的对象
	 * 
	 * @param source
	 *            发起Drag的对象 
	 * @param x
	 *            drop时的x坐标
	 * @param y
	 *            drop时的y坐标
	 * @param xOffset
	 *            拖动对象移动的x距离
	 * @param yOffset
	 *            拖动对象移动的y距离
	 * @param dragView
	 *             被拖动对象
	 * @param dragInfo
	 *            被拖动对象的数据
	 * 
	 */
	void onDrop(DragObject dragObject);

	void onDragEnter(DragObject dragObject);

	void onDragOver(DragObject dragObject);

	void onDragExit(DragObject dragObject);

	/**
	 * Handle an object being dropped as a result of flinging to delete and will
	 * be called in place of onDrop(). (This is only called on objects that are
	 * set as the DragController's fling-to-delete target.
	 * 处理一个滑动删除的对象
	 */
	void onFlingToDelete(DragObject dragObject, int x, int y, PointF vec);

	/**
	 * 允许一个DropTarget将拖放事件代理到另一个对象。
	 * 大多数子类应该只返回null
	 * 
	 * @param source
	 *            发起Drag的对象 
	 * @param x
	 *            drop时的x坐标
	 * @param y
	 *            drop时的y坐标
	 * @param xOffset
	 *            拖动对象移动的x距离
	 * @param yOffset
	 *            拖动对象移动的y距离
	 * @param dragView
	 *             被拖动对象
	 * @param dragInfo
	 *            被拖动对象的数据
	 * 
	 * @return 返回要代理到的对象
	 */
	DropTarget getDropTargetDelegate(DragObject dragObject);

	/**
	 * 检查是否drop操作可以在要求的位置或者附近发生。在onDrop之前发生
	 * 
	 * @param source
	 *            发起Drag的对象 
	 * @param x
	 *            drop时的x坐标
	 * @param y
	 *            drop时的y坐标
	 * @param xOffset
	 *            拖动对象移动的x距离
	 * @param yOffset
	 *            拖动对象移动的y距离
	 * @param dragView
	 *             被拖动对象
	 * @param dragInfo
	 *            被拖动对象的数据
	 *            
	 * @return 如果可以接收drop就返回true，否则返回false
	 */
	boolean acceptDrop(DragObject dragObject);

	// 下面这些方法将在view里面实现
	void getHitRect(Rect outRect);

	void getLocationInDragLayer(int[] loc);

	int getLeft();

	int getTop();
}
