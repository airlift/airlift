package com.proofpoint.experimental.http.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.net.URI;
import java.util.List;

import static com.proofpoint.experimental.discovery.client.ServiceSelectorConfig.DEFAULT_POOL;

public class StaticHttpServiceSelector implements HttpServiceSelector
{
    private static final String UNKNOWN_TYPE = "unknown";
    private final String type;
    private final String pool;
    private final List<URI> uris;

    public StaticHttpServiceSelector(URI uri, URI... uris)
    {
        this(UNKNOWN_TYPE, DEFAULT_POOL, uri, uris);
    }

    public StaticHttpServiceSelector(String type, URI uri, URI... uris)
    {
        this(type, DEFAULT_POOL, uri, uris);
    }

    public StaticHttpServiceSelector(String type, String pool, URI uri, URI... uris)
    {
        Preconditions.checkNotNull(type, "type is null");
        Preconditions.checkNotNull(pool, "pool is null");
        Preconditions.checkNotNull(uri, "uri is null");
        Preconditions.checkNotNull(uris, "uris is null");

        this.type = type;
        this.pool = pool;
        this.uris = ImmutableList.<URI>builder().add(uri).add(uris).build();
    }

    public StaticHttpServiceSelector(Iterable<URI> uris)
    {
        this(UNKNOWN_TYPE, DEFAULT_POOL, uris);
    }

    public StaticHttpServiceSelector(String type, Iterable<URI> uris)
    {
        this(type, DEFAULT_POOL, uris);

    }

    public StaticHttpServiceSelector(String type, String pool, Iterable<URI> uris)
    {
        Preconditions.checkNotNull(type, "type is null");
        Preconditions.checkNotNull(pool, "pool is null");
        Preconditions.checkNotNull(uris, "uris is null");
        this.type = type;
        this.pool = pool;
        this.uris = ImmutableList.copyOf(uris);
    }

    public String getType()
    {
        return type;
    }

    public String getPool()
    {
        return pool;
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
        sb.append("{type='").append(type).append('\'');
        sb.append(", pool='").append(pool).append('\'');
        sb.append(", uris=").append(uris);
        sb.append('}');
        return sb.toString();
    }
}
