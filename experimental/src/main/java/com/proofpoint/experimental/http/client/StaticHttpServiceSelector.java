package com.proofpoint.experimental.http.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.net.URI;
import java.util.List;

public class StaticHttpServiceSelector implements HttpServiceSelector
{
    private final List<URI> uris;

    public StaticHttpServiceSelector(URI... uris)
    {
        Preconditions.checkNotNull(uris, "uris is null");
        this.uris = ImmutableList.copyOf(uris);
    }

    public StaticHttpServiceSelector(List<URI> uris)
    {
        Preconditions.checkNotNull(uris, "uris is null");
        this.uris = ImmutableList.copyOf(uris);
    }

    @Override
    public List<URI> selectHttpService()
    {
        return uris;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("StaticHttpServiceSelector");
        sb.append("{uris=").append(uris);
        sb.append('}');
        return sb.toString();
    }
}
