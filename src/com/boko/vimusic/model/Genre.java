package com.boko.vimusic.model;


/**
 * A class that represents a genre.
 * 
 */
public class Genre extends Media {

    /**
     * The unique Id of the genre
     */
    public long mGenreId;

    /**
     * Constructor of <code>Genre</code>
     * 
     * @param genreId The Id of the genre
     * @param genreName The genre name
     */
    public Genre(final long genreId, final String genreName) {
        super();
        mGenreId = genreId;
        setName(genreName);
    }

}
