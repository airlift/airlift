package io.airlift.http.client.jetty;

import io.airlift.http.client.AbstractHttpClientTest;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.StreamingResponse;
import java.util.Optional;

public class TestJettyHttpClient extends AbstractHttpClientTest {
    @Override
    protected HttpClientConfig createClientConfig() {
        return new HttpClientConfig().setHttp2Enabled(false);
    }

    @Override
    public Optional<StreamingResponse> executeRequest(CloseableTestHttpServer server, Request request) {
        JettyHttpClient client = server.createClient(createClientConfig());
        return Optional.of(new TestingStreamingResponse(() -> client.executeStreaming(request), client));
    }

    @Override
    public <T, E extends Exception> T executeRequest(
            CloseableTestHttpServer server, Request request, ResponseHandler<T, E> responseHandler) throws Exception {
        return executeRequest(server, createClientConfig(), request, responseHandler);
    }

    @Override
    public <T, E extends Exception> T executeRequest(
            CloseableTestHttpServer server,
            HttpClientConfig config,
            Request request,
            ResponseHandler<T, E> responseHandler)
            throws Exception {
        try (JettyHttpClient client = server.createClient(config)) {
            return client.execute(request, responseHandler);
        }
    }
}
