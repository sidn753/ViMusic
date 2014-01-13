package com.boko.vimusic.model;


/**
 * A class that represents a song.
 * 
 */
public class Song extends Media {

    /**
     * The unique Id of the song
     */
    public long mSongId;

    /**
     * The song artist
     */
    public String mArtistName;

    /**
     * The song album
     */
    public String mAlbumName;

    /**
     * The song duration in seconds
     */
    public int mDuration;
    
    public String mLinkPlay;
    
    public String mLinkDownload;
    
    public Song() {
    }

    /**
     * Constructor of <code>Song</code>
     * 
     * @param songId The Id of the song
     * @param songName The name of the song
     * @param artistName The song artist
     * @param albumName The song album
     * @param duration The duration of a song in seconds
     */
    public Song(final long songId, final String songName, final String artistName,
            final String albumName, final int duration) {
        mSongId = songId;
        setName(songName);
        mArtistName = artistName;
        mAlbumName = albumName;
        mDuration = duration;
    }
}
