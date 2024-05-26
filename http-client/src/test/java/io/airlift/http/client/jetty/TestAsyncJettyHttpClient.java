package io.airlift.http.client.jetty;

import io.airlift.http.client.AbstractHttpClientTest;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.ResponseHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestAsyncJettyHttpClient
        extends AbstractHttpClientTest
{
    @Override
    protected HttpClientConfig createClientConfig()
    {
        return new HttpClientConfig()
                .setHttp2Enabled(false);
    }

    @Override
    public void testConnectTimeout()
            throws Exception
    {
        // Async client doesn't work properly with timeouts as this is expected for the caller to put timeout on the Future.get
        doTestConnectTimeout(true);
    }

    @Override
    public <T, E extends Exception> T executeRequest(CloseableTestHttpServer server, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        return executeRequest(server, createClientConfig(), request, responseHandler);
    }

    @Override
    public <T, E extends Exception> T executeRequest(CloseableTestHttpServer server, HttpClientConfig config, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        JettyHttpClient client = server.createClient(config);
        return executeAsync(client, request, responseHandler);
    }

    @Test
    public void testStreamingResponseHandler()
    {
        assertThatThrownBy(super::testStreamingResponseHandler).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    protected void testPutMethodWithStreamingBodyGenerator(boolean largeContent)
            throws Exception
    {
        // don't test with async clients as they buffer responses and the LARGE content is too big
        if (!largeContent) {
            super.testPutMethodWithStreamingBodyGenerator(false);
        }
    }
}
