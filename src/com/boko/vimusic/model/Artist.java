package com.boko.vimusic.model;


/**
 * A class that represents an artist.
 * 
 */
public class Artist extends Media {

    /**
     * The unique Id of the artist
     */
    public long mArtistId;

    /**
     * The number of albums for the artist
     */
    public int mAlbumNumber;

    /**
     * The number of songs for the artist
     */
    public int mSongNumber;

    /**
     * Constructor of <code>Artist</code>
     * 
     * @param artistId The Id of the artist
     * @param artistName The artist name
     * @param songNumber The number of songs for the artist
     * @param albumNumber The number of albums for the artist
     */
    public Artist(final long artistId, final String artistName, final int songNumber,
            final int albumNumber) {
        super();
        mArtistId = artistId;
        setName(artistName);
        mSongNumber = songNumber;
        mAlbumNumber = albumNumber;
    }

}
