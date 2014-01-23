package com.boko.vimusic.model;

public abstract class AbstractSong extends Media {
	
	private HostType host = null;
	
	public AbstractSong (HostType host) {
		this.host = host;
	}
	
	// Do subclass level processing in this method
	protected abstract void construct();

	public HostType getHost() {
		return host;
	}

	public void setHost(HostType host) {
		this.host = host;
	}
}
