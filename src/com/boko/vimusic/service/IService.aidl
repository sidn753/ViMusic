package com.boko.vimusic.service;

import android.graphics.Bitmap;

interface IService
{
    void openFile(String path);
    void open(in String [] list, int position);
    void stop();
    void pause();
    void play();
    void prev();
    void next();
    void enqueue(in String [] list, int action);
    void setQueuePosition(int index);
    void setShuffleMode(int shufflemode);
    void setRepeatMode(int repeatmode);
    void moveQueueItem(int from, int to);
    void toggleFavorite();
    void refresh();
    boolean isFavorite();
    boolean isPlaying();
    String [] getQueue();
    long duration();
    long position();
    long seek(long pos);
    String getAudioId();
    String getArtistId();
    String getAlbumId();
    String getArtistName();
    String getTrackName();
    String getAlbumName();
    String getPath();
    int getQueuePosition();
    int getShuffleMode();
    int removeTracks(int first, int last);
    int removeTrack(String id); 
    int getRepeatMode();
    int getMediaMountedCount();
    int getAudioSessionId();
}

