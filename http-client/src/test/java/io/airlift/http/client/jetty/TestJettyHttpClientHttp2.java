package io.airlift.http.client.jetty;

import io.airlift.http.client.HttpClientConfig;

public class TestJettyHttpClientHttp2
        extends TestJettyHttpClient
{
    @Override
    protected HttpClientConfig createClientConfig()
    {
        return super.createClientConfig()
                .setHttp2Enabled(true);
    }
}
