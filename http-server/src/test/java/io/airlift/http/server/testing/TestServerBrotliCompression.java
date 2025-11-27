package io.airlift.http.server.testing;

import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.server.HttpServerConfig;

public class TestServerBrotliCompression
        extends AbstractTestHttpServerCompression
{
    @Override
    HttpServerConfig httpServerConfig()
    {
        return new HttpServerConfig().setBrotliCompressionEnabled(true);
    }

    @Override
    HttpClientConfig httpClientConfig()
    {
        return new HttpClientConfig().setBrotliCompressionEnabled(true);
    }

    @Override
    String acceptEncoding()
    {
        return "br";
    }

    @Override
    int minCompressionSize()
    {
        return 48;
    }
}
