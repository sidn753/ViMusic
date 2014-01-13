package com.boko.vimusic.model;

/**
 * A class that represents a playlist.
 * 
 */
public class Playlist extends Media {

	/**
	 * The unique Id of the playlist
	 */
	public long mPlaylistId;

	/**
	 * Constructor of <code>Genre</code>
	 * 
	 * @param playlistId
	 *            The Id of the playlist
	 * @param playlistName
	 *            The playlist name
	 */
	public Playlist(final long playlistId, final String playlistName) {
		super();
		mPlaylistId = playlistId;
		setName(playlistName);
	}
}
