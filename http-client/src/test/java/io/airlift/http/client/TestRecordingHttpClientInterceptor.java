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
package io.airlift.http.client;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import io.airlift.http.client.RecordedExchange.CaptureState;
import io.airlift.http.client.RecordedExchange.CapturedBody;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.units.DataSize;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static io.airlift.http.client.RecordedExchangeSanitizer.REDACTED;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static io.airlift.http.client.StreamingBodyGenerator.streamingBodyGenerator;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.units.DataSize.Unit.BYTE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestRecordingHttpClientInterceptor
{
    @Test
    void testRecordsSanitizedRealExchangeWithoutConsumingDecoder()
            throws Exception
    {
        EchoServlet servlet = new EchoServlet();
        servlet.setResponseStatusCode(HttpStatus.CREATED.code());
        servlet.addResponseHeader("Set-Cookie", "session=response-secret");
        servlet.setResponseBody("response-secret");
        ConcurrentLinkedQueue<RecordedExchange> recordings = new ConcurrentLinkedQueue<>();
        AtomicBoolean callerSanitizerInvoked = new AtomicBoolean();
        RecordedExchangeSanitizer callerSanitizer = exchange -> {
            callerSanitizerInvoked.set(true);
            return exchange
                    .withRequest(exchange.request()
                            .withUri("http://recorded.invalid/resource?access_token=" + REDACTED)
                            .withBody(redactedBody()))
                    .withResponse(exchange.response().map(response -> response.withBody(redactedBody())));
        };
        RecordingHttpClientInterceptor recorder = new RecordingHttpClientInterceptor(
                DataSize.of(1_024, BYTE),
                callerSanitizer,
                recordings::add);

        try (TestingHttpServer server = new TestingHttpServer(Optional.empty(), servlet);
                JettyHttpClient client = new JettyHttpClient(new HttpClientConfig(), List.of(recorder))) {
            Request request = preparePost()
                    .setUri(server.baseURI().resolve("/resource?access_token=request-secret"))
                    .setHeader(HeaderName.of("Authorization"), "Bearer request-secret")
                    .setHeader(HeaderName.of("Cookie"), "session=request-secret")
                    .setHeader(HeaderName.of("X-GitHub-Token"), "request-secret")
                    .setBodyGenerator(createStaticBodyGenerator("request-secret", UTF_8))
                    .build();

            StringResponseHandler.StringResponse response = client.execute(request, createStringResponseHandler());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED.code());
            assertThat(response.getBody()).isEqualTo("response-secret");
        }

        assertThat(callerSanitizerInvoked).isTrue();
        assertThat(recordings).hasSize(1);
        RecordedExchange exchange = recordings.element();
        assertThat(exchange.version()).isEqualTo(RecordedExchange.CURRENT_VERSION);
        assertThat(exchange.sequence()).isEqualTo(1);
        assertThat(exchange.request().method()).isEqualTo("POST");
        assertThat(exchange.request().uri()).isEqualTo("http://recorded.invalid/resource?access_token=" + REDACTED);
        assertThat(exchange.request().headers().get("authorization")).containsExactly(REDACTED);
        assertThat(exchange.request().headers().get("cookie")).containsExactly(REDACTED);
        assertThat(exchange.request().headers().get("x-github-token")).containsExactly(REDACTED);
        assertThat(new String(exchange.request().body().bytes(), UTF_8)).isEqualTo(REDACTED);
        assertThat(exchange.response()).get().satisfies(recordedResponse -> {
            assertThat(recordedResponse.statusCode()).isEqualTo(HttpStatus.CREATED.code());
            assertThat(recordedResponse.headers().get("set-cookie")).containsExactly(REDACTED);
            assertThat(new String(recordedResponse.body().bytes(), UTF_8)).isEqualTo(REDACTED);
        });
        assertThat(exchange.failure()).isEmpty();
    }

    @Test
    void testStreamingResponseUsesOneTeeAndCompletesAtEof()
            throws Exception
    {
        EchoServlet servlet = new EchoServlet();
        servlet.setResponseBody("streamed-body");
        ConcurrentLinkedQueue<RecordedExchange> recordings = new ConcurrentLinkedQueue<>();
        RecordingHttpClientInterceptor recorder = recorder(1_024, recordings);

        try (TestingHttpServer server = new TestingHttpServer(Optional.empty(), servlet);
                JettyHttpClient client = new JettyHttpClient(new HttpClientConfig(), List.of(recorder));
                StreamingResponse response = client.executeStreaming(prepareGet().setUri(server.baseURI()).build())) {
            Response.InputStreamContent content = (Response.InputStreamContent) response.getContent();
            assertThat(response.getInputStream()).isSameAs(content.inputStream());
            assertThat(new String(content.inputStream().readAllBytes(), UTF_8)).isEqualTo("streamed-body");
        }

        assertThat(recordings).hasSize(1);
        CapturedBody body = recordings.element().response().orElseThrow().body();
        assertThat(new String(body.bytes(), UTF_8)).isEqualTo("streamed-body");
        assertThat(body.state()).isEqualTo(CaptureState.COMPLETE);
        assertThat(body.truncated()).isFalse();
    }

    @Test
    void testEarlyCloseRecordsIncompleteBody()
            throws Exception
    {
        EchoServlet servlet = new EchoServlet();
        servlet.setResponseBody("streamed-body");
        ConcurrentLinkedQueue<RecordedExchange> recordings = new ConcurrentLinkedQueue<>();
        RecordingHttpClientInterceptor recorder = recorder(1_024, recordings);

        try (TestingHttpServer server = new TestingHttpServer(Optional.empty(), servlet);
                JettyHttpClient client = new JettyHttpClient(new HttpClientConfig(), List.of(recorder));
                StreamingResponse response = client.executeStreaming(prepareGet().setUri(server.baseURI()).build())) {
            assertThat(response.getInputStream().readNBytes(3)).containsExactly("str".getBytes(UTF_8));
        }

        assertThat(recordings).hasSize(1);
        CapturedBody body = recordings.element().response().orElseThrow().body();
        assertThat(new String(body.bytes(), UTF_8)).isEqualTo("str");
        assertThat(body.state()).isEqualTo(CaptureState.INCOMPLETE);
        assertThat(body.truncated()).isFalse();
    }

    @Test
    void testRequestAndResponseCaptureAreBoundedWithoutTruncatingDecoder()
            throws Exception
    {
        EchoServlet servlet = new EchoServlet();
        servlet.setResponseBody("response-body");
        ConcurrentLinkedQueue<RecordedExchange> recordings = new ConcurrentLinkedQueue<>();
        RecordingHttpClientInterceptor recorder = recorder(4, recordings);

        try (TestingHttpServer server = new TestingHttpServer(Optional.empty(), servlet);
                JettyHttpClient client = new JettyHttpClient(new HttpClientConfig(), List.of(recorder))) {
            Request request = preparePost()
                    .setUri(server.baseURI())
                    .setBodyGenerator(createStaticBodyGenerator("request-body", UTF_8))
                    .build();
            assertThat(client.execute(request, createStringResponseHandler()).getBody()).isEqualTo("response-body");
        }

        RecordedExchange exchange = recordings.element();
        assertThat(new String(exchange.request().body().bytes(), UTF_8)).isEqualTo("requ");
        assertThat(exchange.request().body().state()).isEqualTo(CaptureState.COMPLETE);
        assertThat(exchange.request().body().truncated()).isTrue();
        assertThat(new String(exchange.response().orElseThrow().body().bytes(), UTF_8)).isEqualTo("resp");
        assertThat(exchange.response().orElseThrow().body().state()).isEqualTo(CaptureState.COMPLETE);
        assertThat(exchange.response().orElseThrow().body().truncated()).isTrue();
    }

    @Test
    void testByteBufferRequestSnapshotDoesNotConsumeBuffers()
    {
        ByteBuffer first = ByteBuffer.wrap("first".getBytes(UTF_8));
        first.position(1);
        ByteBuffer second = ByteBuffer.wrap("-second".getBytes(UTF_8));
        ConcurrentLinkedQueue<RecordedExchange> recordings = new ConcurrentLinkedQueue<>();
        RecordingHttpClientInterceptor recorder = recorder(1_024, recordings);
        Request request = preparePost()
                .setUri(URI.create("http://unused.invalid"))
                .setBodyGenerator(new ByteBufferBodyGenerator(first, second))
                .build();

        try (StreamingResponse ignored = recorder.intercept(chain(request, _ -> new StaticStreamingResponse("response")))) {
            assertThat(first.position()).isEqualTo(1);
            assertThat(second.position()).isZero();
        }

        assertThat(new String(recordings.element().request().body().bytes(), UTF_8)).isEqualTo("irst-second");
        assertThat(recordings.element().request().body().state()).isEqualTo(CaptureState.COMPLETE);
    }

    @Test
    void testFileAndStreamingRequestCaptureIsUnsupported()
    {
        ConcurrentLinkedQueue<RecordedExchange> recordings = new ConcurrentLinkedQueue<>();
        RecordingHttpClientInterceptor recorder = recorder(1_024, recordings);
        List<Request> requests = List.of(
                preparePost()
                        .setUri(URI.create("http://unused.invalid/stream"))
                        .setBodyGenerator(streamingBodyGenerator(new ByteArrayInputStream("request".getBytes(UTF_8))))
                        .build(),
                preparePost()
                        .setUri(URI.create("http://unused.invalid/file"))
                        .setBodyGenerator(new FileBodyGenerator(Path.of("must-not-be-read")))
                        .build());

        for (Request request : requests) {
            try (StreamingResponse response = recorder.intercept(chain(request, _ -> new StaticStreamingResponse("response")))) {
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK.code());
            }
        }

        assertThat(recordings).allSatisfy(exchange -> {
            assertThat(exchange.request().body().state()).isEqualTo(CaptureState.UNSUPPORTED);
            assertThat(exchange.request().body().bytes()).isEmpty();
        });
    }

    @Test
    void testSkippedResponseBytesAreStillCaptured()
            throws Exception
    {
        ConcurrentLinkedQueue<RecordedExchange> recordings = new ConcurrentLinkedQueue<>();
        RecordingHttpClientInterceptor recorder = recorder(1_024, recordings);
        Request request = prepareGet().setUri(URI.create("http://unused.invalid")).build();

        try (StreamingResponse response = recorder.intercept(chain(
                request,
                _ -> new StreamStreamingResponse(new ByteArrayInputStream("abcdef".getBytes(UTF_8)))))) {
            assertThat(response.getInputStream().skip(2)).isEqualTo(2);
            assertThat(new String(response.getInputStream().readAllBytes(), UTF_8)).isEqualTo("cdef");
        }

        CapturedBody body = recordings.element().response().orElseThrow().body();
        assertThat(new String(body.bytes(), UTF_8)).isEqualTo("abcdef");
        assertThat(body.state()).isEqualTo(CaptureState.COMPLETE);
    }

    @Test
    void testShortCircuitResponseIsRecorded()
    {
        ConcurrentLinkedQueue<RecordedExchange> recordings = new ConcurrentLinkedQueue<>();
        RecordingHttpClientInterceptor recorder = recorder(1_024, recordings);
        Request request = prepareGet().setUri(URI.create("http://unused.invalid")).build();

        try (JettyHttpClient client = new JettyHttpClient(
                new HttpClientConfig(),
                List.<HttpClientInterceptor>of(recorder, _ -> new StaticStreamingResponse("short-circuit")))) {
            assertThat(client.execute(request, createStringResponseHandler()).getBody()).isEqualTo("short-circuit");
        }

        assertThat(new String(recordings.element().response().orElseThrow().body().bytes(), UTF_8)).isEqualTo("short-circuit");
    }

    @Test
    void testFailureRecordsRootCauseInsteadOfTransportWrapper()
    {
        ConcurrentLinkedQueue<RecordedExchange> recordings = new ConcurrentLinkedQueue<>();
        RecordingHttpClientInterceptor recorder = recorder(1_024, recordings);
        Request request = prepareGet().setUri(URI.create("http://unused.invalid")).build();
        IOException transportFailure = new IOException("connection failed");

        assertThatThrownBy(() -> recorder.intercept(chain(request, _ -> {
            throw new UncheckedIOException("transport wrapper", transportFailure);
        })))
                .isInstanceOf(UncheckedIOException.class)
                .hasCause(transportFailure);

        assertThat(recordings).hasSize(1);
        assertThat(recordings.element().response()).isEmpty();
        assertThat(recordings.element().failure()).get().satisfies(failure -> {
            assertThat(failure.type()).isEqualTo(IOException.class.getName());
            assertThat(failure.message()).isEqualTo("connection failed");
        });
    }

    @Test
    void testResponseReadFailureIsRecordedWithIncompleteBody()
            throws Exception
    {
        ConcurrentLinkedQueue<RecordedExchange> recordings = new ConcurrentLinkedQueue<>();
        RecordingHttpClientInterceptor recorder = recorder(1_024, recordings);
        Request request = prepareGet().setUri(URI.create("http://unused.invalid")).build();
        InputStream failingInput = new InputStream()
        {
            @Override
            public int read()
                    throws IOException
            {
                throw new IOException("read failed");
            }
        };

        try (StreamingResponse response = recorder.intercept(chain(request, _ -> new StreamStreamingResponse(failingInput)))) {
            assertThatThrownBy(() -> response.getInputStream().read())
                    .isInstanceOf(IOException.class)
                    .hasMessage("read failed");
        }

        RecordedExchange exchange = recordings.element();
        assertThat(exchange.response()).get().satisfies(response -> assertThat(response.body().state()).isEqualTo(CaptureState.INCOMPLETE));
        assertThat(exchange.failure()).get().satisfies(failure -> assertThat(failure.type()).isEqualTo(IOException.class.getName()));
    }

    @Test
    void testConcurrentSequencesAreUniqueAndCorrelateRequests()
            throws Exception
    {
        int requestCount = 100;
        ConcurrentLinkedQueue<RecordedExchange> recordings = new ConcurrentLinkedQueue<>();
        RecordingHttpClientInterceptor recorder = recorder(1_024, recordings);
        List<Future<?>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(16)) {
            for (int index = 0; index < requestCount; index++) {
                int requestIndex = index;
                futures.add(executor.submit(() -> {
                    Request request = prepareGet()
                            .setUri(URI.create("http://unused.invalid/request-" + requestIndex))
                            .build();
                    try (StreamingResponse ignored = recorder.intercept(chain(request, _ -> new StaticStreamingResponse("response-" + requestIndex)))) {
                        return null;
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(10, SECONDS);
            }
        }

        assertThat(recordings).hasSize(requestCount);
        assertThat(recordings.stream().map(RecordedExchange::sequence).sorted()).containsExactlyElementsOf(
                java.util.stream.LongStream.rangeClosed(1, requestCount).boxed().toList());
        assertThat(recordings.stream()
                .sorted(Comparator.comparingLong(RecordedExchange::sequence))
                .map(exchange -> exchange.request().uri()))
                .doesNotHaveDuplicates();
    }

    private static RecordingHttpClientInterceptor recorder(int maxBodyBytes, ConcurrentLinkedQueue<RecordedExchange> recordings)
    {
        return new RecordingHttpClientInterceptor(DataSize.of(maxBodyBytes, BYTE), exchange -> exchange, recordings::add);
    }

    private static CapturedBody redactedBody()
    {
        return new CapturedBody(REDACTED.getBytes(UTF_8), CaptureState.COMPLETE, false);
    }

    private static HttpClientInterceptor.Chain chain(Request request, Function<Request, StreamingResponse> proceed)
    {
        return new HttpClientInterceptor.Chain()
        {
            @Override
            public Request request()
            {
                return request;
            }

            @Override
            public StreamingResponse proceed(Request request)
            {
                return proceed.apply(request);
            }
        };
    }

    private static class StaticStreamingResponse
            implements StreamingResponse
    {
        private final byte[] body;

        private StaticStreamingResponse(String body)
        {
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public HttpVersion getHttpVersion()
        {
            return HttpVersion.HTTP_1;
        }

        @Override
        public int getStatusCode()
        {
            return HttpStatus.OK.code();
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
        public void close() {}
    }

    private static final class StreamStreamingResponse
            extends StaticStreamingResponse
    {
        private final InputStream inputStream;

        private StreamStreamingResponse(InputStream inputStream)
        {
            super("");
            this.inputStream = inputStream;
        }

        @Override
        public Content getContent()
        {
            return new InputStreamContent(inputStream);
        }

        @Override
        public InputStream getInputStream()
        {
            return inputStream;
        }
    }
}
