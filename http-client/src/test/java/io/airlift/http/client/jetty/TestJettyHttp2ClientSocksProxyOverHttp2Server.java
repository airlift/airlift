package io.airlift.http.client.jetty;

import io.airlift.http.client.HttpClientConfig;

public class TestJettyHttp2ClientSocksProxyOverHttp2Server
        extends TestJettyHttp1ClientSocksProxyOverHttp1Server
{
    @Override
    protected HttpClientConfig createClientConfig()
    {
        return super.createClientConfig()
                .setHttp2Enabled(true);
    }
}
