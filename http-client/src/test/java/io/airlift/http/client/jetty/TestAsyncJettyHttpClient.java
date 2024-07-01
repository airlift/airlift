package io.airlift.http.client.jetty;

import io.airlift.http.client.AbstractHttpClientTest;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.ResponseHandler;
import org.junit.jupiter.api.Test;

import static io.airlift.http.client.InputStreamResponseHandler.inputStreamResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareGet;
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

    @SuppressWarnings("unchecked")
    @Override
    public <T, E extends Exception> T executeRequest(CloseableTestHttpServer server, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        return executeOnServer(server, createClientConfig(), client -> executeAsync((JettyHttpClient) client, request, responseHandler));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, E extends Exception> T executeRequest(CloseableTestHttpServer server, HttpClientConfig config, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        return executeOnServer(server, config, client -> executeAsync((JettyHttpClient) client, request, responseHandler));
    }

    @Override
    public <T, E extends Exception> T executeOnServer(CloseableTestHttpServer server, HttpClientConfig config, ThrowingFunction<T, E> consumer)
            throws Exception
    {
        try (JettyHttpClient client = server.createClient(config)) {
            return consumer.run(client);
        }
    }

    protected void testPutMethodWithStreamingBodyGenerator(boolean largeContent)
            throws Exception
    {
        // don't test with async clients as they buffer responses and the LARGE content is too big
        if (!largeContent) {
            super.testPutMethodWithStreamingBodyGenerator(false);
        }
    }

    @Override
    @Test
    public void testStreamingResponseHandler()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            Request request = prepareGet().setUri(server.baseURI()).build();
            executeOnServer(server, createClientConfig(), client ->
                    assertThatThrownBy(() -> client.executeAsync(request, inputStreamResponseHandler()))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessage("InputStreamResponseHandler cannot be used with executeAsync()"));
        }
    }
}
