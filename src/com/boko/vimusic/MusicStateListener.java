package com.boko.vimusic;

import com.boko.vimusic.service.MediaPlaybackService;

/**
 * Listens for playback changes to send the the fragments bound to this activity
 */
public interface MusicStateListener {

	/**
	 * Called when {@link MediaPlaybackService#ACTION_REFRESH} is invoked
	 */
	public void restartLoader();

	/**
	 * Called when {@link MediaPlaybackService#META_CHANGED} is invoked
	 */
	public void onMetaChanged();

}
