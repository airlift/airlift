package io.airlift.http.server.testing;

import io.airlift.http.server.HttpServerFeatures;

public class TestTestingHttpServerWithLegacyUriCompliance
        extends AbstractTestTestingHttpServer
{
    TestTestingHttpServerWithLegacyUriCompliance()
    {
        super(HttpServerFeatures.builder()
                .withLegacyUriCompliance(true)
                .build());
    }
}
