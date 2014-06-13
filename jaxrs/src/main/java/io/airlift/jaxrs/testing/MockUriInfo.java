/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.jaxrs.testing;
/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.List;

public class MockUriInfo implements UriInfo
{
    private static final Splitter PATH_SPLITTER = Splitter.on('/');
    private static final Splitter QUERY_STRING_SPLITTER = Splitter.on('&').trimResults().omitEmptyStrings();
    private static final Splitter QUERY_PARAM_SPLITTER = Splitter.on('=');
    private static final Joiner QUERY_PARAM_VALUE_JOINER = Joiner.on("");

    private final URI requestUri;
    private final URI baseUri;


    public static UriInfo from(String requestUri)
    {
        return new MockUriInfo(URI.create(requestUri));
    }

    public static UriInfo from(URI requestUri)
    {
        return new MockUriInfo(requestUri);
    }

    public MockUriInfo(URI requestUri)
    {
        this(requestUri, requestUri.resolve("/"));
    }

    public MockUriInfo(URI requestUri, URI baseUri)
    {
        this.requestUri = requestUri;
        this.baseUri = baseUri;
    }

    @Override
    public URI getBaseUri()
    {
        return baseUri;
    }

    @Override
    public UriBuilder getBaseUriBuilder()
    {
        return UriBuilder.fromUri(getBaseUri());
    }

    @Override
    public URI getRequestUri()
    {
        return requestUri;
    }

    @Override
    public UriBuilder getRequestUriBuilder()
    {
        return UriBuilder.fromUri(getRequestUri());
    }

    @Override
    public URI getAbsolutePath()
    {
        return UriBuilder.fromUri(requestUri).replaceQuery("").fragment("").build();
    }

    @Override
    public UriBuilder getAbsolutePathBuilder()
    {
        return UriBuilder.fromUri(getAbsolutePath());
    }

    @Override
    public String getPath()
    {
        return getPath(true);
    }

    @Override
    public String getPath(boolean decode)
    {
        // todo decode is ignored
        return getRequestUri().getRawPath().substring(getBaseUri().getRawPath().length());
    }

    @Override
    public List<PathSegment> getPathSegments()
    {
        return getPathSegments(true);
    }

    @Override
    public List<PathSegment> getPathSegments(boolean decode)
    {
        Builder<PathSegment> builder = ImmutableList.builder();
        for (String path : PATH_SPLITTER.split(getPath(decode))) {
            builder.add(new ImmutablePathSegment(path));
        }
        return builder.build();
    }

    @Override
    public MultivaluedMap<String, String> getQueryParameters()
    {
        return getQueryParameters(true);
    }

    @Override
    public MultivaluedMap<String, String> getQueryParameters(boolean decode)
    {
        return decodeQuery(getRequestUri().getRawQuery(), decode);
    }

    @Override
    public URI resolve(URI uri)
    {
        return baseUri.resolve(uri);
    }

    @Override
    public URI relativize(URI uri)
    {
        if (!uri.isAbsolute()) {
            uri = resolve(uri);
        }
        return baseUri.resolve(uri);
    }

    public static MultivaluedMap<String, String> decodeQuery(String query, boolean decode)
    {
        if (query == null) {
            return new GuavaMultivaluedMap<>();
        }

        ArrayListMultimap<String, String> map = ArrayListMultimap.create();
        for (String param : QUERY_STRING_SPLITTER.split(query)) {
            List<String> pair = ImmutableList.copyOf(QUERY_PARAM_SPLITTER.split(param));
            if (pair.isEmpty()) {
                continue;
            }

            String key = urlDecode(pair.get(0));
            String value = null;
            if (pair.size() == 1) {

            }
            else {
                value = QUERY_PARAM_VALUE_JOINER.join(pair.subList(1, pair.size()));
                if (decode) {
                    value = urlDecode(value);
                }
            }
            map.put(key, value);
        }

        return new GuavaMultivaluedMap<>(map);
    }

    private static String urlDecode(String value)
    {
        try {
            return URLDecoder.decode(value, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public MultivaluedMap<String, String> getPathParameters()
    {
        return getPathParameters(true);
    }

    @Override
    public MultivaluedMap<String, String> getPathParameters(boolean decode)
    {
        // this requires knowledge of @Path
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getMatchedURIs()
    {
        return getMatchedURIs(true);
    }

    @Override
    public List<String> getMatchedURIs(boolean decode)
    {
        // this requires knowledge of @Path
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Object> getMatchedResources()
    {
        // this requires knowledge of @Path
        throw new UnsupportedOperationException();
    }


    private static class ImmutablePathSegment implements PathSegment
    {
        private final String path;

        public ImmutablePathSegment(String path)
        {
            this.path = path;
        }

        @Override
        public String getPath()
        {
            return path;
        }

        @Override
        public MultivaluedMap<String, String> getMatrixParameters()
        {
            return new GuavaMultivaluedMap<>();
        }
    }
}
