
package com.boko.vimusic;

import com.boko.vimusic.service.MusicPlaybackService;

/**
 * Listens for playback changes to send the the fragments bound to this activity
 */
public interface MusicStateListener {

    /**
     * Called when {@link MusicPlaybackService#EVENT_REFRESH_FORCED} is invoked
     */
    public void restartLoader();

    /**
     * Called when {@link MusicPlaybackService#EVENT_META_CHANGED} is invoked
     */
    public void onMetaChanged();

}
