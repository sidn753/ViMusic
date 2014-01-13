/*
 * Copyright (c) 2012, the Last.fm Java Project and Committers All rights
 * reserved. Redistribution and use of this software in source and binary forms,
 * with or without modification, are permitted provided that the following
 * conditions are met: - Redistributions of source code must retain the above
 * copyright notice, this list of conditions and the following disclaimer. -
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution. THIS SOFTWARE IS
 * PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.boko.vimusic.api.lastfm;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import android.content.Context;

import com.boko.vimusic.Config;
import com.boko.vimusic.api.Caller;
import com.boko.vimusic.api.DomElement;
import com.boko.vimusic.api.Result;

/**
 * Wrapper class for Album related API calls and Album Bean.
 * 
 * @author Janni Kovacs
 */
public class Album extends com.boko.vimusic.model.Album {

    public Album(String albumId, String albumName, String artistName,
			int songNumber, String albumYear) {
		super(albumId, albumName, artistName, songNumber, albumYear);
	}
	
    /**
     * Get the metadata for an album on Last.fm using the album name or a
     * musicbrainz id. See playlist.fetch on how to get the album playlist.
     * 
     * @param artist Artist's name
     * @param albumOrMbid Album name or MBID
     * @return Album metadata
     */
    public final static Album getInfo(final Context context, final String artist,
            final String albumOrMbid) {
        return getInfo(context, artist, albumOrMbid, null, Config.LASTFM_API_KEY);
    }

    /**
     * Get the metadata for an album on Last.fm using the album name or a
     * musicbrainz id. See playlist.fetch on how to get the album playlist.
     * 
     * @param artist Artist's name
     * @param albumOrMbid Album name or MBID
     * @param username The username for the context of the request. If supplied,
     *            the user's playcount for this album is included in the
     *            response.
     * @param apiKey The API key
     * @return Album metadata
     */
    public final static Album getInfo(final Context context, final String artist,
            final String albumOrMbid, final String username, final String apiKey) {
        final Map<String, String> params = new HashMap<String, String>();
        params.put("method","album.getInfo");
        params.put("artist", artist);
        params.put("album", albumOrMbid);
        params.put("username", username);
        params.put("api_key", apiKey);
        final Result result = Caller.getInstance(context).call("http://ws.audioscrobbler.com/2.0/", params);
        return createItemFromElement(result.getContentElement());
    }
    
    public static Album createItemFromElement(final DomElement element) {
        if (element == null) {
            return null;
        }
        final Album album = new Album(null, null, null, 0, null);
        
        final Collection<DomElement> images = element.getChildren("image");
        for (final DomElement image : images) {
            final String attribute = image.getAttribute("size");
            ImageSize size = null;
            if (attribute == null) {
                size = ImageSize.UNKNOWN;
            } else {
                try {
                    size = ImageSize.valueOf(attribute.toUpperCase(Locale.ENGLISH));
                } catch (final IllegalArgumentException e) {
                    // if they suddenly again introduce a new image size
                }
            }
            if (size == ImageSize.EXTRALARGE) {
            	album.setAvatarUrl(image.getText());
            }
        }
        
        if (element.hasChild("artist")) {
            album.mArtistName = element.getChild("artist").getChildText("name");
            if (album.mArtistName == null) {
                album.mArtistName = element.getChildText("artist");
            }
        }
        return album;
    }
}
