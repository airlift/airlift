package io.airlift.http.client.jetty;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import io.airlift.http.client.AbstractHttpClientTest;
import io.airlift.http.client.AsyncBodyGenerator;
import io.airlift.http.client.HttpClient.HttpResponseFuture;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.HttpRequestFilter;
import io.airlift.http.client.Request;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.ResponseMetadata;
import io.airlift.http.client.ResponseTooLargeException;
import io.airlift.http.client.StreamingResponse;
import io.airlift.http.client.StreamingResponseHandler;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.units.DataSize;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.airlift.http.client.AsyncBodyGenerator.asyncBodyGenerator;
import static io.airlift.http.client.Request.Builder.fromRequest;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.ResponseHandlerUtils.propagate;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.units.DataSize.Unit.BYTE;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
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
            return executeAsync(client, request, responseHandler);
        }
    }

    @Test
    public void testStreamingResponseHandler()
            throws Exception
    {
        byte[] body = "0123456789abcdef".repeat(20 * 1024).getBytes(US_ASCII);
        for (boolean declareLength : new boolean[] {false, true}) {
            try (CloseableTestHttpServer server = newServerWithServlet(new PiecewiseResponseServlet(body, declareLength));
                    JettyHttpClient client = server.createClient(createClientConfig())) {
                Request request = prepareGet()
                        .setUri(server.baseURI())
                        .setMaxResponseContentLength(DataSize.of(body.length, BYTE))
                        .build();
                CollectingResponseHandler handler = new CollectingResponseHandler();
                assertThat(client.executeAsync(request, handler).get()).isEqualTo(body);
                assertThat(handler.statusCode.get()).isEqualTo(200);
                assertThat(handler.chunks.get()).isGreaterThan(1);
            }
        }
    }

    @Test
    @Timeout(30)
    public void testAsyncBodyGeneratorFailsWithCancelledRequest()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServerWithServlet(new IncrementalUploadServlet());
                JettyHttpClient client = server.createClient(createClientConfig())) {
            AsyncBodyGenerator body = asyncBodyGenerator();
            Request request = preparePost()
                    .setUri(server.baseURI())
                    .setBodyGenerator(body)
                    .build();
            HttpResponseFuture<StringResponse> response = client.executeAsync(request, createStringResponseHandler());
            body.write(ByteBuffer.wrap("partial".getBytes(US_ASCII))).get(10, SECONDS);

            response.cancel(true);

            // The request is aborted asynchronously, so content may still be accepted for a moment,
            // but it must not be accepted for good
            long deadline = System.nanoTime() + SECONDS.toNanos(10);
            Throwable failure = null;
            while (failure == null && System.nanoTime() < deadline) {
                try {
                    body.write(ByteBuffer.wrap("late".getBytes(US_ASCII))).get(10, SECONDS);
                }
                catch (ExecutionException e) {
                    failure = e.getCause();
                }
            }
            assertThat(failure).isNotNull();
        }
    }

    @Test
    @Timeout(30)
    public void testAsyncBodyGeneratorFailsWhenRequestIsRejected()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            JettyHttpClient client = server.createClient(createClientConfig());
            client.close();

            for (boolean closeBody : new boolean[] {false, true}) {
                AsyncBodyGenerator body = asyncBodyGenerator();
                ListenableFuture<Void> write = body.write(ByteBuffer.wrap("never sent".getBytes(US_ASCII)));
                if (closeBody) {
                    body.close();
                }
                Request request = preparePost()
                        .setUri(server.baseURI())
                        .setBodyGenerator(body)
                        .build();
                HttpResponseFuture<StringResponse> response = client.executeAsync(request, createStringResponseHandler());

                assertThatThrownBy(() -> response.get(10, SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(RejectedExecutionException.class);
                // the content written before the request fails with it, whether the body is still open or not
                assertThatThrownBy(() -> write.get(10, SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(RejectedExecutionException.class);
            }
        }
    }

    @Test
    @Timeout(30)
    public void testAsyncBodyGeneratorIsLeftToCallerWhenRequestIsRejected()
            throws Exception
    {
        RuntimeException failure = new IllegalStateException("filter failed");
        HttpRequestFilter failingFilter = _ -> {
            throw failure;
        };
        try (CloseableTestHttpServer server = newServer();
                JettyHttpClient client = new JettyHttpClient("test", createClientConfig(), ImmutableList.of(failingFilter), ImmutableSet.of())) {
            AsyncBodyGenerator asyncBody = asyncBodyGenerator();
            ListenableFuture<Void> asyncWrite = asyncBody.write(ByteBuffer.wrap("never sent".getBytes(US_ASCII)));
            Request asyncRequest = preparePost()
                    .setUri(server.baseURI())
                    .setBodyGenerator(asyncBody)
                    .build();
            assertThatThrownBy(() -> client.executeAsync(asyncRequest, createStringResponseHandler()).get(10, SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCause(failure);

            // the blocking call rejects the request the same way
            AsyncBodyGenerator body = asyncBodyGenerator();
            ListenableFuture<Void> write = body.write(ByteBuffer.wrap("never sent".getBytes(US_ASCII)));
            Request request = preparePost()
                    .setUri(server.baseURI())
                    .setBodyGenerator(body)
                    .build();
            assertThatThrownBy(() -> client.execute(request, createStringResponseHandler()))
                    .isSameAs(failure);

            // a request that was never sent leaves its body to the caller
            assertThat(asyncWrite).isNotDone();
            assertThat(write).isNotDone();
            asyncBody.fail(failure);
            body.fail(failure);
            assertThatThrownBy(() -> asyncWrite.get(10, SECONDS)).hasCause(failure);
            assertThatThrownBy(() -> write.get(10, SECONDS)).hasCause(failure);
        }
    }

    @Test
    @Timeout(30)
    public void testAsyncBodyGeneratorFailsRequestAfterBodyWasSent()
            throws Exception
    {
        HoldingUploadServlet servlet = new HoldingUploadServlet();
        try (CloseableTestHttpServer server = newServerWithServlet(servlet);
                JettyHttpClient client = server.createClient(createClientConfig())) {
            AsyncBodyGenerator body = asyncBodyGenerator();
            body.write(ByteBuffer.wrap("complete".getBytes(US_ASCII)));
            body.close();
            Request request = preparePost()
                    .setUri(server.baseURI())
                    .setBodyGenerator(body)
                    .build();
            HttpResponseFuture<StringResponse> response = client.executeAsync(request, createStringResponseHandler());

            // the server has all of the body, and the request is waiting for the response
            assertThat(servlet.bodyReceived.await(10, SECONDS)).isTrue();
            assertThat(response).isNotDone();

            body.fail(new IOException("abandoned"));
            assertThatThrownBy(() -> response.get(10, SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasStackTraceContaining("abandoned");
        }
        finally {
            servlet.respond.countDown();
        }
    }

    @Test
    @Timeout(30)
    public void testAsyncBodyGeneratorWrappedByRequestFilter()
            throws Exception
    {
        // the filter takes the original body over, and sends its content in a body of its own
        HttpRequestFilter wrappingFilter = request -> {
            AsyncBodyGenerator wrapper = asyncBodyGenerator();
            ((AsyncBodyGenerator) request.getBodyGenerator()).attach(new AsyncBodyGenerator.Sink()
            {
                @Override
                public ListenableFuture<Void> write(ByteBuffer content)
                {
                    wrapper.write(ByteBuffer.wrap("wrapped ".getBytes(US_ASCII)));
                    return wrapper.write(content);
                }

                @Override
                public void close()
                {
                    wrapper.close();
                }

                @Override
                public void fail(Throwable failure)
                {
                    wrapper.fail(failure);
                }
            });
            return fromRequest(request)
                    .setBodyGenerator(wrapper)
                    .build();
        };
        try (CloseableTestHttpServer server = newServer();
                JettyHttpClient client = new JettyHttpClient("test", createClientConfig(), ImmutableList.of(wrappingFilter), ImmutableSet.of())) {
            AsyncBodyGenerator body = asyncBodyGenerator();
            ListenableFuture<Void> write = body.write(ByteBuffer.wrap("original".getBytes(US_ASCII)));
            body.close();
            Request request = preparePost()
                    .setUri(server.baseURI())
                    .setBodyGenerator(body)
                    .build();

            assertThat(client.executeAsync(request, createStringResponseHandler()).get(10, SECONDS).getStatusCode()).isEqualTo(200);
            assertThat(server.servlet().getRequestBytes()).isEqualTo("wrapped original".getBytes(US_ASCII));
            write.get(10, SECONDS);
        }
    }

    @Test
    public void testStreamingResponseHandlerFailure()
            throws Exception
    {
        byte[] body = "0123456789abcdef".repeat(20 * 1024).getBytes(US_ASCII);
        try (CloseableTestHttpServer server = newServerWithServlet(new PiecewiseResponseServlet(body, false));
                JettyHttpClient client = server.createClient(createClientConfig())) {
            Request request = prepareGet()
                    .setUri(server.baseURI())
                    .build();
            for (Stage stage : ImmutableList.of(Stage.HEADERS, Stage.CONTENT, Stage.COMPLETE)) {
                FailingResponseHandler handler = new FailingResponseHandler(stage);
                HttpResponseFuture<byte[]> future = client.executeAsync(request, handler);
                assertThatThrownBy(future::get)
                        .isInstanceOf(ExecutionException.class)
                        .cause()
                        .isSameAs(handler.failure);
                assertThat(handler.handledException.get()).isNull();
            }
        }
    }

    @Test
    public void testStreamingResponseHandlerExceedsLimit()
            throws Exception
    {
        byte[] body = "0123456789abcdef".repeat(20 * 1024).getBytes(US_ASCII);
        for (boolean declareLength : new boolean[] {false, true}) {
            try (CloseableTestHttpServer server = newServerWithServlet(new PiecewiseResponseServlet(body, declareLength));
                    JettyHttpClient client = server.createClient(createClientConfig())) {
                int maxLength = body.length - 1;
                Request request = prepareGet()
                        .setUri(server.baseURI())
                        .setMaxResponseContentLength(DataSize.of(maxLength, BYTE))
                        .build();
                FailingResponseHandler handler = new FailingResponseHandler(Stage.NONE);
                assertThat(client.executeAsync(request, handler).get()).isNull();
                assertThat(handler.handledException.get())
                        .isInstanceOf(ResponseTooLargeException.class)
                        .hasMessage("Buffering capacity " + maxLength + " exceeded");
            }
        }
    }

    private static final class CollectingResponseHandler
            implements StreamingResponseHandler<byte[], RuntimeException>
    {
        private final AtomicInteger statusCode = new AtomicInteger();
        private final AtomicInteger chunks = new AtomicInteger();

        @Override
        public byte[] handleException(Request request, Exception exception)
        {
            throw propagate(request, exception);
        }

        @Override
        public BodyConsumer<byte[], RuntimeException> handleHeaders(Request request, ResponseMetadata response)
        {
            statusCode.set(response.getStatusCode());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            return new BodyConsumer<>()
            {
                @Override
                public void accept(ByteBuffer content)
                {
                    chunks.incrementAndGet();
                    byte[] bytes = new byte[content.remaining()];
                    content.get(bytes);
                    output.writeBytes(bytes);
                }

                @Override
                public byte[] complete()
                {
                    return output.toByteArray();
                }
            };
        }
    }

    /**
     * Reads the whole request body, and responds only when the test allows it.
     */
    private static final class HoldingUploadServlet
            extends HttpServlet
    {
        private final CountDownLatch bodyReceived = new CountDownLatch(1);
        private final CountDownLatch respond = new CountDownLatch(1);

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response)
                throws IOException
        {
            request.getInputStream().readAllBytes();
            bodyReceived.countDown();
            try {
                respond.await(20, SECONDS);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            response.setStatus(200);
        }
    }

    private enum Stage
    {
        NONE, HEADERS, CONTENT, COMPLETE
    }

    private static final class FailingResponseHandler
            implements StreamingResponseHandler<byte[], RuntimeException>
    {
        private final Stage stage;
        private final RuntimeException failure = new RuntimeException("handler failure");
        private final AtomicReference<Exception> handledException = new AtomicReference<>();

        private FailingResponseHandler(Stage stage)
        {
            this.stage = stage;
        }

        @Override
        public byte[] handleException(Request request, Exception exception)
        {
            handledException.set(exception);
            return null;
        }

        @Override
        public BodyConsumer<byte[], RuntimeException> handleHeaders(Request request, ResponseMetadata response)
        {
            failAt(Stage.HEADERS);
            return new BodyConsumer<>()
            {
                @Override
                public void accept(ByteBuffer content)
                {
                    failAt(Stage.CONTENT);
                    content.position(content.limit());
                }

                @Override
                public byte[] complete()
                {
                    failAt(Stage.COMPLETE);
                    return new byte[0];
                }
            };
        }

        private void failAt(Stage current)
        {
            if (stage == current) {
                throw failure;
            }
        }
    }
}
