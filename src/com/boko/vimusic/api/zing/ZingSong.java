package com.boko.vimusic.api.zing;

import java.util.Vector;

import android.content.Context;

import com.boko.vimusic.api.Caller;
import com.boko.vimusic.api.DomElement;
import com.boko.vimusic.api.HTMLLinkExtractor;
import com.boko.vimusic.api.Result;
import com.boko.vimusic.model.HostType;

public class ZingSong extends com.boko.vimusic.model.Song {
	private static final long serialVersionUID = 1L;
	private static final String DETAIL_URL = "http://mp3.zing.vn/bai-hat/bai-hat/";
	private static final String XML_URL = "http://mp3.zing.vn/xml/song-xml/";
	private static final String DOWNLOAD_URL = "http://mp3.zing.vn/download/song/";

	public ZingSong(String id) {
		super(id);
		mHost = HostType.ZING;
	}

	protected void doQuery(final Context context) {
		synchronized (this) {
			if (getId() != null) {
				String url = DETAIL_URL + getId() + ".html";
				final Result result = Caller.getInstance().call(url);
				readFromElement(result.getResultRaw());
			}
		}
	}
	
	private void readFromElement(final String htmlResponse) {
		if (htmlResponse == null) {
			return;
		}
		Vector<String> urls = HTMLLinkExtractor.grabHTMLLinks(htmlResponse);

		for (String url : urls) {
			if (url.startsWith(XML_URL) && mLinkPlay == null) {
				final Result rs = Caller.getInstance().call(
						url.replace("&amp;", "&"));
				DomElement element = rs.getContentElement();
				mName = element.getChildText("title");
				mArtistName = element.getChildText("performer");
				mLinkPlay = element.getChildText("source");
			}
			if (url.startsWith(DOWNLOAD_URL)) {
				mLinkDownload = url;
			}
		}
	}

}
