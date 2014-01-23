package com.boko.vimusic.model;

public class SongFactory {
	public static AbstractSong newSongFromHost(HostType host) {
		switch (HostType) {
			LOCAL:
				return new Song();
			ZING:
				return new com.boko.vimusic.api.zing.Song();
			default:
				throw new IllegalArgumentException();
		}
	}
}
