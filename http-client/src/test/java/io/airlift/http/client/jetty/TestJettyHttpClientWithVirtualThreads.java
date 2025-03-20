package io.airlift.http.client.jetty;

import io.airlift.http.client.HttpClientConfig;

public class TestJettyHttpClientWithVirtualThreads
        extends TestJettyHttpClient
{
    @Override
    protected HttpClientConfig createClientConfig()
    {
        return super.createClientConfig()
                .setUseVirtualThreads(true);
    }
}
