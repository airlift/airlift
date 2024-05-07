package io.airlift.http.server.testing;

import io.airlift.http.server.HttpServerFeatures;

public class TestTestingHttpServerWithCaseSensitiveHeaderCache
        extends AbstractTestTestingHttpServer
{
    TestTestingHttpServerWithCaseSensitiveHeaderCache()
    {
        super(HttpServerFeatures.builder()
                .withCaseSensitiveHeaderCache(true)
                .build());
    }
}
