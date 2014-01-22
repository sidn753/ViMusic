package com.boko.vimusic.model;

/**
 * A class that represents a playlist.
 * 
 */
public class Playlist extends Media {

	/**
	 * Constructor of <code>Genre</code>
	 * 
	 * @param playlistId
	 *            The Id of the playlist
	 * @param playlistName
	 *            The playlist name
	 */
	public Playlist(final String playlistId, final String playlistName) {
		super();
		mId = playlistId;
		mName = playlistName;
	}
}
