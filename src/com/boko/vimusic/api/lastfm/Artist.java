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
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import android.content.Context;

import com.boko.vimusic.Config;
import com.boko.vimusic.api.Caller;
import com.boko.vimusic.api.DomElement;
import com.boko.vimusic.api.Result;

/**
 * Bean that contains artist information.<br/>
 * This class contains static methods that executes API methods relating to
 * artists.<br/>
 * Method names are equivalent to the last.fm API method names.
 * 
 * @author Janni Kovacs
 */
public class Artist extends com.boko.vimusic.model.Artist {

    /**
	 * @param artistId
	 * @param artistName
	 * @param songNumber
	 * @param albumNumber
	 */
	public Artist(long artistId, String artistName, int songNumber,
			int albumNumber) {
		super(artistId, artistName, songNumber, albumNumber);
	}

	/**
     * Retrieves detailed artist info for the given artist or mbid entry.
     * 
     * @param artistOrMbid Name of the artist or an mbid
     * @return detailed artist info
     */
    public final static Artist getInfo(final Context context, final String artistOrMbid) {
        return getInfo(context, artistOrMbid, Locale.getDefault(), Config.LASTFM_API_KEY);
    }

    /**
     * Retrieves detailed artist info for the given artist or mbid entry.
     * 
     * @param artistOrMbid Name of the artist or an mbid
     * @param locale The language to fetch info in, or <code>null</code>
     * @param apiKey The API key
     * @return detailed artist info
     */
    public final static Artist getInfo(final Context context, final String artistOrMbid,
            final Locale locale, final String apiKey) {
        final Map<String, String> mParams = new WeakHashMap<String, String>();
        mParams.put("method","artist.getInfo");
        mParams.put("artist", artistOrMbid);
        if (locale != null && locale.getLanguage().length() != 0) {
            mParams.put("lang", locale.getLanguage());
        }
        mParams.put("api_key", apiKey);
        final Result mResult = Caller.getInstance(context).call("http://ws.audioscrobbler.com/2.0/", mParams);
        return createItemFromElement(mResult.getContentElement());
    }

    /**
     * Use the last.fm corrections data to check whether the supplied artist has
     * a correction to a canonical artist. This method returns a new
     * {@link Artist} object containing the corrected data, or <code>null</code>
     * if the supplied Artist was not found.
     * 
     * @param artist The artist name to correct
     * @return a new {@link Artist}, or <code>null</code>
     */
    public final static Artist getCorrection(final Context context, final String artist) {
        Result result = null;
        try {
            result = Caller.getInstance(context).call("artist.getCorrection",
                    Config.LASTFM_API_KEY, "artist", artist);
            final DomElement correctionElement = result.getContentElement().getChild("correction");
            if (correctionElement == null) {
                return new Artist(0, artist, 0, 0);
            }
            final DomElement artistElem = correctionElement.getChild("artist");
            return createItemFromElement(artistElem);
        } catch (final Exception ignored) {
            return null;
        }
    }
    
    public static Artist createItemFromElement(final DomElement element) {
        if (element == null) {
            return null;
        }
        final Artist artist = new Artist(0, null, 0, 0);
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
            	artist.setAvatarUrl(image.getText());
            }
        }
        return artist;
    }
}
