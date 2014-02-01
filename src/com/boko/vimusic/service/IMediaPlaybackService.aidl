package com.boko.vimusic.service;

import android.graphics.Bitmap;
import com.boko.vimusic.model.Song;

interface IMediaPlaybackService
{
    void playFile(String path);
    void open(in Song [] list, int position);
    int getQueuePosition();
    void setQueuePosition(int index);
    boolean isPlaying();
    void stop();
    void pause();
    void play();
    void prev();
    void next();
    String getTrackName();
    String getAlbumName();
    String getAlbumId();
    String getArtistName();
    String getArtistId();
    void enqueue(in Song [] list, int action);
    Song [] getQueue();
    void moveQueueItem(int from, int to);
    String getPath();
    String getAudioId();
    long position();
    long duration();
    long seek(long pos);
    void setShuffleMode(int shufflemode);
    int getShuffleMode();
    int removeTracks(int first, int last);
    int removeTrack(in Song song);
    void setRepeatMode(int repeatmode);
    int getRepeatMode();
    int getAudioSessionId();
    boolean isFavorite();
    void toggleFavorite();
    void refresh();
}

