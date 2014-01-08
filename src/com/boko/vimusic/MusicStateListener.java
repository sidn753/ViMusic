
package com.boko.vimusic;

/**
 * Listens for playback changes to send the the fragments bound to this activity
 */
public interface MusicStateListener {

    /**
     * Called when {@link MusicPlaybackService#REFRESH} is invoked
     */
    public void restartLoader();

    /**
     * Called when {@link MusicPlaybackService#EVENT_META_CHANGED} is invoked
     */
    public void onMetaChanged();

}
