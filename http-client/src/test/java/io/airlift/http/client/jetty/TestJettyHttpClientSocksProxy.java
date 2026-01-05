package io.airlift.http.client.jetty;

import io.airlift.http.client.AbstractHttpClientTest;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.StreamingResponse;
import io.airlift.http.client.TestingSocksProxy;

import java.util.Optional;

public class TestJettyHttpClientSocksProxy
        extends AbstractHttpClientTest
{
    @Override
    protected HttpClientConfig createClientConfig()
    {
        return new HttpClientConfig()
                .setHttp2Enabled(false);
    }

    @Override
    public Optional<StreamingResponse> executeStreamingRequest(CloseableTestHttpServer server, Request request)
            throws Exception
    {
        try (TestingSocksProxy testingSocksProxy = new TestingSocksProxy().start()) {
            JettyHttpClient client = server.createClient(createClientConfig().setSocksProxy(testingSocksProxy.getHostAndPort()));
            return Optional.of(new TestingStreamingResponse(() -> client.executeStreaming(request), testingSocksProxy, client));
        }
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
        try (TestingSocksProxy testingSocksProxy = new TestingSocksProxy().start(); JettyHttpClient client = server.createClient(config.setSocksProxy(testingSocksProxy.getHostAndPort()))) {
            return client.execute(request, responseHandler);
        }
    }
}
