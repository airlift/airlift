package com.facebook.airlift.http.client.jetty;

import com.facebook.airlift.http.client.HttpClientConfig;

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
