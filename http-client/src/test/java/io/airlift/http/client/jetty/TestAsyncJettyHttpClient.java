package io.airlift.http.client.jetty;

import io.airlift.http.client.AbstractHttpClientTest;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.StreamingResponse;
import java.util.Optional;

public class TestAsyncJettyHttpClient extends AbstractHttpClientTest {
    @Override
    protected HttpClientConfig createClientConfig() {
        return new HttpClientConfig().setHttp2Enabled(false);
    }

    @Override
    public Optional<StreamingResponse> executeRequest(CloseableTestHttpServer server, Request request) {
        return Optional.empty();
    }

    @Override
    public void testConnectTimeout() throws Exception {
        // Async client doesn't work properly with timeouts as this is expected for the caller to put timeout on the
        // Future.get
        doTestConnectTimeout(true);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, E extends Exception> T executeRequest(
            CloseableTestHttpServer server, Request request, ResponseHandler<T, E> responseHandler) throws Exception {
        return executeRequest(server, createClientConfig(), request, responseHandler);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, E extends Exception> T executeRequest(
            CloseableTestHttpServer server,
            HttpClientConfig config,
            Request request,
            ResponseHandler<T, E> responseHandler)
            throws Exception {
        try (JettyHttpClient client = server.createClient(config)) {
            return executeAsync(client, request, responseHandler);
        }
    }

    protected void testPutMethodWithStreamingBodyGenerator(boolean largeContent) throws Exception {
        // don't test with async clients as they buffer responses and the LARGE content is too big
        if (!largeContent) {
            super.testPiped(largeContent);
        }
    }
}
