package com.boko.vimusic.api.zing;

import java.util.Vector;

import android.content.Context;

import com.boko.vimusic.api.Caller;
import com.boko.vimusic.api.DomElement;
import com.boko.vimusic.api.HTMLLinkExtractor;
import com.boko.vimusic.api.Result;

public class Song extends com.boko.vimusic.model.Song {

	private static final String DETAIL_URL = "http://mp3.zing.vn/bai-hat/bai-hat/";
	private static final String XML_URL = "http://mp3.zing.vn/xml/song-xml/";
	private static final String DOWNLOAD_URL = "http://mp3.zing.vn/download/song/";

	public Song() {
		super();
	}

	public static Song getDetail(final Context context, final String sId) {
		String url = DETAIL_URL + sId + ".html";
		final Result result = Caller.getInstance(context).call(url);
		return createItemFromElement(context, result.getResultRaw());
	}

	public static Song createItemFromElement(final Context context,
			final String htmlResponse) {
		if (htmlResponse == null) {
			return null;
		}
		final Song song = new Song();

		Vector<String> urls = HTMLLinkExtractor.grabHTMLLinks(htmlResponse);

		for (String url : urls) {
			if (url.startsWith(XML_URL) && song.getLinkPlay() == null) {
				final Result rs = Caller.getInstance(context).call(
						url.replace("&amp;", "&"));
				DomElement element = rs.getContentElement();
				song.setName(element.getChildText("title"));
				song.mArtistName = element.getChildText("performer");
				song.mLinkPlay = element.getChildText("source");
			}
			if (url.startsWith(DOWNLOAD_URL)) {
				song.mLinkDownload = url;
			}
		}
		return song;
	}

}
