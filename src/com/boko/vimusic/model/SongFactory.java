package com.boko.vimusic.model;

import com.boko.vimusic.api.zing.ZingSong;

public class SongFactory {
	public static Song newSong(HostType host, String id) {
		switch (host) {
		case LOCAL:
			return new LocalSong(id);
		case ZING:
			return new ZingSong(id);
		default:
			throw new IllegalArgumentException();
		}
	}
}
