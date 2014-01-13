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

package com.boko.vimusic.model;


/**
 * A class that represents an album.
 * 
 */
public class Album extends Media {

    /**
     * The unique Id of the album
     */
    public long mAlbumId;

    /**
     * The album artist
     */
    public String mArtistName;

    /**
     * The number of songs in the album
     */
    public int mSongNumber;

    /**
     * The year the album was released
     */
    public String mYear;

    /**
     * Constructor of <code>Album</code>
     * 
     * @param albumId The Id of the album
     * @param albumName The name of the album
     * @param artistName The album artist
     * @param songNumber The number of songs in the album
     * @param albumYear The year the album was released
     */
    public Album(final long albumId, final String albumName, final String artistName,
            final int songNumber, final String albumYear) {
        super();
        mAlbumId = albumId;
        setName(albumName);
        mArtistName = artistName;
        mSongNumber = songNumber;
        mYear = albumYear;
    }
}
