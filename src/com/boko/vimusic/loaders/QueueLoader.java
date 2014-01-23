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
import com.boko.vimusic.utils.Lists;

/**
 * Used to return the current playlist or queue.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class QueueLoader extends WrappedAsyncTaskLoader<List<Song>> {

	/**
	 * The result
	 */
	private final ArrayList<Song> mSongList = Lists.newArrayList();

	/**
	 * The {@link Cursor} used to run the query.
	 */
	private NowPlayingCursor mCursor;

	/**
	 * Constructor of <code>QueueLoader</code>
	 * 
	 * @param context
	 *            The {@link Context} to use
	 */
	public QueueLoader(final Context context) {
		super(context);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Song> loadInBackground() {
		// Create the Cursor
		mCursor = new NowPlayingCursor(getContext());
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
	 * Creates the {@link Cursor} used to run the query.
	 * 
	 * @param context
	 *            The {@link Context} to use.
	 * @return The {@link Cursor} used to run the song query.
	 */
	public static final Cursor makeQueueCursor(final Context context) {
		final Cursor cursor = new NowPlayingCursor(context);
		return cursor;
	}
}
