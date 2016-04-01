package io.airlift.http.client.jetty;

import io.airlift.http.client.HttpClientConfig;

public class TestJettyHttpClientSocksProxyHttp2
        extends TestJettyHttpClientSocksProxy
{
    @Override
    protected HttpClientConfig createClientConfig()
    {
        return super.createClientConfig()
                .setHttp2Enabled(true);
    }
}
