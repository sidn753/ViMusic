package com.boko.vimusic;

import com.boko.vimusic.service.MediaPlaybackService;

/**
 * Listens for playback changes to send the the fragments bound to this activity
 */
public interface MusicStateListener {

	/**
	 * Called when {@link BaseActivity#REFRESH_REQUESTED} is invoked
	 */
	public void restartLoader();

	/**
	 * Called when {@link MediaPlaybackService#EVENT_META_CHANGED} is invoked
	 */
	public void onMetaChanged();

}
