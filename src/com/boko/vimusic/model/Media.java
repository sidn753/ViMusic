package com.boko.vimusic.model;

/**
 * A class that represents a generic media.
 */
public class Media {

	/**
	 * Host's unique id of a media.
	 */
	private String mId;
	
	/**
	 * Media host.
	 */
	private String mHost;
	
	/**
	 * Media name.
	 */
	private String mName;
	
	/**
	 * Media avatar url.
	 */
	private String mAvatarUrl;

	public String getId() {
		return mId;
	}

	public void setId(String id) {
		this.mId = id;
	}

	public String getHost() {
		return mHost;
	}

	public void setHost(String host) {
		this.mHost = host;
	}

	public String getName() {
		return mName;
	}

	public void setName(String name) {
		this.mName = name;
	}

	public String getAvatarUrl() {
		return mAvatarUrl;
	}

	public void setAvatarUrl(String avatarUrl) {
		this.mAvatarUrl = avatarUrl;
	}
}
