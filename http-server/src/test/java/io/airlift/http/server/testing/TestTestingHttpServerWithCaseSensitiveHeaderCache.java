package io.airlift.http.server.testing;

public class TestTestingHttpServerWithCaseSensitiveHeaderCache
        extends AbstractTestTestingHttpServer
{
    TestTestingHttpServerWithCaseSensitiveHeaderCache()
    {
        super(false, false, true);
    }
}
