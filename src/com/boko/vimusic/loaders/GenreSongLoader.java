/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.boko.vimusic.loaders;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;

import com.boko.vimusic.model.HostType;
import com.boko.vimusic.model.Song;
import com.boko.vimusic.model.SongFactory;
import com.boko.vimusic.utils.Lists;

/**
 * Used to query {@link MediaStore.Audio.Genres.Members.EXTERNAL_CONTENT_URI}
 * and return the songs for a particular genre.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class GenreSongLoader extends WrappedAsyncTaskLoader<List<Song>> {

	/**
	 * The result
	 */
	private final ArrayList<Song> mSongList = Lists.newArrayList();

	/**
	 * The {@link Cursor} used to run the query.
	 */
	private Cursor mCursor;

	/**
	 * The Id of the genre the songs belong to.
	 */
	private final String mGenreID;

	/**
	 * Constructor of <code>GenreSongHandler</code>
	 * 
	 * @param context
	 *            The {@link Context} to use.
	 * @param genreID
	 *            The Id of the genre the songs belong to.
	 */
	public GenreSongLoader(final Context context, final String genreId) {
		super(context);
		mGenreID = genreId;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Song> loadInBackground() {
		// Create the Cursor
		mCursor = makeGenreSongCursor(getContext(), mGenreID);
		// Gather the data
		if (mCursor != null && mCursor.moveToFirst()) {
			do {
				// Copy the song Id
				final String id = mCursor.getString(0);

				// Create a new song
				final Song song = SongFactory.newSong(HostType.LOCAL, id);
				song.setName(mCursor.getString(1));
				song.setArtistName(mCursor.getString(2));
				song.setAlbumName(mCursor.getString(3));

				// Add everything up
				mSongList.add(song);
			} while (mCursor.moveToNext());
		}
		// Close the cursor
		if (mCursor != null) {
			mCursor.close();
			mCursor = null;
		}
		return mSongList;
	}

	/**
	 * @param context
	 *            The {@link Context} to use.
	 * @param genreId
	 *            The Id of the genre the songs belong to.
	 * @return The {@link Cursor} used to run the query.
	 */
	public static final Cursor makeGenreSongCursor(final Context context,
			final String genreId) {
		// Match the songs up with the genre
		final StringBuilder selection = new StringBuilder();
		selection.append(MediaStore.Audio.Genres.Members.IS_MUSIC + "=1");
		selection
				.append(" AND " + MediaStore.Audio.Genres.Members.TITLE + "!=''"); //$NON-NLS-2$
		return context.getContentResolver().query(
				MediaStore.Audio.Genres.Members.getContentUri("external",
						Long.valueOf(genreId)), new String[] {
				/* 0 */
				MediaStore.Audio.Genres.Members._ID,
				/* 1 */
				MediaStore.Audio.Genres.Members.TITLE,
				/* 2 */
				MediaStore.Audio.Genres.Members.ALBUM,
				/* 3 */
				MediaStore.Audio.Genres.Members.ARTIST }, selection.toString(),
				null, MediaStore.Audio.Genres.Members.DEFAULT_SORT_ORDER);
	}
}
