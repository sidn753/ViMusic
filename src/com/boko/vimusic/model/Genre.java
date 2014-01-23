package com.boko.vimusic.model;

/**
 * A class that represents a genre.
 * 
 */
public class Genre extends Media {

	/**
	 * Constructor of <code>Genre</code>
	 * 
	 * @param genreId
	 *            The Id of the genre
	 * @param genreName
	 *            The genre name
	 */
	public Genre(final String genreId, final String genreName) {
		super();
		mId = genreId;
		mName = genreName;
	}

}
