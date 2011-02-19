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
package com.proofpoint.platform.sample;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;

public class MockUriInfo
    implements UriInfo
{
    private final URI requestUri;

    public static UriInfo from(URI requestUri)
    {
        return new MockUriInfo(requestUri);
    }

    private MockUriInfo(URI requestUri)
    {
        this.requestUri = requestUri;
    }

    @Override
    public String getPath()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getPath(boolean decode)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<PathSegment> getPathSegments()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<PathSegment> getPathSegments(boolean decode)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public URI getRequestUri()
    {
        return requestUri;
    }

    @Override
    public UriBuilder getRequestUriBuilder()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public URI getAbsolutePath()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public UriBuilder getAbsolutePathBuilder()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public URI getBaseUri()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public UriBuilder getBaseUriBuilder()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public MultivaluedMap<String, String> getPathParameters()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public MultivaluedMap<String, String> getPathParameters(boolean decode)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public MultivaluedMap<String, String> getQueryParameters()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public MultivaluedMap<String, String> getQueryParameters(boolean decode)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getMatchedURIs()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getMatchedURIs(boolean decode)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Object> getMatchedResources()
    {
        throw new UnsupportedOperationException();
    }
}
