package io.airlift.http.client.jetty;

import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.HttpVersion;

import static com.google.common.io.Resources.getResource;

public class TestJettyHttps2Client
        extends AbstractHttpsClientTest
{
    @Override
    protected HttpClientConfig createClientConfig()
    {
        return new HttpClientConfig()
                .setHttp2Enabled(true) // Enable HTTP/2 protocol
                .setKeyStorePath(getResource("localhost.keystore").getPath())
                .setKeyStorePassword("changeit")
                .setTrustStorePath(getResource("localhost.truststore").getPath())
                .setTrustStorePassword("changeit");
    }

    @Override
    HttpVersion testedHttpVersion()
    {
        return HttpVersion.HTTP_2;
    }

    @Override
    public void testConnectReadRequestClose()
            throws Exception
    {
        // TODO: enable once the timeouts are handled correctly
    }
}
