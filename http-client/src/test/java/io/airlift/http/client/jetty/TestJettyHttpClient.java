package io.airlift.http.client.jetty;

import io.airlift.http.client.AbstractHttpClientTest;
import io.airlift.http.client.HttpClientConfig;

public class TestJettyHttpClient
        extends AbstractHttpClientTest
{
    @Override
    protected HttpClientConfig createClientConfig()
    {
        return new HttpClientConfig()
                .setHttp2Enabled(false);
    }

    @Override
    public <T, E extends Exception> T executeOnServer(CloseableTestHttpServer server, HttpClientConfig config, ThrowingFunction<T, E> consumer)
            throws Exception
    {
        try (JettyHttpClient client = server.createClient(config)) {
            return consumer.run(client);
        }
    }
}
