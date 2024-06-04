package io.airlift.http.server.testing;

public class TestTestingHttpServerWithLegacyUriCompliance
        extends AbstractTestTestingHttpServer
{
    TestTestingHttpServerWithLegacyUriCompliance()
    {
        super(false, true, false);
    }
}
