/* Copyright (c) restSQL Project Contributors. Licensed under MIT. */
package org.restsql.core.impl;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.restsql.core.Factory;
import org.restsql.core.HttpRequestAttributes;
import org.restsql.core.InvalidRequestException;
import org.restsql.core.NameValuePair;
import org.restsql.core.Request;
import org.restsql.core.RequestLogger;
import org.restsql.core.RequestUtil;
import org.restsql.core.SqlResource;
import org.restsql.core.SqlResourceException;
import org.restsql.core.Factory.RequestFactory;
import org.restsql.core.Factory.SqlResourceFactoryException;
import org.restsql.core.Request.Type;

/**
 * Creates requests.
 * 
 * @author Mark Sawers
 */
public class RequestFactoryImpl implements RequestFactory {

	@Override
	public Request getChildRequest(final Request parentRequest) {
		final RequestImpl childRequest = new RequestImpl(parentRequest.getHttpRequestAttributes(),
				parentRequest.getType(), parentRequest.getSqlResource(),
				parentRequest.getResourceIdentifiers(), null, null, parentRequest.getLogger());
		childRequest.setParent(parentRequest);
		return childRequest;
	}

	/**
	 * Builds request from URI. Assumes pattern
	 * <code>res/{resourceName}/{resId1}/{resId2}?{param1}={value1}&{param2}={value2}</code>. Used by the test harness,
	 * Java API clients and perhaps a straight servlet implementation.
	 */
	@Override
	public Request getRequest(final HttpRequestAttributes httpAttributes) throws InvalidRequestException,
			SqlResourceFactoryException, SqlResourceException {
		int elements = 0;
		String resName = null, path, query = null;
		List<NameValuePair> resIds = null;
		List<NameValuePair> params = null;

		String uri;
		try {
			uri = URLDecoder.decode(httpAttributes.getUri(), "UTF-8");
		} catch (final UnsupportedEncodingException exception) {
			throw new InvalidRequestException("Problem decoding uri: " + httpAttributes.getUri() + " - "
					+ exception.getMessage());
		}

		final int queryIndex = uri.indexOf('?');
		if (queryIndex > 0) {
			query = uri.substring(queryIndex + 1);
			path = uri.substring(0, queryIndex);
		} else {
			path = uri;
		}

		// Parse path params
		StringTokenizer tokenizer = new StringTokenizer(path, "/");
		String[] resIdValues = null;
		while (tokenizer.hasMoreElements()) {
			elements++;
			final String element = (String) tokenizer.nextElement();
			if (elements == 2) {
				resName = element;
			} else if (elements > 2) {
				if (resIdValues == null) {
					resIdValues = new String[4];
				}
				resIdValues[elements - 2] = element;
			}
		}

		if (resIdValues != null) {
			final SqlResource sqlResource = Factory.getSqlResource(resName);
			resIds = RequestUtil.getResIds(sqlResource, resIdValues);
		}

		// Parse query params
		if (query != null) {
			tokenizer = new StringTokenizer(query, "&");
			while (tokenizer.hasMoreElements()) {
				final String element = (String) tokenizer.nextElement();
				if (params == null) {
					params = new ArrayList<NameValuePair>(4);
				}
				final int equalsIndex = element.indexOf('=');
				params.add(new NameValuePair(element.substring(0, equalsIndex), element
						.substring(equalsIndex + 1)));
			}
		}

		RequestUtil.checkForInvalidMultipleParameters(params);

		final String responseMediaType = RequestUtil.getResponseMediaType(params,
				httpAttributes.getRequestMediaType(), httpAttributes.getResponseMediaType());
		httpAttributes.setResponseMediaType(responseMediaType);
		final RequestLogger requestLogger = Factory.getRequestLogger();
		final Request.Type type = Request.Type.fromHttpMethod(httpAttributes.getMethod());
		return new RequestImpl(httpAttributes, type, resName, resIds, params, null, requestLogger);
	}

	/**
	 * Returns request object with pre-parsed data from the URI. Used by service and possibly Java API clients.
	 */
	@Override
	public Request getRequest(final HttpRequestAttributes httpAttributes, final Type type,
			final String sqlResource, final List<NameValuePair> resIds, final List<NameValuePair> params,
			final List<List<NameValuePair>> childrenParams, final RequestLogger requestLogger)
			throws InvalidRequestException {
		// Verify expectations
		if (sqlResource == null) {
			throw new InvalidRequestException(InvalidRequestException.MESSAGE_SQLRESOURCE_REQUIRED);
		}

		switch (type) {
			case INSERT:
				if (params == null && childrenParams == null) {
					throw new InvalidRequestException(InvalidRequestException.MESSSAGE_INSERT_MISSING_PARAMS);
				}
				break;
			case UPDATE:
				if (params == null && childrenParams == null) {
					throw new InvalidRequestException(InvalidRequestException.MESSSAGE_UPDATE_MISSING_PARAMS);
				}
				break;
			default:
		}
		RequestUtil.checkForInvalidMultipleParameters(params);

		return new RequestImpl(httpAttributes, type, sqlResource, resIds, params, childrenParams,
				requestLogger);
	}
}
