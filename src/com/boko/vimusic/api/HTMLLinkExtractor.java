package com.boko.vimusic.api;

import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HTMLLinkExtractor {

	private static Pattern patternTag;

	private static final String HTML_PATTERN = "(http|ftp|https):\\/\\/[\\w\\-_]+(\\.[\\w\\-_]+)+([\\w\\-\\.,@?^=%&amp;:/~\\+#]*[\\w\\-\\@?^=%&amp;/~\\+#])?";
	
	static {
		patternTag = Pattern.compile(HTML_PATTERN);
	}

	/**
	 * Validate html with regular expression
	 * 
	 * @param html
	 *            html content for validation
	 * @return Vector links and link text
	 */
	public static Vector<String> grabHTMLLinks(final String html) {

		Vector<String> result = new Vector<String>();

		Matcher matcherTag = patternTag.matcher(html);

		while (matcherTag.find()) {
			result.add(matcherTag.group());
		}

		return result;

	}
}