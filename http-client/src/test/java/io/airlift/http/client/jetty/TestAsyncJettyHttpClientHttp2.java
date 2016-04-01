package io.airlift.http.client.jetty;

import io.airlift.http.client.HttpClientConfig;

public class TestAsyncJettyHttpClientHttp2
        extends TestAsyncJettyHttpClient
{
    @Override
    protected HttpClientConfig createClientConfig()
    {
        return super.createClientConfig()
                .setHttp2Enabled(true);
    }
}
