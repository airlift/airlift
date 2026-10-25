package io.airlift.http.client.jetty;

import com.google.common.collect.ImmutableList;
import io.airlift.http.client.AbstractHttpClientTest;
import io.airlift.http.client.HttpClient.HttpResponseFuture;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.ResponseMetadata;
import io.airlift.http.client.StreamingResponse;
import io.airlift.http.client.StreamingResponseHandler;
import io.airlift.units.DataSize;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.ResponseHandlerUtils.propagate;
import static io.airlift.units.DataSize.Unit.BYTE;
import static java.nio.charset.StandardCharsets.US_ASCII;
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
                        .isInstanceOf(IllegalArgumentException.class)
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
