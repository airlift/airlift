package io.airlift.http.client.jetty;

import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.HttpVersion;
import io.airlift.http.client.Request;
import io.airlift.http.client.ResponseHandler;

public class TestJettyHttpClientHttp2
        extends TestJettyHttpClient
{
    @Override
    protected HttpClientConfig createClientConfig()
    {
        return super.createClientConfig()
                .setHttp2Enabled(true);
    }

    @Override
    public <T, E extends Exception> T executeRequest(HttpClientConfig config, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        return super.executeRequest(config, upgradeRequest(request, HttpVersion.HTTP_2), responseHandler);
    }

    @Override
    public <T, E extends Exception> T executeRequest(Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        return super.executeRequest(upgradeRequest(request, HttpVersion.HTTP_2), responseHandler);
    }
}
