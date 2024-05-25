package io.airlift.http.server.testing;

import io.airlift.http.server.HttpServerFeatures;

public class TestTestingHttpServerWithAllEnabled
        extends AbstractTestTestingHttpServer
{
    TestTestingHttpServerWithAllEnabled()
    {
        super(HttpServerFeatures.builder()
                .withVirtualThreads(true)
                .withLegacyUriCompliance(true)
                .build());
    }
}
