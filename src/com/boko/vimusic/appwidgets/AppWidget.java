package com.boko.vimusic.appwidgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.boko.vimusic.service.MusicPlaybackService;

public abstract class AppWidget extends AppWidgetProvider {
	
	public static final String CMD_WIDGET_UPDATE = "app_widget_update";

	protected PendingIntent buildPendingIntent(Context context,
			final String action, final ComponentName serviceName) {
		Intent intent = new Intent(MusicPlaybackService.ACTION);
		intent.setComponent(serviceName);
		intent.putExtra(MusicPlaybackService.EXTRA_COMMAND, action);
		intent.putExtra(MusicPlaybackService.EXTRA_FOREGROUND, false);
		return PendingIntent.getService(context, 0, intent, 0);
	}

	public abstract void performUpdate(final MusicPlaybackService service,
			final int[] appWidgetIds);
}
