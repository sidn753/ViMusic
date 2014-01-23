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
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AudioColumns;

import com.boko.vimusic.model.HostType;
import com.boko.vimusic.model.Song;
import com.boko.vimusic.model.SongFactory;
import com.boko.vimusic.utils.Lists;

/**
 * Used to query {@link MediaStore.Audio.Media.EXTERNAL_CONTENT_URI} and return
 * the Song the user added over the past four of weeks.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class LastAddedLoader extends WrappedAsyncTaskLoader<List<Song>> {

	/**
	 * The result
	 */
	private final ArrayList<Song> mSongList = Lists.newArrayList();

	/**
	 * The {@link Cursor} used to run the query.
	 */
	private Cursor mCursor;

	/**
	 * Constructor of <code>LastAddedHandler</code>
	 * 
	 * @param context
	 *            The {@link Context} to use.
	 */
	public LastAddedLoader(final Context context) {
		super(context);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Song> loadInBackground() {
		// Create the Cursor
		mCursor = makeLastAddedCursor(getContext());
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
	 * @return The {@link Cursor} used to run the song query.
	 */
	public static final Cursor makeLastAddedCursor(final Context context) {
		final int fourWeeks = 4 * 3600 * 24 * 7;
		final StringBuilder selection = new StringBuilder();
		selection.append(AudioColumns.IS_MUSIC + "=1");
		selection.append(" AND " + AudioColumns.TITLE + " != ''"); //$NON-NLS-2$
		selection.append(" AND " + MediaStore.Audio.Media.DATE_ADDED + ">"); //$NON-NLS-2$
		selection.append(System.currentTimeMillis() / 1000 - fourWeeks);
		return context.getContentResolver().query(
				MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, new String[] {
				/* 0 */
				BaseColumns._ID,
				/* 1 */
				AudioColumns.TITLE,
				/* 2 */
				AudioColumns.ARTIST,
				/* 3 */
				AudioColumns.ALBUM }, selection.toString(), null,
				MediaStore.Audio.Media.DATE_ADDED + " DESC");
	}
}
