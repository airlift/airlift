package io.airlift.http.client.jetty;

public class NoopLogger
        implements HttpClientLogger
{
    @Override
    public void log(RequestInfo requestInfo, ResponseInfo responseInfo)
    {
    }

    @Override
    public void close()
    {
    }
}
