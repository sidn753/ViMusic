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

package com.boko.vimusic.ui.fragments.zing;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONObject;
import org.w3c.dom.Document;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;

import com.boko.vimusic.api.Caller;
import com.boko.vimusic.api.DomElement;
import com.boko.vimusic.api.Result;
import com.boko.vimusic.loaders.WrappedAsyncTaskLoader;
import com.boko.vimusic.model.Song;
import com.boko.vimusic.utils.Lists;

/**
 * Used to query {@link MediaStore.Audio.Media.EXTERNAL_CONTENT_URI} and return
 * the songs on a user's device.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class ZingSongLoader extends WrappedAsyncTaskLoader<List<Song>> {

	private static final String SEARCH_URL = "http://m.mp3.zing.vn/tim-kiem/bai-hat.html?search_type=bai-hat&act=more&q=";
	/**
	 * The result
	 */
	private final ArrayList<Song> mSongList = Lists.newArrayList();

	/**
	 * The {@link Cursor} used to run the query.
	 */
	private Cursor mCursor;

	/**
	 * Constructor of <code>SongLoader</code>
	 * 
	 * @param context
	 *            The {@link Context} to use
	 */
	public ZingSongLoader(final Context context) {
		super(context);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Song> loadInBackground() {
		final Result rs = Caller.getInstance(getContext()).call(
				SEARCH_URL + "o");
		String raw = rs.getResultRaw();
		JSONObject obj;
		try {
			obj = new JSONObject(raw);
			Document doc = newDocumentBuilder().parse(
					new ByteArrayInputStream(("<html>"
							+ obj.get("html").toString() + "</html>")
							.getBytes("UTF-8")));
			List<DomElement> links = new DomElement(doc.getDocumentElement())
					.getChildren("a");
			for (DomElement link : links) {
				Song song = new Song();
				song.mArtistName = link.getChild("h4").removeChild("span")
						.getText().trim();
				song.setName(link.getChildText("h3").trim());
				mSongList.add(song);
			}
		} catch (Exception e) {
		}
		return mSongList;
	}

	/**
	 * @return
	 */
	private static DocumentBuilder newDocumentBuilder() {
		try {
			final DocumentBuilderFactory builderFactory = DocumentBuilderFactory
					.newInstance();
			return builderFactory.newDocumentBuilder();
		} catch (final ParserConfigurationException e) {
			// better never happens
			throw new RuntimeException(e);
		}
	}
}
