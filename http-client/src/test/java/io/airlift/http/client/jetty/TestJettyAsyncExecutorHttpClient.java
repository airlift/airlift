package io.airlift.http.client.jetty;

import io.airlift.http.client.AbstractHttpClientTest;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.StreamingResponse;

import java.util.Optional;
import java.util.concurrent.Executors;

import static io.airlift.concurrent.Threads.virtualThreadsNamed;

public class TestJettyAsyncExecutorHttpClient
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
    {
        return Optional.empty();
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
        try (JettyHttpClient client = server.createClient(config)) {
            return executeAsync(Executors.newThreadPerTaskExecutor(virtualThreadsNamed("async-executor-pool#v")), client, request, responseHandler);
        }
    }
}
