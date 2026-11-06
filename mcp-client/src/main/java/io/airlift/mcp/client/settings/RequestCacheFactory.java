package io.airlift.mcp.client.settings;

import java.net.URI;

public interface RequestCacheFactory
{
    RequestCache createRequestCache(URI uri);
}
