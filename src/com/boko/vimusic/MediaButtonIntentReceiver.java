/*
 * Copyright (C) 2007 The Android Open Source Project Licensed under the Apache
 * License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.boko.vimusic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;

import com.boko.vimusic.service.MediaPlaybackService;

/**
 * Used to control headset playback. Single press: pause/resume. Double press:
 * next track Long press: voice search.
 */
public class MediaButtonIntentReceiver extends BroadcastReceiver {

	private static final int MSG_LONGPRESS_TIMEOUT = 1;

	private static final int LONG_PRESS_DELAY = 1000;

	private static final int DOUBLE_CLICK = 800;

	private static long mLastClickTime = 0;

	private static boolean mDown = false;

	private static boolean mLaunched = false;

	private static Handler mHandler = new Handler() {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void handleMessage(final Message msg) {
			switch (msg.what) {
			case MSG_LONGPRESS_TIMEOUT:
				if (!mLaunched) {
					final Context context = (Context) msg.obj;
					final Intent i = context
							.getPackageManager()
							.getLaunchIntentForPackage(context.getPackageName());
					i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
							| Intent.FLAG_ACTIVITY_CLEAR_TOP);
					context.startActivity(i);
					mLaunched = true;
				}
				break;
			}
		}
	};

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onReceive(final Context context, final Intent intent) {
		final String intentAction = intent.getAction();
		if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intentAction)) {
			final Intent i = new Intent(context, MediaPlaybackService.class);
			i.setAction(MediaPlaybackService.ACTION_PLAYER_PAUSE);
			context.startService(i);
		} else if (Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) {
			final KeyEvent event = (KeyEvent) intent
					.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
			if (event == null) {
				return;
			}

			final int keycode = event.getKeyCode();
			final int keyaction = event.getAction();
			final long eventtime = event.getEventTime();

			String action = null;
			switch (keycode) {
			case KeyEvent.KEYCODE_MEDIA_STOP:
				action = MediaPlaybackService.ACTION_PLAYER_STOP;
				break;
			case KeyEvent.KEYCODE_HEADSETHOOK:
			case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
				action = MediaPlaybackService.ACTION_PLAYER_TOGGLEPAUSE;
				break;
			case KeyEvent.KEYCODE_MEDIA_NEXT:
				action = MediaPlaybackService.ACTION_PLAYER_NEXT;
				break;
			case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
				action = MediaPlaybackService.ACTION_PLAYER_PREVIOUS;
				break;
			case KeyEvent.KEYCODE_MEDIA_PAUSE:
				action = MediaPlaybackService.ACTION_PLAYER_PAUSE;
				break;
			case KeyEvent.KEYCODE_MEDIA_PLAY:
				action = MediaPlaybackService.ACTION_PLAYER_PLAY;
				break;
			}
			if (action != null) {
				if (keyaction == KeyEvent.ACTION_DOWN) {
					if (mDown) {
						if ((MediaPlaybackService.ACTION_PLAYER_TOGGLEPAUSE
								.equals(action) || MediaPlaybackService.ACTION_PLAYER_PLAY
								.equals(action))
								&& mLastClickTime != 0
								&& eventtime - mLastClickTime > LONG_PRESS_DELAY) {
							mHandler.sendMessage(mHandler.obtainMessage(
									MSG_LONGPRESS_TIMEOUT, context));
						}
					} else if (event.getRepeatCount() == 0) {
						// Only consider the first event in a sequence, not the
						// repeat events,
						// so that we don't trigger in cases where the first
						// event went to
						// a different app (e.g. when the user ends a phone call
						// by
						// long pressing the headset button)

						// The service may or may not be running, but we need to
						// send it
						// a command.
						final Intent i = new Intent(context,
								MediaPlaybackService.class);
						if (keycode == KeyEvent.KEYCODE_HEADSETHOOK
								&& eventtime - mLastClickTime < DOUBLE_CLICK) {
							i.setAction(MediaPlaybackService.ACTION_PLAYER_NEXT);
							context.startService(i);
							mLastClickTime = 0;
						} else {
							i.setAction(action);
							context.startService(i);
							mLastClickTime = eventtime;
						}
						mLaunched = false;
						mDown = true;
					}
				} else {
					mHandler.removeMessages(MSG_LONGPRESS_TIMEOUT);
					mDown = false;
				}
				if (isOrderedBroadcast()) {
					abortBroadcast();
				}
			}
		}
	}
}
