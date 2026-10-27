package io.airlift.http.client.jetty;

import io.airlift.http.client.AbstractHttpClientTest;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.StreamingResponse;
import io.airlift.units.DataSize;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.airlift.http.client.jetty.JettyHttpClient.maxPooledBufferSize;
import static io.airlift.units.DataSize.Unit.KILOBYTE;
import static org.assertj.core.api.Assertions.assertThat;

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
    public Optional<StreamingResponse> executeStreamingRequest(CloseableTestHttpServer server, Request request)
    {
        JettyHttpClient client = server.createClient(createClientConfig());
        return Optional.of(new TestingStreamingResponse(() -> client.executeStreaming(request), client));
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
            return client.execute(request, responseHandler);
        }
    }

    @Test
    public void testMaxPooledBufferSize()
    {
        assertThat(maxPooledBufferSize(new HttpClientConfig())).isEqualTo(64 * 1024);
        assertThat(maxPooledBufferSize(new HttpClientConfig().setRequestBufferSize(DataSize.of(128, KILOBYTE)))).isEqualTo(128 * 1024);
        assertThat(maxPooledBufferSize(new HttpClientConfig().setResponseBufferSize(DataSize.of(256, KILOBYTE)))).isEqualTo(256 * 1024);
        assertThat(maxPooledBufferSize(new HttpClientConfig().setMaxRequestHeaderSize(DataSize.of(96, KILOBYTE)))).isEqualTo(96 * 1024);
        assertThat(maxPooledBufferSize(new HttpClientConfig().setHttp2InputBufferSize(DataSize.of(512, KILOBYTE)))).isEqualTo(512 * 1024);
    }
}
