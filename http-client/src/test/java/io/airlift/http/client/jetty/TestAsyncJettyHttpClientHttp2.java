package io.airlift.http.client.jetty;

import io.airlift.http.client.HttpClient.HttpResponseFuture;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static org.assertj.core.api.Assertions.assertThat;

public class TestAsyncJettyHttpClientHttp2
        extends TestAsyncJettyHttpClient
{
    @Override
    protected HttpClientConfig createClientConfig()
    {
        return super.createClientConfig()
                .setHttp2Enabled(true);
    }

    @Test
    @Timeout(30)
    public void testConcurrentHttp2StreamsUnaffectedByEarlyResponse()
            throws Exception
    {
        // Pin the destination to a single shared HTTP/2 connection, then issue a
        // regular request concurrently with one that the server rejects after an
        // early response. The streams must complete independently: rejection
        // delivers 503 while the regular request still gets 200.
        HttpClientConfig config = createClientConfig().setMaxConnectionsPerServer(1);
        try (CloseableTestHttpServer server = newServerWithServlet(new EarlyResponseServlet());
                JettyHttpClient client = server.createClient(config)) {
            Request rejected = rejectingUploadRequest(server);
            Request regular = prepareGet()
                    .setUri(server.baseURI())
                    .build();

            HttpResponseFuture<StatusResponse> rejectedFuture = client.executeAsync(rejected, createStatusResponseHandler());
            HttpResponseFuture<StatusResponse> regularFuture = client.executeAsync(regular, createStatusResponseHandler());

            assertThat(rejectedFuture.get().getStatusCode()).isEqualTo(503);
            assertThat(regularFuture.get().getStatusCode()).isEqualTo(200);
        }
    }
}
