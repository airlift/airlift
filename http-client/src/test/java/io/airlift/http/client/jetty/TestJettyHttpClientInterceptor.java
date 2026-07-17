/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.http.client.jetty;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import io.airlift.http.client.EchoServlet;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.HttpClientInterceptor;
import io.airlift.http.client.HttpRequestFilter;
import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.HttpVersion;
import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.StreamingResponse;
import io.airlift.http.client.TestingHttpServer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static io.airlift.http.client.Request.Builder.fromRequest;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.opentelemetry.api.OpenTelemetry.propagating;
import static io.opentelemetry.api.trace.StatusCode.ERROR;
import static io.opentelemetry.api.trace.StatusCode.UNSET;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestJettyHttpClientInterceptor
{
    private static final HeaderName FILTER_HEADER = HeaderName.of("x-test-filter");
    private static final HeaderName INTERCEPTOR_HEADER = HeaderName.of("x-test-interceptor");
    private static final HeaderName REQUEST_ID_HEADER = HeaderName.of("x-test-request-id");
    private static final HeaderName TRACE_HEADER = HeaderName.of("x-test-trace");

    @Test
    void testRequestRewriteReachesTransport()
            throws Exception
    {
        EchoServlet servlet = new EchoServlet();
        try (TestingHttpServer server = new TestingHttpServer(Optional.empty(), servlet);
                JettyHttpClient client = new JettyHttpClient(new HttpClientConfig(), List.of(chain -> {
                    Request rewritten = fromRequest(chain.request())
                            .setUri(server.baseURI().resolve("/rewritten?value=expected"))
                            .setHeader(INTERCEPTOR_HEADER, "present")
                            .build();
                    return chain.proceed(rewritten);
                }))) {
            client.execute(prepareGet().setUri(server.baseURI()).build(), createStatusResponseHandler());

            assertThat(servlet.getRequestUri().getPath()).isEqualTo("/rewritten");
            assertThat(servlet.getRequestUri().getQuery()).isEqualTo("value=expected");
            assertThat(servlet.getRequestHeaders(INTERCEPTOR_HEADER)).containsExactly("present");
        }
    }

    @Test
    void testLegacyFiltersAndTracingRunBeforeUserInterceptors()
    {
        AtomicReference<Request> interceptedRequest = new AtomicReference<>();
        HttpRequestFilter filter = request -> fromRequest(request)
                .setHeader(FILTER_HEADER, "filtered")
                .build();
        HttpClientInterceptor interceptor = chain -> {
            interceptedRequest.set(chain.request());
            return new StaticStreamingResponse(HttpStatus.OK, "short-circuit");
        };

        try (JettyHttpClient client = new JettyHttpClient(
                "interceptor-order-test",
                new HttpClientConfig(),
                List.of(filter),
                propagating(ContextPropagators.create(new TestingPropagator())),
                TracerProvider.noop().get("testing"),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(interceptor))) {
            client.execute(prepareGet().setUri(URI.create("http://unused.invalid")).build(), createStringResponseHandler());
        }

        assertThat(interceptedRequest.get().getHeaders().get(FILTER_HEADER)).containsExactly("filtered");
        assertThat(interceptedRequest.get().getHeaders().get(TRACE_HEADER)).containsExactly("injected");
    }

    @Test
    void testFirstRegisteredInterceptorIsOutermost()
    {
        List<String> events = new CopyOnWriteArrayList<>();
        List<HttpClientInterceptor> interceptors = List.of(
                orderedInterceptor("first", events, false),
                orderedInterceptor("second", events, false),
                orderedInterceptor("third", events, true));

        try (JettyHttpClient client = new JettyHttpClient(new HttpClientConfig(), interceptors)) {
            client.execute(prepareGet().setUri(URI.create("http://unused.invalid")).build(), createStringResponseHandler());
        }

        assertThat(events).containsExactly(
                "first-before",
                "second-before",
                "third-before",
                "third-after",
                "second-after",
                "first-after");
    }

    @Test
    void testResponseWrappingIsVisibleToSynchronousHandler()
    {
        HttpClientInterceptor wrappingInterceptor = chain -> new ForwardingStreamingResponse(chain.proceed(chain.request()))
        {
            @Override
            public int getStatusCode()
            {
                return HttpStatus.CREATED.code();
            }
        };
        HttpClientInterceptor shortCircuit = _ -> new StaticStreamingResponse(HttpStatus.OK, "body");

        try (JettyHttpClient client = new JettyHttpClient(new HttpClientConfig(), List.of(wrappingInterceptor, shortCircuit))) {
            assertThat(client.execute(prepareGet().setUri(URI.create("http://unused.invalid")).build(), createStatusResponseHandler()).getStatusCode())
                    .isEqualTo(HttpStatus.CREATED.code());
        }
    }

    @Test
    void testShortCircuitIsVisibleToStreamingCaller()
            throws IOException
    {
        HttpClientInterceptor shortCircuit = _ -> new StaticStreamingResponse(HttpStatus.ACCEPTED, "short-circuit");

        try (JettyHttpClient client = new JettyHttpClient(new HttpClientConfig(), List.of(shortCircuit));
                StreamingResponse response = client.executeStreaming(prepareGet().setUri(URI.create("http://unused.invalid")).build())) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED.code());
            assertThat(new String(response.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("short-circuit");
        }
    }

    @Test
    void testTransportFailureIsObservedByInterceptorAndHandledByResponseHandler()
            throws Exception
    {
        AtomicReference<RuntimeException> observedFailure = new AtomicReference<>();
        AtomicReference<Exception> handledFailure = new AtomicReference<>();
        HttpClientInterceptor interceptor = chain -> {
            try {
                return chain.proceed(chain.request());
            }
            catch (RuntimeException e) {
                observedFailure.set(e);
                throw e;
            }
        };

        try (ResettingServer server = new ResettingServer();
                JettyHttpClient client = new JettyHttpClient(new HttpClientConfig(), List.of(interceptor))) {
            String result = client.execute(prepareGet().setUri(server.uri()).build(), new ResponseHandler<>()
            {
                @Override
                public String handleException(Request request, Exception exception)
                {
                    handledFailure.set(exception);
                    return "handled";
                }

                @Override
                public String handle(Request request, Response response)
                {
                    throw new AssertionError("transport failure unexpectedly produced a response");
                }
            });

            assertThat(result).isEqualTo("handled");
            assertThat(observedFailure.get()).hasCause(handledFailure.get());
        }
    }

    @Test
    void testInterceptorFailureBypassesResponseHandler()
    {
        IllegalStateException failure = new IllegalStateException("interceptor failed");
        AtomicBoolean exceptionHandlerCalled = new AtomicBoolean();
        HttpClientInterceptor interceptor = _ -> {
            throw failure;
        };

        try (JettyHttpClient client = new JettyHttpClient(new HttpClientConfig(), List.of(interceptor))) {
            assertThatThrownBy(() -> client.execute(prepareGet().setUri(URI.create("http://unused.invalid")).build(), new ResponseHandler<>()
            {
                @Override
                public Object handleException(Request request, Exception exception)
                {
                    exceptionHandlerCalled.set(true);
                    return null;
                }

                @Override
                public Object handle(Request request, Response response)
                {
                    throw new AssertionError("interceptor failure unexpectedly produced a response");
                }
            }))
                    .isSameAs(failure);
        }

        assertThat(exceptionHandlerCalled).isFalse();
    }

    @Test
    void testSynchronousExecutionClosesResponseExactlyOnce()
    {
        AtomicInteger closeCount = new AtomicInteger();
        HttpClientInterceptor shortCircuit = _ -> new StaticStreamingResponse(HttpStatus.OK, "body", closeCount);

        try (JettyHttpClient client = new JettyHttpClient(new HttpClientConfig(), List.of(shortCircuit))) {
            client.execute(prepareGet().setUri(URI.create("http://unused.invalid")).build(), createStringResponseHandler());
        }

        assertThat(closeCount).hasValue(1);
    }

    @Test
    void testSynchronousHandlerFailureIsRecordedBeforeSpanEnds()
    {
        RuntimeException failure = new RuntimeException("handler failed");
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        try (SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
                JettyHttpClient client = tracingClient(tracerProvider, _ -> new StaticStreamingResponse(HttpStatus.OK, "body"))) {
            assertThatThrownBy(() -> client.execute(
                    prepareGet().setUri(URI.create("http://unused.invalid")).build(),
                    new ResponseHandler<>()
                    {
                        @Override
                        public Object handleException(Request request, Exception exception)
                        {
                            throw new AssertionError("short circuit unexpectedly produced a transport failure");
                        }

                        @Override
                        public Object handle(Request request, Response response)
                        {
                            throw failure;
                        }
                    }))
                    .isSameAs(failure);
            assertSpanFailure(exporter, failure);
        }
    }

    @Test
    void testSynchronousCloseFailureIsRecordedBeforeSpanEnds()
    {
        RuntimeException failure = new RuntimeException("close failed");
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        try (SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
                JettyHttpClient client = tracingClient(tracerProvider, _ -> new StaticStreamingResponse(HttpStatus.OK, "body")
                {
                    @Override
                    public void close()
                    {
                        throw failure;
                    }
                })) {
            assertThatThrownBy(() -> client.execute(
                    prepareGet().setUri(URI.create("http://unused.invalid")).build(),
                    createStringResponseHandler()))
                    .isSameAs(failure);
            assertSpanFailure(exporter, failure);
        }
    }

    @Test
    void testHandledSynchronousTransportFailurePreservesSpanStatus()
            throws Exception
    {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        try (ResettingServer server = new ResettingServer();
                SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build();
                JettyHttpClient client = tracingClient(tracerProvider, chain -> chain.proceed(chain.request()))) {
            String result = client.execute(prepareGet().setUri(server.uri()).build(), new ResponseHandler<String, RuntimeException>()
            {
                @Override
                public String handleException(Request request, Exception exception)
                {
                    return "handled";
                }

                @Override
                public String handle(Request request, Response response)
                {
                    throw new AssertionError("transport failure unexpectedly produced a response");
                }
            });
            assertThat(result).isEqualTo("handled");

            SpanData span = exporter.getFinishedSpanItems().stream().collect(onlyElement());
            assertThat(span.getStatus().getStatusCode()).isEqualTo(UNSET);
            assertThat(span.getEvents()).isEmpty();
        }
    }

    @Test
    void testSynchronousTransportExceptionHandlerFailureIsRecordedBeforeSpanEnds()
            throws Exception
    {
        RuntimeException failure = new RuntimeException("exception handler failed");
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        try (ResettingServer server = new ResettingServer();
                SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build();
                JettyHttpClient client = tracingClient(tracerProvider, chain -> chain.proceed(chain.request()))) {
            assertThatThrownBy(() -> client.execute(prepareGet().setUri(server.uri()).build(), new ResponseHandler<>()
            {
                @Override
                public Object handleException(Request request, Exception exception)
                {
                    throw failure;
                }

                @Override
                public Object handle(Request request, Response response)
                {
                    throw new AssertionError("transport failure unexpectedly produced a response");
                }
            }))
                    .isSameAs(failure);
            assertSpanFailure(exporter, failure);
        }
    }

    @Test
    void testStreamingExecutionClosesResponseExactlyOnce()
    {
        AtomicInteger closeCount = new AtomicInteger();
        HttpClientInterceptor shortCircuit = _ -> new StaticStreamingResponse(HttpStatus.OK, "body", closeCount);

        try (JettyHttpClient client = new JettyHttpClient(new HttpClientConfig(), List.of(shortCircuit))) {
            StreamingResponse response = client.executeStreaming(prepareGet().setUri(URI.create("http://unused.invalid")).build());
            response.close();
            response.close();
        }

        assertThat(closeCount).hasValue(1);
    }

    @Test
    void testConcurrentChainsKeepRequestStateLocal()
            throws Exception
    {
        int requestCount = 16;
        CyclicBarrier barrier = new CyclicBarrier(requestCount);
        HttpClientInterceptor synchronize = chain -> {
            await(barrier);
            return chain.proceed(chain.request());
        };
        HttpClientInterceptor respondWithRequestId = chain -> new StaticStreamingResponse(
                HttpStatus.OK,
                requireNonNull(chain.request().getHeader(REQUEST_ID_HEADER), "request id is null"));

        try (JettyHttpClient client = new JettyHttpClient(new HttpClientConfig(), List.of(synchronize, respondWithRequestId));
                ExecutorService executor = Executors.newFixedThreadPool(requestCount)) {
            List<Future<String>> futures = java.util.stream.IntStream.range(0, requestCount)
                    .mapToObj(index -> executor.submit(() -> client.execute(
                            prepareGet()
                                    .setUri(URI.create("http://unused.invalid"))
                                    .setHeader(REQUEST_ID_HEADER, "request-" + index)
                                    .build(),
                            createStringResponseHandler()).getBody()))
                    .toList();

            for (int index = 0; index < requestCount; index++) {
                assertThat(futures.get(index).get(10, SECONDS)).isEqualTo("request-" + index);
            }
        }
    }

    @Test
    void testAsyncExecutionDoesNotInvokeSynchronousInterceptors()
            throws Exception
    {
        AtomicInteger invocationCount = new AtomicInteger();
        HttpClientInterceptor interceptor = chain -> {
            invocationCount.incrementAndGet();
            return chain.proceed(chain.request());
        };

        try (TestingHttpServer server = new TestingHttpServer(Optional.empty(), new EchoServlet());
                JettyHttpClient client = new JettyHttpClient(new HttpClientConfig(), List.of(interceptor))) {
            assertThat(client.executeAsync(prepareGet().setUri(server.baseURI()).build(), createStatusResponseHandler()).get().getStatusCode())
                    .isEqualTo(HttpStatus.OK.code());
        }

        assertThat(invocationCount).hasValue(0);
    }

    private static HttpClientInterceptor orderedInterceptor(String name, List<String> events, boolean shortCircuit)
    {
        return chain -> {
            events.add(name + "-before");
            StreamingResponse response = shortCircuit
                    ? new StaticStreamingResponse(HttpStatus.OK, "body")
                    : chain.proceed(chain.request());
            events.add(name + "-after");
            return response;
        };
    }

    private static JettyHttpClient tracingClient(SdkTracerProvider tracerProvider, HttpClientInterceptor interceptor)
    {
        return new JettyHttpClient(
                "interceptor-tracing-test",
                new HttpClientConfig(),
                List.of(),
                propagating(ContextPropagators.noop()),
                tracerProvider.get("testing"),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(interceptor));
    }

    private static void assertSpanFailure(InMemorySpanExporter exporter, RuntimeException failure)
    {
        SpanData span = exporter.getFinishedSpanItems().stream().collect(onlyElement());
        assertThat(span.getStatus().getStatusCode()).isEqualTo(ERROR);
        assertThat(span.getStatus().getDescription()).isEqualTo(failure.getMessage());
        assertThat(span.getEvents())
                .singleElement()
                .satisfies(event -> assertThat(event.getAttributes().get(JettyHttpClient.EXCEPTION_ESCAPED)).isTrue());
    }

    private static void await(CyclicBarrier barrier)
    {
        try {
            barrier.await(10, SECONDS);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class StaticStreamingResponse
            implements StreamingResponse
    {
        private final HttpStatus status;
        private final byte[] body;
        private final AtomicInteger closeCount;

        private StaticStreamingResponse(HttpStatus status, String body)
        {
            this(status, body, new AtomicInteger());
        }

        private StaticStreamingResponse(HttpStatus status, String body, AtomicInteger closeCount)
        {
            this.status = status;
            this.body = body.getBytes(StandardCharsets.UTF_8);
            this.closeCount = closeCount;
        }

        @Override
        public HttpVersion getHttpVersion()
        {
            return HttpVersion.HTTP_1;
        }

        @Override
        public int getStatusCode()
        {
            return status.code();
        }

        @Override
        public ListMultimap<HeaderName, String> getHeaders()
        {
            return ImmutableListMultimap.of();
        }

        @Override
        public Content getContent()
        {
            return new BytesContent(body);
        }

        @Override
        public InputStream getInputStream()
        {
            return new ByteArrayInputStream(body);
        }

        @Override
        public long getBytesRead()
        {
            return body.length;
        }

        @Override
        public void close()
        {
            closeCount.incrementAndGet();
        }
    }

    private static class ForwardingStreamingResponse
            implements StreamingResponse
    {
        private final StreamingResponse delegate;

        private ForwardingStreamingResponse(StreamingResponse delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public HttpVersion getHttpVersion()
        {
            return delegate.getHttpVersion();
        }

        @Override
        public int getStatusCode()
        {
            return delegate.getStatusCode();
        }

        @Override
        public ListMultimap<HeaderName, String> getHeaders()
        {
            return delegate.getHeaders();
        }

        @Override
        public Content getContent()
        {
            return delegate.getContent();
        }

        @Override
        public InputStream getInputStream()
                throws IOException
        {
            return delegate.getInputStream();
        }

        @Override
        public long getBytesRead()
        {
            return delegate.getBytesRead();
        }

        @Override
        public void close()
        {
            delegate.close();
        }
    }

    private static final class ResettingServer
            implements AutoCloseable
    {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final Future<?> serverTask;

        private ResettingServer()
                throws IOException
        {
            serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            serverTask = executor.submit(() -> {
                try (Socket socket = serverSocket.accept()) {
                    socket.setSoLinger(true, 0);
                }
                catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

        private URI uri()
        {
            return URI.create("http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + serverSocket.getLocalPort());
        }

        @Override
        public void close()
                throws Exception
        {
            serverSocket.close();
            executor.shutdownNow();
            serverTask.get(10, SECONDS);
        }
    }

    private static final class TestingPropagator
            implements TextMapPropagator
    {
        @Override
        public Collection<String> fields()
        {
            return ImmutableList.of(TRACE_HEADER.toString());
        }

        @Override
        public <C> void inject(Context context, C carrier, TextMapSetter<C> setter)
        {
            setter.set(carrier, TRACE_HEADER.toString(), "injected");
        }

        @Override
        public <C> Context extract(Context context, C carrier, TextMapGetter<C> getter)
        {
            return context;
        }
    }
}
