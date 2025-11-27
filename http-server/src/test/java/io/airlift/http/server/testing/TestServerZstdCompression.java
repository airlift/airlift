package io.airlift.http.server.testing;

import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.server.HttpServerConfig;

public class TestServerZstdCompression
        extends AbstractTestHttpServerCompression
{
    @Override
    HttpServerConfig httpServerConfig()
    {
        return new HttpServerConfig().setZstdCompressionEnabled(true);
    }

    @Override
    HttpClientConfig httpClientConfig()
    {
        return new HttpClientConfig().setZstdCompressionEnabled(true);
    }

    @Override
    String acceptEncoding()
    {
        return "zstd";
    }

    @Override
    int minCompressionSize()
    {
        return 48;
    }
}
