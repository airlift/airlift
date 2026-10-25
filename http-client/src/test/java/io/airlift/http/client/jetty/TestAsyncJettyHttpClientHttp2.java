package io.airlift.http.client.jetty;

import io.airlift.http.client.HttpClient.HttpResponseFuture;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.ResponseMetadata;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.StreamingResponseHandler;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.common.util.concurrent.Uninterruptibles.awaitUninterruptibly;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.ResponseHandlerUtils.propagate;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
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

    @Test
    @Timeout(30)
    public void testBlockedResponseHandlerDoesNotStallHttp2Connection()
            throws Exception
    {
        // A blocked response handler does not stop other responses on the shared HTTP/2 connection from arriving
        HttpClientConfig config = createClientConfig().setMaxConnectionsPerServer(1);
        HeldResponseServlet servlet = new HeldResponseServlet();
        try (CloseableTestHttpServer server = newServerWithServlet(servlet);
                JettyHttpClient client = server.createClient(config)) {
            Request heldRequest = prepareGet()
                    .setUri(server.baseURI().resolve("/held"))
                    .build();
            HttpResponseFuture<StatusResponse> heldFuture = client.executeAsync(heldRequest, createStatusResponseHandler());
            CountDownLatch heldResponseReceived = new CountDownLatch(1);
            heldFuture.addListener(heldResponseReceived::countDown, directExecutor());
            servlet.awaitHeldRequest();

            Request request = prepareGet()
                    .setUri(server.baseURI())
                    .build();
            HttpResponseFuture<Boolean> blockingFuture = client.executeAsync(request, new ResponseHandler<Boolean, RuntimeException>()
            {
                @Override
                public Boolean handleException(Request request, Exception exception)
                {
                    throw propagate(request, exception);
                }

                @Override
                public Boolean handle(Request request, Response response)
                {
                    servlet.releaseHeldResponse();
                    return awaitUninterruptibly(heldResponseReceived, 10, SECONDS);
                }
            });

            assertThat(blockingFuture.get()).isTrue();
            assertThat(heldFuture.get().getStatusCode()).isEqualTo(200);
        }
    }

    @Test
    @Timeout(30)
    public void testBlockedBodyConsumerDoesNotStallHttp2Connection()
            throws Exception
    {
        // A body consumer blocked mid-body does not stop other responses on the shared HTTP/2 connection from arriving
        HttpClientConfig config = createClientConfig().setMaxConnectionsPerServer(1);
        HeldResponseServlet servlet = new HeldResponseServlet();
        try (CloseableTestHttpServer server = newServerWithServlet(servlet);
                JettyHttpClient client = server.createClient(config)) {
            Request heldRequest = prepareGet()
                    .setUri(server.baseURI().resolve("/held"))
                    .build();
            HttpResponseFuture<StatusResponse> heldFuture = client.executeAsync(heldRequest, createStatusResponseHandler());
            CountDownLatch heldResponseReceived = new CountDownLatch(1);
            heldFuture.addListener(heldResponseReceived::countDown, directExecutor());
            servlet.awaitHeldRequest();

            Request request = prepareGet()
                    .setUri(server.baseURI())
                    .build();
            AtomicBoolean unblocked = new AtomicBoolean();
            HttpResponseFuture<String> blockingFuture = client.executeAsync(request, new StreamingResponseHandler<String, RuntimeException>()
            {
                @Override
                public String handleException(Request request, Exception exception)
                {
                    throw propagate(request, exception);
                }

                @Override
                public BodyConsumer<String, RuntimeException> handleHeaders(Request request, ResponseMetadata response)
                {
                    StringBuilder body = new StringBuilder();
                    return new BodyConsumer<>()
                    {
                        @Override
                        public void accept(ByteBuffer content)
                        {
                            body.append(US_ASCII.decode(content));
                            if (body.toString().equals("first")) {
                                servlet.releaseHeldResponse();
                                unblocked.set(awaitUninterruptibly(heldResponseReceived, 10, SECONDS));
                            }
                        }

                        @Override
                        public String complete()
                        {
                            return body.toString();
                        }
                    };
                }
            });

            assertThat(blockingFuture.get()).isEqualTo("firstlast");
            assertThat(unblocked.get()).isTrue();
            assertThat(heldFuture.get().getStatusCode()).isEqualTo(200);
        }
    }

    private static final class HeldResponseServlet
            extends HttpServlet
    {
        private final CountDownLatch heldRequestReceived = new CountDownLatch(1);
        private final CountDownLatch heldResponseReleased = new CountDownLatch(1);

        public void awaitHeldRequest()
        {
            assertThat(awaitUninterruptibly(heldRequestReceived, 10, SECONDS)).isTrue();
        }

        public void releaseHeldResponse()
        {
            heldResponseReleased.countDown();
        }

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response)
                throws IOException
        {
            if (request.getRequestURI().equals("/held")) {
                heldRequestReceived.countDown();
                awaitUninterruptibly(heldResponseReleased, 10, SECONDS);
                response.setStatus(200);
                return;
            }
            // The last chunk arrives after the client has demanded more content
            response.setStatus(200);
            response.getOutputStream().print("first");
            response.flushBuffer();
            sleepUninterruptibly(200, MILLISECONDS);
            response.getOutputStream().print("last");
        }
    }
}
