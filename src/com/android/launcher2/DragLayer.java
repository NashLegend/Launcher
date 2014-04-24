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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.launcher.R;

import java.util.ArrayList;

/**
 * 基层父控件
 */
public class DragLayer extends FrameLayout implements
		ViewGroup.OnHierarchyChangeListener {
	private DragController mDragController;
	private int[] mTmpXY = new int[2];

	private int mXDown, mYDown;
	private Launcher mLauncher;

	// 与widget的resize相关的变量
	private final ArrayList<AppWidgetResizeFrame> mResizeFrames = new ArrayList<AppWidgetResizeFrame>();
	private AppWidgetResizeFrame mCurrentResizeFrame;

	// 与view在drop后的动画相关的变量们
	private ValueAnimator mDropAnim = null;
	private ValueAnimator mFadeOutAnim = null;
	private TimeInterpolator mCubicEaseOutInterpolator = new DecelerateInterpolator(
			1.5f);
	private DragView mDropView = null;
	private int mAnchorViewInitialScrollX = 0;
	private View mAnchorView = null;

	private boolean mHoverPointClosesFolder = false;
	private Rect mHitRect = new Rect();
	private int mWorkspaceIndex = -1;
	private int mQsbIndex = -1;
	public static final int ANIMATION_END_DISAPPEAR = 0;
	public static final int ANIMATION_END_FADE_OUT = 1;
	public static final int ANIMATION_END_REMAIN_VISIBLE = 2;

	/**
	 * 从xml里创建DragLayer
	 * 
	 * @param context
	 *            整个应用的context
	 * @param attrs
	 *            xml中的自定义参数
	 */
	public DragLayer(Context context, AttributeSet attrs) {
		super(context, attrs);

		// 禁止多点触控
		setMotionEventSplittingEnabled(false);
		// 按指定顺序渲染子控件
		setChildrenDrawingOrderEnabled(true);
		// 侦听子view的add和remove事件
		setOnHierarchyChangeListener(this);

		// 好像是拖动到ScrollArea区域时的高光色。
		mLeftHoverDrawable = getResources().getDrawable(
				R.drawable.page_hover_left_holo);
		mRightHoverDrawable = getResources().getDrawable(
				R.drawable.page_hover_right_holo);
	}

	/**
	 * 在Launcher中调用，设置Launcher和DragController和this的关联状态
	 * 
	 * @param launcher
	 * @param controller
	 */
	public void setup(Launcher launcher, DragController controller) {
		mLauncher = launcher;
		mDragController = controller;
	}

	/*
	 * 将KeyEvent交给DragController处理
	 */
	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		return mDragController.dispatchKeyEvent(event)
				|| super.dispatchKeyEvent(event);
	}

	/**
	 * 触控事件是否经过了文件夹图标的文字区域
	 * 
	 * @param folder
	 * @param ev
	 * @return
	 */
	private boolean isEventOverFolderTextRegion(Folder folder, MotionEvent ev) {
		getDescendantRectRelativeToSelf(folder.getEditTextRegion(), mHitRect);
		if (mHitRect.contains((int) ev.getX(), (int) ev.getY())) {
			return true;
		}
		return false;
	}

	/**
	 * 触控事件是否经过了文件夹图标区域
	 * 
	 * @param folder
	 * @param ev
	 * @return
	 */
	private boolean isEventOverFolder(Folder folder, MotionEvent ev) {
		getDescendantRectRelativeToSelf(folder, mHitRect);
		if (mHitRect.contains((int) ev.getX(), (int) ev.getY())) {
			return true;
		}
		return false;
	}

	/**
	 * 是否拦截处理按下事件，在onInterceptTouchEvent和onTouchEvent中调用。
	 * 当正在缩放插件、打开文件夹、编辑文件夹名称的时候返回true拦截
	 * 
	 * @param ev
	 * @param intercept
	 * @return
	 */
	private boolean handleTouchDown(MotionEvent ev, boolean intercept) {
		Rect hitRect = new Rect();
		int x = (int) ev.getX();
		int y = (int) ev.getY();

		for (AppWidgetResizeFrame child : mResizeFrames) {
			// 找到widget缩放框占据的矩形区域的矩形坐标
			child.getHitRect(hitRect);
			// 动作如果碰到了子控件。检查如果坐标处于缩放框可以对widget进行缩放操作的区域中，则禁止父控件再响应触控事件
			if (hitRect.contains(x, y)) {
				if (child.beginResizeIfPointInRegion(x - child.getLeft(), y
						- child.getTop())) {
					mCurrentResizeFrame = child;
					mXDown = x;
					mYDown = y;
					requestDisallowInterceptTouchEvent(true);
					return true;
				}
			}
		}

		// 获取当前打开的文件夹。
		// 如果文件夹正在打开状态。那么如果点击的是文件夹以外区域则关闭文件夹。
		// 如果点击的是文件夹内区域并且正处于重命名状态，则取消重命名
		Folder currentFolder = mLauncher.getWorkspace().getOpenFolder();
		if (currentFolder != null && !mLauncher.isFolderClingVisible()
				&& intercept) {
			if (currentFolder.isEditingName()) {
				if (!isEventOverFolderTextRegion(currentFolder, ev)) {
					currentFolder.dismissEditingName();
					return true;
				}
			}

			getDescendantRectRelativeToSelf(currentFolder, hitRect);
			if (!isEventOverFolder(currentFolder, ev)) {
				mLauncher.closeFolder();
				return true;
			}
		}
		return false;
	}

	/*
	 * 如果没有正在缩放插件、没有打开文件夹、没有编辑文件夹名称。 DragLayer将处理触控动作，并取消一切widget的Resize动作(如果有)。
	 */
	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if (ev.getAction() == MotionEvent.ACTION_DOWN) {
			if (handleTouchDown(ev, true)) {
				return true;
			}
		}
		clearAllResizeFrames();
		return mDragController.onInterceptTouchEvent(ev);
	}

	@Override
	public boolean onInterceptHoverEvent(MotionEvent ev) {
		if (mLauncher == null || mLauncher.getWorkspace() == null) {
			return false;
		}
		Folder currentFolder = mLauncher.getWorkspace().getOpenFolder();
		if (currentFolder == null) {
			return false;
		} else {
			AccessibilityManager accessibilityManager = (AccessibilityManager) getContext()
					.getSystemService(Context.ACCESSIBILITY_SERVICE);
			if (accessibilityManager.isTouchExplorationEnabled()) {
				final int action = ev.getAction();
				boolean isOverFolder;
				switch (action) {
				case MotionEvent.ACTION_HOVER_ENTER:
					isOverFolder = isEventOverFolder(currentFolder, ev);
					if (!isOverFolder) {
						sendTapOutsideFolderAccessibilityEvent(currentFolder
								.isEditingName());
						mHoverPointClosesFolder = true;
						return true;
					} else if (isOverFolder) {
						mHoverPointClosesFolder = false;
					} else {
						return true;
					}
				case MotionEvent.ACTION_HOVER_MOVE:
					isOverFolder = isEventOverFolder(currentFolder, ev);
					if (!isOverFolder && !mHoverPointClosesFolder) {
						sendTapOutsideFolderAccessibilityEvent(currentFolder
								.isEditingName());
						mHoverPointClosesFolder = true;
						return true;
					} else if (isOverFolder) {
						mHoverPointClosesFolder = false;
					} else {
						return true;
					}
				}
			}
		}
		return false;
	}

	private void sendTapOutsideFolderAccessibilityEvent(boolean isEditingName) {
		AccessibilityManager accessibilityManager = (AccessibilityManager) getContext()
				.getSystemService(Context.ACCESSIBILITY_SERVICE);
		if (accessibilityManager.isEnabled()) {
			int stringId = isEditingName ? R.string.folder_tap_to_rename
					: R.string.folder_tap_to_close;
			AccessibilityEvent event = AccessibilityEvent
					.obtain(AccessibilityEvent.TYPE_VIEW_FOCUSED);
			onInitializeAccessibilityEvent(event);
			event.getText().add(getContext().getString(stringId));
			accessibilityManager.sendAccessibilityEvent(event);
		}
	}

	@Override
	public boolean onHoverEvent(MotionEvent ev) {
		// If we've received this, we've already done the necessary handling
		// in onInterceptHoverEvent. Return true to consume the event.
		return false;
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		boolean handled = false;
		int action = ev.getAction();

		int x = (int) ev.getX();
		int y = (int) ev.getY();

		if (ev.getAction() == MotionEvent.ACTION_DOWN) {
			if (ev.getAction() == MotionEvent.ACTION_DOWN) {
				if (handleTouchDown(ev, false)) {
					return true;
				}
			}
		}

		if (mCurrentResizeFrame != null) {
			handled = true;
			switch (action) {
			case MotionEvent.ACTION_MOVE:
				mCurrentResizeFrame.visualizeResizeForDelta(x - mXDown, y
						- mYDown);
				break;
			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP:
				mCurrentResizeFrame.visualizeResizeForDelta(x - mXDown, y
						- mYDown);
				mCurrentResizeFrame.onTouchUp();
				mCurrentResizeFrame = null;
			}
		}
		if (handled)
			return true;
		// 如果没有正在调节widget大小也没有打开文件夹，则进入正题，开始拖动
		return mDragController.onTouchEvent(ev);
	}

	/**
	 * 获取子view所占据的矩形在父控件中的位置
	 * 
	 * @param descendant
	 *            The descendant whose coordinates we want to find.
	 * @param r
	 *            The rect into which to place the results.
	 * @return The factor by which this descendant is scaled relative to this
	 *         DragLayer.
	 */
	public float getDescendantRectRelativeToSelf(View descendant, Rect r) {
		mTmpXY[0] = 0;
		mTmpXY[1] = 0;
		float scale = getDescendantCoordRelativeToSelf(descendant, mTmpXY);
		r.set(mTmpXY[0], mTmpXY[1], mTmpXY[0] + descendant.getWidth(),
				mTmpXY[1] + descendant.getHeight());
		return scale;
	}

	/**
	 * 返回子控件在父控件上的坐标位置
	 * 
	 * @param child
	 * @param loc
	 * @return
	 */
	public float getLocationInDragLayer(View child, int[] loc) {
		loc[0] = 0;
		loc[1] = 0;
		return getDescendantCoordRelativeToSelf(child, loc);
	}

	/**
	 * 返回相对子view的坐标point的相对于父控件的坐标
	 * 
	 * @param descendant
	 *            The descendant to which the passed coordinate is relative.
	 * @param coord
	 *            The coordinate that we want mapped.
	 * @return The factor by which this descendant is scaled relative to this
	 *         DragLayer. Caution this scale factor is assumed to be equal in X
	 *         and Y, and so if at any point this assumption fails, we will need
	 *         to return a pair of scale factors.
	 */
	public float getDescendantCoordRelativeToSelf(View descendant, int[] coord) {
		float scale = 1.0f;
		float[] pt = { coord[0], coord[1] };
		descendant.getMatrix().mapPoints(pt);
		scale *= descendant.getScaleX();
		pt[0] += descendant.getLeft();
		pt[1] += descendant.getTop();
		ViewParent viewParent = descendant.getParent();
		while (viewParent instanceof View && viewParent != this) {
			final View view = (View) viewParent;
			view.getMatrix().mapPoints(pt);
			scale *= view.getScaleX();
			pt[0] += view.getLeft() - view.getScrollX();
			pt[1] += view.getTop() - view.getScrollY();
			viewParent = view.getParent();
		}
		coord[0] = (int) Math.round(pt[0]);
		coord[1] = (int) Math.round(pt[1]);
		return scale;
	}

	/**
	 * 获取view在DragLayer中的占据的矩形，不一定是DragLayer的子控件
	 * 
	 * @param v
	 * @param r
	 */
	public void getViewRectRelativeToSelf(View v, Rect r) {
		int[] loc = new int[2];
		getLocationInWindow(loc);
		int x = loc[0];
		int y = loc[1];

		v.getLocationInWindow(loc);
		int vX = loc[0];
		int vY = loc[1];

		int left = vX - x;
		int top = vY - y;
		r.set(left, top, left + v.getMeasuredWidth(),
				top + v.getMeasuredHeight());
	}

	/*
	 * 用于处理方向键，一般手机没有
	 */
	@Override
	public boolean dispatchUnhandledMove(View focused, int direction) {
		return mDragController.dispatchUnhandledMove(focused, direction);
	}

	public static class LayoutParams extends FrameLayout.LayoutParams {
		public int x, y;
		public boolean customPosition = false;

		/**
		 * {@inheritDoc}
		 */
		public LayoutParams(int width, int height) {
			super(width, height);
		}

		public void setWidth(int width) {
			this.width = width;
		}

		public int getWidth() {
			return width;
		}

		public void setHeight(int height) {
			this.height = height;
		}

		public int getHeight() {
			return height;
		}

		public void setX(int x) {
			this.x = x;
		}

		public int getX() {
			return x;
		}

		public void setY(int y) {
			this.y = y;
		}

		public int getY() {
			return y;
		}
	}

	/*
	 * 将要对各个子控件分配大小和位置时调用。需要调用所有子控件的layout()方法
	 */
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		super.onLayout(changed, l, t, r, b);
		int count = getChildCount();
		for (int i = 0; i < count; i++) {
			View child = getChildAt(i);
			final FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) child
					.getLayoutParams();
			if (flp instanceof LayoutParams) {
				final LayoutParams lp = (LayoutParams) flp;
				if (lp.customPosition) {
					child.layout(lp.x, lp.y, lp.x + lp.width, lp.y + lp.height);
				}
			}
		}
	}

	/**
	 * 清除widget的所有缩放框
	 */
	public void clearAllResizeFrames() {
		if (mResizeFrames.size() > 0) {
			for (AppWidgetResizeFrame frame : mResizeFrames) {
				frame.commitResize();
				removeView(frame);
			}
			mResizeFrames.clear();
		}
	}

	/**
	 * 桌面上中否有widget缩放框
	 */
	public boolean hasResizeFrames() {
		return mResizeFrames.size() > 0;
	}

	/**
	 * 是否正在缩放widget
	 */
	public boolean isWidgetBeingResized() {
		return mCurrentResizeFrame != null;
	}

	/**
	 * 为widget添加缩放框
	 * 
	 * @param itemInfo
	 * @param widget
	 * @param cellLayout
	 */
	public void addResizeFrame(ItemInfo itemInfo,
			LauncherAppWidgetHostView widget, CellLayout cellLayout) {
		AppWidgetResizeFrame resizeFrame = new AppWidgetResizeFrame(
				getContext(), widget, cellLayout, this);

		// 先将大小设置为MATCH_PARENT
		LayoutParams lp = new LayoutParams(-1, -1);
		lp.customPosition = true;

		addView(resizeFrame, lp);
		mResizeFrames.add(resizeFrame);

		// 将缩放框附到widget上
		resizeFrame.snapToWidget(false);
	}

	// 以下animateViewIntoxxx都是拖放结束后的动画

	public void animateViewIntoPosition(DragView dragView, final View child) {
		animateViewIntoPosition(dragView, child, null);
	}

	public void animateViewIntoPosition(DragView dragView, final int[] pos,
			float alpha, float scaleX, float scaleY, int animationEndStyle,
			Runnable onFinishRunnable, int duration) {
		Rect r = new Rect();
		getViewRectRelativeToSelf(dragView, r);
		final int fromX = r.left;
		final int fromY = r.top;

		animateViewIntoPosition(dragView, fromX, fromY, pos[0], pos[1], alpha,
				1, 1, scaleX, scaleY, onFinishRunnable, animationEndStyle,
				duration, null);
	}

	public void animateViewIntoPosition(DragView dragView, final View child,
			final Runnable onFinishAnimationRunnable) {
		animateViewIntoPosition(dragView, child, -1, onFinishAnimationRunnable,
				null);
	}

	public void animateViewIntoPosition(DragView dragView, final View child,
			int duration, final Runnable onFinishAnimationRunnable,
			View anchorView) {
		ShortcutAndWidgetContainer parentChildren = (ShortcutAndWidgetContainer) child
				.getParent();
		CellLayout.LayoutParams lp = (CellLayout.LayoutParams) child
				.getLayoutParams();
		parentChildren.measureChild(child);

		Rect r = new Rect();
		getViewRectRelativeToSelf(dragView, r);

		// child的实际可见位置
		int coord[] = new int[2];
		float childScale = child.getScaleX();
		coord[0] = lp.x
				+ (int) (child.getMeasuredWidth() * (1 - childScale) / 2);
		coord[1] = lp.y
				+ (int) (child.getMeasuredHeight() * (1 - childScale) / 2);

		// child不一定被layout()过，直接使用lp有可能不对（会在下一帧生效），所以我们强制lp使用上面得到的坐标，并且使用他们决定最终位置
		float scale = getDescendantCoordRelativeToSelf(
				(View) child.getParent(), coord);
		// 上面仅仅负责child在parent里面的scale,我们要负责child自身的scale
		// 自身的scale指实际可见显示大小与原本未缩放大小的比例。
		// 因为父控件甚至父控件的父控件也有可能被缩放过，这样使得child看起来被缩放的值是各种父控件与自身scale的乘积
		scale *= childScale;

		int toX = coord[0];
		int toY = coord[1];
		if (child instanceof TextView) {
			TextView tv = (TextView) child;

			// child有可能被缩放过（问题相对于view中心缩放），所以我们必须根据scale修正位置。
			// 这样我们就可以根据缩放过的child的中心确定dragview的中心（center the drag view about the
			// scaled child view.）
			toY += Math.round(scale * tv.getPaddingTop());
			toY -= dragView.getMeasuredHeight() * (1 - scale) / 2;
			toX -= (dragView.getMeasuredWidth() - Math.round(scale
					* child.getMeasuredWidth())) / 2;
		} else if (child instanceof FolderIcon) {
			// Account for holographic blur padding on the drag view
			toY -= scale * Workspace.DRAG_BITMAP_PADDING / 2;
			toY -= (1 - scale) * dragView.getMeasuredHeight() / 2;
			// Center in the x coordinate about the target's drawable
			toX -= (dragView.getMeasuredWidth() - Math.round(scale
					* child.getMeasuredWidth())) / 2;
		} else {
			toY -= (Math.round(scale
					* (dragView.getHeight() - child.getMeasuredHeight()))) / 2;
			toX -= (Math.round(scale
					* (dragView.getMeasuredWidth() - child.getMeasuredWidth()))) / 2;
		}

		final int fromX = r.left;
		final int fromY = r.top;
		child.setVisibility(INVISIBLE);
		Runnable onCompleteRunnable = new Runnable() {
			public void run() {
				child.setVisibility(VISIBLE);
				if (onFinishAnimationRunnable != null) {
					onFinishAnimationRunnable.run();
				}
			}
		};
		animateViewIntoPosition(dragView, fromX, fromY, toX, toY, 1, 1, 1,
				scale, scale, onCompleteRunnable, ANIMATION_END_DISAPPEAR,
				duration, anchorView);
	}

	public void animateViewIntoPosition(final DragView view, final int fromX,
			final int fromY, final int toX, final int toY, float finalAlpha,
			float initScaleX, float initScaleY, float finalScaleX,
			float finalScaleY, Runnable onCompleteRunnable,
			int animationEndStyle, int duration, View anchorView) {
		Rect from = new Rect(fromX, fromY, fromX + view.getMeasuredWidth(),
				fromY + view.getMeasuredHeight());
		Rect to = new Rect(toX, toY, toX + view.getMeasuredWidth(), toY
				+ view.getMeasuredHeight());
		animateView(view, from, to, finalAlpha, initScaleX, initScaleY,
				finalScaleX, finalScaleY, duration, null, null,
				onCompleteRunnable, animationEndStyle, anchorView);
	}

	/**
	 * 在拖放结束后放下view的动画
	 * 
	 * @param view
	 *            The view to be animated. This view is drawn directly into
	 *            DragLayer, and so doesn't need to be a child of DragLayer.
	 * @param from
	 *            放下前的位置
	 * @param to
	 *            放下后的位置，不计scale
	 * @param finalAlpha
	 *            The final alpha of the view, in case we want it to fade as it
	 *            animates.
	 * @param finalScale
	 *            The final scale of the view. The view is scaled about its
	 *            center.
	 * @param duration
	 *            The duration of the animation.
	 * @param motionInterpolator
	 *            The interpolator to use for the location of the view.
	 * @param alphaInterpolator
	 *            The interpolator to use for the alpha of the view.
	 * @param onCompleteRunnable
	 *            Optional runnable to run on animation completion.
	 * @param fadeOut
	 *            Whether or not to fade out the view once the animation
	 *            completes. If true, the runnable will execute after the view
	 *            is faded out.
	 * @param anchorView
	 *            If not null, this represents the view which the animated view
	 *            stays anchored to in case scrolling is currently taking place.
	 *            Note: currently this is only used for the X dimension for the
	 *            case of the workspace.
	 */
	public void animateView(final DragView view, final Rect from,
			final Rect to, final float finalAlpha, final float initScaleX,
			final float initScaleY, final float finalScaleX,
			final float finalScaleY, int duration,
			final Interpolator motionInterpolator,
			final Interpolator alphaInterpolator,
			final Runnable onCompleteRunnable, final int animationEndStyle,
			View anchorView) {

		// Calculate the duration of the animation based on the object's
		// distance
		final float dist = (float) Math.sqrt(Math.pow(to.left - from.left, 2)
				+ Math.pow(to.top - from.top, 2));
		final Resources res = getResources();
		final float maxDist = (float) res
				.getInteger(R.integer.config_dropAnimMaxDist);

		// If duration < 0, this is a cue to compute the duration based on the
		// distance
		if (duration < 0) {
			duration = res.getInteger(R.integer.config_dropAnimMaxDuration);
			if (dist < maxDist) {
				duration *= mCubicEaseOutInterpolator.getInterpolation(dist
						/ maxDist);
			}
			duration = Math.max(duration,
					res.getInteger(R.integer.config_dropAnimMinDuration));
		}

		// Fall back to cubic ease out interpolator for the animation if none is
		// specified
		TimeInterpolator interpolator = null;
		if (alphaInterpolator == null || motionInterpolator == null) {
			interpolator = mCubicEaseOutInterpolator;
		}

		// Animate the view
		final float initAlpha = view.getAlpha();
		final float dropViewScale = view.getScaleX();
		AnimatorUpdateListener updateCb = new AnimatorUpdateListener() {
			@Override
			public void onAnimationUpdate(ValueAnimator animation) {
				final float percent = (Float) animation.getAnimatedValue();
				final int width = view.getMeasuredWidth();
				final int height = view.getMeasuredHeight();

				float alphaPercent = alphaInterpolator == null ? percent
						: alphaInterpolator.getInterpolation(percent);
				float motionPercent = motionInterpolator == null ? percent
						: motionInterpolator.getInterpolation(percent);

				float initialScaleX = initScaleX * dropViewScale;
				float initialScaleY = initScaleY * dropViewScale;
				float scaleX = finalScaleX * percent + initialScaleX
						* (1 - percent);
				float scaleY = finalScaleY * percent + initialScaleY
						* (1 - percent);
				float alpha = finalAlpha * alphaPercent + initAlpha
						* (1 - alphaPercent);

				float fromLeft = from.left + (initialScaleX - 1f) * width / 2;
				float fromTop = from.top + (initialScaleY - 1f) * height / 2;

				int x = (int) (fromLeft + Math
						.round(((to.left - fromLeft) * motionPercent)));
				int y = (int) (fromTop + Math
						.round(((to.top - fromTop) * motionPercent)));

				int xPos = x
						- mDropView.getScrollX()
						+ (mAnchorView != null ? (mAnchorViewInitialScrollX - mAnchorView
								.getScrollX()) : 0);
				int yPos = y - mDropView.getScrollY();

				mDropView.setTranslationX(xPos);
				mDropView.setTranslationY(yPos);
				mDropView.setScaleX(scaleX);
				mDropView.setScaleY(scaleY);
				mDropView.setAlpha(alpha);
			}
		};
		animateView(view, updateCb, duration, interpolator, onCompleteRunnable,
				animationEndStyle, anchorView);
	}

	public void animateView(final DragView view,
			AnimatorUpdateListener updateCb, int duration,
			TimeInterpolator interpolator, final Runnable onCompleteRunnable,
			final int animationEndStyle, View anchorView) {
		// Clean up the previous animations
		if (mDropAnim != null)
			mDropAnim.cancel();
		if (mFadeOutAnim != null)
			mFadeOutAnim.cancel();

		// Show the drop view if it was previously hidden
		mDropView = view;
		mDropView.cancelAnimation();
		mDropView.resetLayoutParams();

		// Set the anchor view if the page is scrolling
		if (anchorView != null) {
			mAnchorViewInitialScrollX = anchorView.getScrollX();
		}
		mAnchorView = anchorView;

		// Create and start the animation
		mDropAnim = new ValueAnimator();
		mDropAnim.setInterpolator(interpolator);
		mDropAnim.setDuration(duration);
		mDropAnim.setFloatValues(0f, 1f);
		mDropAnim.addUpdateListener(updateCb);
		mDropAnim.addListener(new AnimatorListenerAdapter() {
			public void onAnimationEnd(Animator animation) {
				if (onCompleteRunnable != null) {
					onCompleteRunnable.run();
				}
				switch (animationEndStyle) {
				case ANIMATION_END_DISAPPEAR:
					clearAnimatedView();
					break;
				case ANIMATION_END_FADE_OUT:
					fadeOutDragView();
					break;
				case ANIMATION_END_REMAIN_VISIBLE:
					break;
				}
			}
		});
		mDropAnim.start();
	}

	/**
	 * 取消drop动画
	 */
	public void clearAnimatedView() {
		if (mDropAnim != null) {
			mDropAnim.cancel();
		}
		if (mDropView != null) {
			mDragController.onDeferredEndDrag(mDropView);
		}
		mDropView = null;
		invalidate();
	}

	public View getAnimatedView() {
		return mDropView;
	}

	private void fadeOutDragView() {
		mFadeOutAnim = new ValueAnimator();
		mFadeOutAnim.setDuration(150);
		mFadeOutAnim.setFloatValues(0f, 1f);
		mFadeOutAnim.removeAllUpdateListeners();
		mFadeOutAnim.addUpdateListener(new AnimatorUpdateListener() {
			public void onAnimationUpdate(ValueAnimator animation) {
				final float percent = (Float) animation.getAnimatedValue();

				float alpha = 1 - percent;
				mDropView.setAlpha(alpha);
			}
		});
		mFadeOutAnim.addListener(new AnimatorListenerAdapter() {
			public void onAnimationEnd(Animator animation) {
				if (mDropView != null) {
					mDragController.onDeferredEndDrag(mDropView);
				}
				mDropView = null;
				invalidate();
			}
		});
		mFadeOutAnim.start();
	}

	@Override
	public void onChildViewAdded(View parent, View child) {
		updateChildIndices();
	}

	@Override
	public void onChildViewRemoved(View parent, View child) {
		updateChildIndices();
	}

	private void updateChildIndices() {
		if (mLauncher != null) {
			mWorkspaceIndex = indexOfChild(mLauncher.getWorkspace());
			mQsbIndex = indexOfChild(mLauncher.getSearchBar());
		}
	}

	/*
	 * 此方法事实上不起效果，使用默认值 ，即i——child被加入的顺序
	 */
	@Override
	protected int getChildDrawingOrder(int childCount, int i) {
		// TODO: We have turned off this custom drawing order because it now
		// effects touch
		// dispatch order. We need to sort that issue out and then decide how to
		// go about this.
		// if (true || LauncherApplication.isScreenLandscape(getContext())
		// || mWorkspaceIndex == -1 || mQsbIndex == -1
		// || mLauncher.getWorkspace().isDrawingBackgroundGradient()) {
		// return i;
		// }
		
		return i;

		// This ensures that the workspace is drawn above the hotseat and qsb,
		// except when the workspace is drawing a background gradient, in which
		// case we want the workspace to stay behind these elements.
		// if (i == mQsbIndex) {
		// return mWorkspaceIndex;
		// } else if (i == mWorkspaceIndex) {
		// return mQsbIndex;
		// } else {
		// return i;
		// }
	}

	private boolean mInScrollArea;
	private Drawable mLeftHoverDrawable;
	private Drawable mRightHoverDrawable;

	/**
	 * 进入滚动区域，会在DragController中执行滚动
	 */
	void onEnterScrollArea(int direction) {
		mInScrollArea = true;
		invalidate();
	}

	/**
	 * 退出滚动区域后，仅仅是把mInScrollArea设为了false
	 */
	void onExitScrollArea() {
		mInScrollArea = false;
		invalidate();
	}

	/*
	 * 在子view重绘前执行，好像是绘制进入ScrollArea时的边框色
	 */
	@Override
	protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);

		// 不是很明白 TODO
		// 如果在ScrollArea并且是小屏幕，则要高光
		if (mInScrollArea && !LauncherApplication.isScreenLarge()) {
			Workspace workspace = mLauncher.getWorkspace();
			int width = workspace.getWidth();
			Rect childRect = new Rect();
			getDescendantRectRelativeToSelf(workspace.getChildAt(0), childRect);

			int page = workspace.getNextPage();
			CellLayout leftPage = (CellLayout) workspace.getChildAt(page - 1);
			CellLayout rightPage = (CellLayout) workspace.getChildAt(page + 1);

			if (leftPage != null && leftPage.getIsDragOverlapping()) {
				mLeftHoverDrawable.setBounds(0, childRect.top,
						mLeftHoverDrawable.getIntrinsicWidth(),
						childRect.bottom);
				mLeftHoverDrawable.draw(canvas);
			} else if (rightPage != null && rightPage.getIsDragOverlapping()) {
				mRightHoverDrawable.setBounds(
						width - mRightHoverDrawable.getIntrinsicWidth(),
						childRect.top, width, childRect.bottom);
				mRightHoverDrawable.draw(canvas);
			}
		}
	}
}
