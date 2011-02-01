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
