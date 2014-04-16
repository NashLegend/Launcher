/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.os.Handler;

public class Alarm implements Runnable {
	// 如果到了预订的时间，并且这个alarm也没有取消，就执行
	private long mAlarmTriggerTime;

	// 如果有定时任务，那么就是true，防止重复的等等调用
	private boolean mWaitingForCallback;

	private Handler mHandler;
	private OnAlarmListener mAlarmListener;
	private boolean mAlarmPending = false;

	public Alarm() {
		mHandler = new Handler();
	}

	public void setOnAlarmListener(OnAlarmListener alarmListener) {
		mAlarmListener = alarmListener;
	}

	// 设定一段时间到执行alarm。如果已经设定了，那么覆盖并使用新的alarm。重新设定mAlarmTriggerTime就是覆盖了
	public void setAlarm(long millisecondsInFuture) {
		long currentTime = System.currentTimeMillis();
		mAlarmPending = true;
		mAlarmTriggerTime = currentTime + millisecondsInFuture;
		if (!mWaitingForCallback) {
			mHandler.postDelayed(this, mAlarmTriggerTime - currentTime);
			mWaitingForCallback = true;
		}
	}

	public void cancelAlarm() {
		mAlarmTriggerTime = 0;
		mAlarmPending = false;
	}

	// 到时间就执行……
	public void run() {
		mWaitingForCallback = false;
		if (mAlarmTriggerTime != 0) {
			long currentTime = System.currentTimeMillis();
			if (mAlarmTriggerTime > currentTime) {
				// 这时候仍然要再等一小会会再触发spring loaded
				// mode,因为有可能中途发生了setAlarm，mAlarmTriggerTime发生了改变
				mHandler.postDelayed(this,
						Math.max(0, mAlarmTriggerTime - currentTime));
				mWaitingForCallback = true;
			} else {
				mAlarmPending = false;
				if (mAlarmListener != null) {
					mAlarmListener.onAlarm(this);
				}
			}
		}
	}

	public boolean alarmPending() {
		return mAlarmPending;
	}
}

interface OnAlarmListener {
	public void onAlarm(Alarm alarm);
}
