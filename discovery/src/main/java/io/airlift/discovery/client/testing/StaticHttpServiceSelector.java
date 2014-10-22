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
package io.airlift.discovery.client.testing;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import io.airlift.discovery.client.HttpServiceSelector;

import java.net.URI;
import java.util.List;

import static com.google.common.base.MoreObjects.toStringHelper;
import static io.airlift.discovery.client.ServiceSelectorConfig.DEFAULT_POOL;

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
        return toStringHelper(this)
                .add("type", type)
                .add("pool", pool)
                .add("uris", uris)
                .toString();
    }
}
