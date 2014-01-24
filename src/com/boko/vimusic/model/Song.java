package com.boko.vimusic.model;

import java.io.Serializable;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

public abstract class Song extends Media implements Parcelable, Serializable,
		Comparable<Song> {
	private static final long serialVersionUID = 1L;

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

	/**
	 * Link for play
	 */
	public String mLinkPlay;

	/**
	 * Link for download
	 */
	public String mLinkDownload;

	/**
	 * Data query executed or not
	 */
	private boolean queried = false;

	public Song(String id) {
		this.mId = id;
	}

	public void query(final Context context) {
		doQuery(context);
		queried = true;
	}

	protected abstract void doQuery(final Context context);

	public String getArtistName() {
		return mArtistName;
	}

	public String getAlbumName() {
		return mAlbumName;
	}

	public int getDuration() {
		return mDuration;
	}

	public String getLinkPlay() {
		return mLinkPlay;
	}

	public String getLinkDownload() {
		return mLinkDownload;
	}
	
	public void setArtistName(String mArtistName) {
		this.mArtistName = mArtistName;
	}

	public void setAlbumName(String mAlbumName) {
		this.mAlbumName = mAlbumName;
	}

	public void setDuration(int mDuration) {
		this.mDuration = mDuration;
	}

	public void setLinkPlay(String mLinkPlay) {
		this.mLinkPlay = mLinkPlay;
	}

	public void setLinkDownload(String mLinkDownload) {
		this.mLinkDownload = mLinkDownload;
	}
	
	public boolean isQueried() {
		return queried;
	}

	public static final Parcelable.Creator<Song> CREATOR = new Parcelable.Creator<Song>() {

		@Override
		public Song createFromParcel(Parcel src) {
			int host = src.readInt();
			String id = src.readString();
			String name = src.readString();
			String artistName = src.readString();
			
			Song song = SongFactory.newSong(HostType.getHost(host), id);
			song.setName(name);
			song.mArtistName = artistName;
			return song;
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
		dest.writeInt(getHost().getCode());
		dest.writeString(getId());
		dest.writeString(getName());
		dest.writeString(getArtistName());
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
			if (getHost().getCode() > obj.getHost().getCode()) {
				return 1;
			} else if (getHost().getCode() < obj.getHost().getCode()) {
				return -1;
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
