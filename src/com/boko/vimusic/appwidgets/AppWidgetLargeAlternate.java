/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.boko.vimusic.appwidgets;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.widget.RemoteViews;

import com.boko.vimusic.R;
import com.boko.vimusic.service.MediaPlaybackService;
import com.boko.vimusic.ui.activities.AudioPlayerActivity;
import com.boko.vimusic.ui.activities.HomeActivity;
import com.boko.vimusic.utils.CommonUtils;

/**
 * 4x2 App-Widget
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
@SuppressLint("NewApi")
public class AppWidgetLargeAlternate extends AppWidgetBase {

	public static final String CMDAPPWIDGETUPDATE = "app_widget_large_alternate_update";

	private static AppWidgetLargeAlternate mInstance;

	public static synchronized AppWidgetLargeAlternate getInstance() {
		if (mInstance == null) {
			mInstance = new AppWidgetLargeAlternate();
		}
		return mInstance;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onUpdate(final Context context,
			final AppWidgetManager appWidgetManager, final int[] appWidgetIds) {
		defaultAppWidget(context, appWidgetIds);
		final Intent updateIntent = new Intent(MediaPlaybackService.ACTION_APPWIDGET_UPDATE);
		updateIntent.putExtra(MediaPlaybackService.EXTRA_APPWIDGET_PROVIDER,
				AppWidgetLargeAlternate.CMDAPPWIDGETUPDATE);
		updateIntent.putExtra(MediaPlaybackService.EXTRA_APPWIDGET_IDS,
				appWidgetIds);
		updateIntent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
		context.sendBroadcast(updateIntent);
	}

	/**
	 * Initialize given widgets to default state, where we launch Music on
	 * default click and hide actions if service not running.
	 */
	private void defaultAppWidget(final Context context,
			final int[] appWidgetIds) {
		final RemoteViews appWidgetViews = new RemoteViews(
				context.getPackageName(), R.layout.app_widget_large_alternate);
		linkButtons(context, appWidgetViews, false);
		pushUpdate(context, appWidgetIds, appWidgetViews);
	}

	private void pushUpdate(final Context context, final int[] appWidgetIds,
			final RemoteViews views) {
		final AppWidgetManager appWidgetManager = AppWidgetManager
				.getInstance(context);
		if (appWidgetIds != null) {
			appWidgetManager.updateAppWidget(appWidgetIds, views);
		} else {
			appWidgetManager.updateAppWidget(new ComponentName(context,
					getClass()), views);
		}
	}

	/**
	 * Check against {@link AppWidgetManager} if there are any instances of this
	 * widget.
	 */
	private boolean hasInstances(final Context context) {
		final AppWidgetManager appWidgetManager = AppWidgetManager
				.getInstance(context);
		final int[] mAppWidgetIds = appWidgetManager
				.getAppWidgetIds(new ComponentName(context, getClass()));
		return mAppWidgetIds.length > 0;
	}

	/**
	 * Handle a change notification coming over from
	 * {@link MediaPlaybackService}
	 */
	public void notifyChange(final MediaPlaybackService service,
			final String what) {
		if (hasInstances(service)) {
			if (MediaPlaybackService.META_CHANGED.equals(what)
					|| MediaPlaybackService.PLAYSTATE_CHANGED.equals(what)
					|| MediaPlaybackService.REPEATMODE_CHANGED.equals(what)
					|| MediaPlaybackService.SHUFFLEMODE_CHANGED.equals(what)) {
				performUpdate(service, null);
			}
		}
	}

	/**
	 * Update all active widget instances by pushing changes
	 */
	public void performUpdate(final MediaPlaybackService service,
			final int[] appWidgetIds) {
		final RemoteViews appWidgetView = new RemoteViews(
				service.getPackageName(), R.layout.app_widget_large_alternate);

		final CharSequence trackName = service.getTrackName();
		final CharSequence artistName = service.getArtistName();
		final CharSequence albumName = service.getAlbumName();
		final Bitmap bitmap = service.getAlbumArt();

		// Set the titles and artwork
		appWidgetView.setTextViewText(R.id.app_widget_large_alternate_line_one,
				trackName);
		appWidgetView.setTextViewText(R.id.app_widget_large_alternate_line_two,
				artistName);
		appWidgetView.setTextViewText(
				R.id.app_widget_large_alternate_line_three, albumName);
		appWidgetView.setImageViewBitmap(R.id.app_widget_large_alternate_image,
				bitmap);

		// Set correct drawable for pause state
		final boolean isPlaying = service.isPlaying();
		if (isPlaying) {
			appWidgetView.setImageViewResource(
					R.id.app_widget_large_alternate_play,
					R.drawable.btn_playback_pause);
			if (CommonUtils.hasJellyBean()) {
				appWidgetView.setContentDescription(
						R.id.app_widget_large_alternate_play,
						service.getString(R.string.accessibility_pause));
			}
		} else {
			appWidgetView.setImageViewResource(
					R.id.app_widget_large_alternate_play,
					R.drawable.btn_playback_play);
			if (CommonUtils.hasJellyBean()) {
				appWidgetView.setContentDescription(
						R.id.app_widget_large_alternate_play,
						service.getString(R.string.accessibility_play));
			}
		}

		// Set the correct drawable for the repeat state
		switch (service.getRepeatMode()) {
		case MediaPlaybackService.REPEAT_ALL:
			appWidgetView.setImageViewResource(
					R.id.app_widget_large_alternate_repeat,
					R.drawable.btn_playback_repeat_all);
			break;
		case MediaPlaybackService.REPEAT_CURRENT:
			appWidgetView.setImageViewResource(
					R.id.app_widget_large_alternate_repeat,
					R.drawable.btn_playback_repeat_one);
			break;
		default:
			appWidgetView.setImageViewResource(
					R.id.app_widget_large_alternate_repeat,
					R.drawable.btn_playback_repeat);
			break;
		}

		// Set the correct drawable for the shuffle state
		switch (service.getShuffleMode()) {
		case MediaPlaybackService.SHUFFLE_NONE:
			appWidgetView.setImageViewResource(
					R.id.app_widget_large_alternate_shuffle,
					R.drawable.btn_playback_shuffle);
			break;
		default:
			appWidgetView.setImageViewResource(
					R.id.app_widget_large_alternate_shuffle,
					R.drawable.btn_playback_shuffle_all);
			break;
		}

		// Link actions buttons to intents
		linkButtons(service, appWidgetView, isPlaying);

		// Update the app-widget
		pushUpdate(service, appWidgetIds, appWidgetView);
	}

	/**
	 * Link up various button actions using {@link PendingIntents}.
	 * 
	 * @param playerActive
	 *            True if player is active in background, which means widget
	 *            click will launch {@link AudioPlayerActivity}, otherwise we
	 *            launch {@link MusicBrowserActivity}.
	 */
	private void linkButtons(final Context context, final RemoteViews views,
			final boolean playerActive) {
		Intent action;
		PendingIntent pendingIntent;

		final ComponentName serviceName = new ComponentName(context,
				MediaPlaybackService.class);

		// Now playing
		if (playerActive) {
			action = new Intent(context, AudioPlayerActivity.class);
			pendingIntent = PendingIntent.getActivity(context, 0, action, 0);
			views.setOnClickPendingIntent(
					R.id.app_widget_large_alternate_info_container,
					pendingIntent);
			views.setOnClickPendingIntent(
					R.id.app_widget_large_alternate_image, pendingIntent);
		} else {
			// Home
			action = new Intent(context, HomeActivity.class);
			pendingIntent = PendingIntent.getActivity(context, 0, action, 0);
			views.setOnClickPendingIntent(
					R.id.app_widget_large_alternate_info_container,
					pendingIntent);
			views.setOnClickPendingIntent(
					R.id.app_widget_large_alternate_image, pendingIntent);
		}
		// Shuffle modes
		pendingIntent = buildPendingIntent(context,
				MediaPlaybackService.ACTION_SHUFFLE, serviceName);
		views.setOnClickPendingIntent(R.id.app_widget_large_alternate_shuffle,
				pendingIntent);

		// Previous track
		pendingIntent = buildPendingIntent(context,
				MediaPlaybackService.ACTION_PREVIOUS, serviceName);
		views.setOnClickPendingIntent(R.id.app_widget_large_alternate_previous,
				pendingIntent);

		// Play and pause
		pendingIntent = buildPendingIntent(context,
				MediaPlaybackService.ACTION_TOGGLEPAUSE, serviceName);
		views.setOnClickPendingIntent(R.id.app_widget_large_alternate_play,
				pendingIntent);

		// Next track
		pendingIntent = buildPendingIntent(context,
				MediaPlaybackService.ACTION_NEXT, serviceName);
		views.setOnClickPendingIntent(R.id.app_widget_large_alternate_next,
				pendingIntent);

		// Repeat modes
		pendingIntent = buildPendingIntent(context,
				MediaPlaybackService.ACTION_REPEAT, serviceName);
		views.setOnClickPendingIntent(R.id.app_widget_large_alternate_repeat,
				pendingIntent);
	}

}
