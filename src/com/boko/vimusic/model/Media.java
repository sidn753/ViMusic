package com.boko.vimusic.model;

import android.text.TextUtils;

/**
 * A class that represents a generic media.
 */
public abstract class Media {
	
	/**
	 * Media host.
	 */
	protected HostType mHost;
	
	/**
	 * Host's unique id of a media.
	 */
	protected String mId;

	/**
	 * Media name.
	 */
	protected String mName;

	/**
	 * Media avatar url.
	 */
	protected String mAvatarUrl;
	
	public HostType getHost() {
		return mHost;
	}

	public String getId() {
		return mId;
	}

	public String getName() {
		return mName;
	}

	public String getAvatarUrl() {
		return mAvatarUrl;
	}
	
	public void setName(String mName) {
		this.mName = mName;
	}

	public void setAvatarUrl(String mAvatarUrl) {
		this.mAvatarUrl = mAvatarUrl;
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj == null) {
			return false;
		}
		final Media other = (Media) obj;
		if (getHost().getCode() != other.getHost().getCode()) {
			return false;
		}
		if (!TextUtils.equals(getId(), other.getId())) {
			return false;
		}
		return true;
	}
}
