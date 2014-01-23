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

import com.boko.vimusic.model.HostType;
import com.boko.vimusic.model.Song;
import com.boko.vimusic.model.SongFactory;
import com.boko.vimusic.provider.FavoritesStore;
import com.boko.vimusic.provider.FavoritesStore.FavoritesTable;
import com.boko.vimusic.utils.Lists;

/**
 * Used to query the {@link FavoritesStore} for the tracks marked as favorites.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class FavoritesLoader extends WrappedAsyncTaskLoader<List<Song>> {

	/**
	 * The result
	 */
	private final ArrayList<Song> mSongList = Lists.newArrayList();

	/**
	 * The {@link Cursor} used to run the query.
	 */
	private Cursor mCursor;

	/**
	 * Constructor of <code>FavoritesHandler</code>
	 * 
	 * @param context
	 *            The {@link Context} to use.
	 */
	public FavoritesLoader(final Context context) {
		super(context);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Song> loadInBackground() {
		// Create the Cursor
		mCursor = makeFavoritesCursor(getContext());
		// Gather the data
		if (mCursor != null && mCursor.moveToFirst()) {
			do {

				// Copy the song Id
				final String id = mCursor.getString(mCursor
						.getColumnIndexOrThrow(FavoritesTable.SID));
				
				// Create a new song
				final Song song = SongFactory.newSong(HostType.LOCAL, id);
				song.setName(mCursor.getString(mCursor
						.getColumnIndexOrThrow(FavoritesTable.NAME)));
				song.setArtistName(mCursor.getString(mCursor
						.getColumnIndexOrThrow(FavoritesTable.ARTIST)));
				song.setAlbumName( mCursor.getString(mCursor
						.getColumnIndexOrThrow(FavoritesTable.ALBUM)));

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
	 * @return The {@link Cursor} used to run the favorites query.
	 */
	public static final Cursor makeFavoritesCursor(final Context context) {
		return FavoritesStore
				.getInstance(context)
				.getReadableDatabase()
				.query(FavoritesTable.TABLE_NAME,
						new String[] { FavoritesTable.SID + " as _id",
								FavoritesTable.SID, FavoritesTable.NAME,
								FavoritesTable.ALBUM, FavoritesTable.ARTIST,
								FavoritesTable.PLAY_COUNT }, null, null, null,
						null, FavoritesTable.PLAY_COUNT + " DESC");
	}
}
