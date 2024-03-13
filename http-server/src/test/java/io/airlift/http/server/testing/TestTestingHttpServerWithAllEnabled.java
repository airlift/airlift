package io.airlift.http.server.testing;

public class TestTestingHttpServerWithAllEnabled
        extends AbstractTestTestingHttpServer
{
    TestTestingHttpServerWithAllEnabled()
    {
        super(true, true);
    }
}
