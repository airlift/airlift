package io.airlift.http.server.testing;

import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.server.HttpServerConfig;

public class TestHttpServerGzipCompression
        extends AbstractTestHttpServerCompression
{
    @Override
    HttpServerConfig httpServerConfig()
    {
        return new HttpServerConfig().setDeflateCompressionEnabled(true);
    }

    @Override
    HttpClientConfig httpClientConfig()
    {
        return new HttpClientConfig().setDeflateCompressionEnabled(true);
    }

    @Override
    String acceptEncoding()
    {
        return "gzip";
    }

    @Override
    int minCompressionSize()
    {
        return 32;
    }
}
