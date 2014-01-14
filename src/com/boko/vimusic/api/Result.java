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

package com.boko.vimusic.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * The <code>Result</code> class contains the response sent by the server, i.e.
 * the status (either ok or failed), an error code and message if failed and the
 * xml response sent by the server.
 * 
 * @author Janni Kovacs
 */
public class Result {

	protected String resultRaw;

	protected Document resultDocument;

	/**
	 * @param resultRaw
	 */
	protected Result(final String resultRaw) {
		this.resultRaw = resultRaw;
	}

	public Document getResultDocument() {
		if (resultDocument == null && resultRaw != null) {
			try {
				resultDocument = newDocumentBuilder().parse(
						new ByteArrayInputStream(resultRaw.getBytes("UTF-8")));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return resultDocument;
	}

	public DomElement getContentElement() {
		return new DomElement(getResultDocument().getDocumentElement())
				.getChild("*");
	}

	public String getResultRaw() {
		return resultRaw;
	}

	/**
	 * @return
	 */
	private DocumentBuilder newDocumentBuilder() {
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
