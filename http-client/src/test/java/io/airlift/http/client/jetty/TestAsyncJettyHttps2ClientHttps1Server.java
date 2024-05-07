package io.airlift.http.client.jetty;

import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.HttpVersion;

public class TestAsyncJettyHttps2ClientHttps1Server
        extends TestAsyncJettyHttps1ClientHttps1Server
{
    @Override
    protected HttpClientConfig createClientConfig()
    {
        return super.createClientConfig().setHttp2Enabled(true);
    }

    @Override
    protected HttpVersion expectedProtocolVersion()
    {
        return HttpVersion.HTTP_2; // upgraded to H2C
    }
}
