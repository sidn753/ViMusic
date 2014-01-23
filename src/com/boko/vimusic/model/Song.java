package com.boko.vimusic.model;

import java.io.Serializable;

import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.text.TextUtils;

/**
 * A class that represents a song.
 * 
 */
public class Song extends Media implements Parcelable, Serializable,
		Comparable<Song> {

	/**
	 * The song artist
	 */
	public String mArtistName;

	/**
	 * The song album
	 */
	public String mAlbumName;

	/**
	 * The song duration in seconds
	 */
	public int mDuration;

	public String mLinkPlay;

	public String mLinkDownload;

	public Song() {
	}

	/**
	 * Constructor of <code>Song</code>
	 * 
	 * @param songId
	 *            The Id of the song
	 * @param songName
	 *            The name of the song
	 * @param artistName
	 *            The song artist
	 * @param albumName
	 *            The song album
	 * @param duration
	 *            The duration of a song in seconds
	 */
	public Song(final String songId, final String songName,
			final String artistName, final String albumName, final int duration) {
		setId(songId);
		setName(songName);
		mArtistName = artistName;
		mAlbumName = albumName;
		mDuration = duration;
	}

	public static final Parcelable.Creator<Song> CREATOR = new Parcelable.Creator<Song>() {

		@Override
		public Song createFromParcel(Parcel src) {

			String id = src.readString();

			String host = src.readString();

			return new Song(id, host, null, null, 0);
		}

		@Override
		public Song[] newArray(int size) {
			return new Song[size];
		}
	};

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(getId());
		dest.writeString(getHost());
	}

	public String getLinkPlay() {
		if (mLinkPlay != null) {
			return mLinkPlay;
		}
		if (getId() != null && TextUtils.isDigitsOnly(getId())) {
			mLinkPlay = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "/"
					+ getId();
		}
		return mLinkPlay;
	}

	@Override
	public int compareTo(Song obj) {
		if (obj == null) {
			return 1;
		}

		if (getHost() == null) {
			if (obj.getHost() != null) {
				return -1;
			}
		} else {
			if (getHost().compareTo(obj.getHost()) != 0) {
				return getHost().compareTo(obj.getHost());
			}
		}

		if (getId() == null) {
			if (obj.getId() != null) {
				return -1;
			}
		} else {
			if (getId().compareTo(obj.getId()) != 0) {
				return getId().compareTo(obj.getId());
			}
		}

		return 0;
	}
}
