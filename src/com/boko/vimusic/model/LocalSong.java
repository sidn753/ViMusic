package com.boko.vimusic.model;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;
import android.text.TextUtils;

import com.boko.vimusic.utils.CommonUtils;

/**
 * A class that represents a song.
 * 
 */
public class LocalSong extends Song {
	private static final long serialVersionUID = 1L;

	private static final String[] PROJECTION = new String[] {
			"audio._id AS _id", MediaStore.Audio.Media.ARTIST,
			MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.TITLE,
			MediaStore.Audio.Media.DURATION };

	public LocalSong(String id) {
		super(id);
		mHost = HostType.LOCAL;
	}

	protected void doQuery(final Context context) {
		synchronized (this) {
			if (getId() != null && getId().length() > 0 && TextUtils.isDigitsOnly(getId())) {
				if (context != null) {
					Cursor c = context.getContentResolver().query(
							MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
							PROJECTION, "_id=" + mId, null, null);
					if (c != null) {
						if (c.getCount() > 0) {
							c.moveToNext();
							mArtistName = CommonUtils.getUnicode(c.getString(1));
							mAlbumName = CommonUtils.getUnicode(c.getString(2));
							mName = CommonUtils.getUnicode(c.getString(3));
							mDuration = (int) (c.getLong(4) / 1000);
						}
						c.close();
						c = null;
					}
				}
				mLinkPlay = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "/"
						+ getId();
			}
		}
	}
}
