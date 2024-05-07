package io.airlift.http.client.jetty;

import io.airlift.http.client.AbstractHttpsClientTest;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.HttpVersion;

public class TestJettyHttps2ClientHttps2Server
        extends AbstractHttpsClientTest
{
    @Override
    protected HttpClientConfig createClientConfig()
    {
        return super.createClientConfig()
                .setHttp2Enabled(true);
    }

    @Override
    public HttpVersion expectedProtocolVersion()
    {
        return HttpVersion.HTTP_2;
    }
}
