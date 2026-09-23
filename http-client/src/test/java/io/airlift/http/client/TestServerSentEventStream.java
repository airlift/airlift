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
import io.airlift.http.client.testing.TestingResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.StreamSupport;

import static io.airlift.http.client.HttpStatus.OK;
import static io.airlift.http.client.ResponseHandlerUtils.isEventStreamContent;
import static io.airlift.http.client.ServerSentEventStream.DEFAULT_MAX_EVENT_DATA_BYTES;
import static io.airlift.json.JsonCodec.jsonCodec;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestServerSentEventStream
{
    @Test
    void testIncrementalEventParsingAndLifecycle()
    {
        String input =
                """
                : keepalive
                id: event-1
                event: status
                retry: 1500
                data: {"message":"héllo",
                data: "count":1}

                : another keepalive
                data: {"message":"next","count":2}

                """;
        TestingStreamingResponse response = response(input);

        List<ServerSentEvent<TestEvent>> events;
        try (ServerSentEventStream<TestEvent> stream = new ServerSentEventStream<>(response, jsonCodec(TestEvent.class))) {
            events = StreamSupport.stream(stream.spliterator(), false).toList();
            assertThat(response.isClosed()).isTrue();
        }

        assertThat(events).containsExactly(
                new ServerSentEvent<>(Optional.of("status"), Optional.of("event-1"), new TestEvent("héllo", 1), Optional.of(Duration.ofMillis(1500))),
                new ServerSentEvent<>(Optional.empty(), Optional.of("event-1"), new TestEvent("next", 2), Optional.empty()));
    }

    @Test
    void testCloseBeforeEndReleasesResponse()
    {
        TestingStreamingResponse response = response("data: {\"message\":\"first\",\"count\":1}\n\ndata: {\"message\":\"second\",\"count\":2}\n\n");

        ServerSentEventStream<TestEvent> stream = new ServerSentEventStream<>(response, jsonCodec(TestEvent.class));
        assertThat(stream.readEvent()).contains(new ServerSentEvent<>(Optional.empty(), Optional.empty(), new TestEvent("first", 1), Optional.empty()));
        assertThat(response.isClosed()).isFalse();

        stream.close();
        assertThat(response.isClosed()).isTrue();
        assertThat(stream.hasNext()).isFalse();
    }

    @Test
    void testInitialUtf8BomIsIgnored()
    {
        TestingStreamingResponse response = response("\uFEFFdata: {\"message\":\"first\",\"count\":1}\n\n");

        try (ServerSentEventStream<TestEvent> stream = new ServerSentEventStream<>(response, jsonCodec(TestEvent.class))) {
            assertThat(stream.readEvent()).contains(new ServerSentEvent<>(Optional.empty(), Optional.empty(), new TestEvent("first", 1), Optional.empty()));
        }
    }

    @Test
    void testRetryOnlyDirectiveIsRetainedUntilNextEvent()
    {
        TestingStreamingResponse response = response(
                """
                retry: 1500

                : keepalive

                data: {"message":"first","count":1}

                data: {"message":"second","count":2}

                """);

        try (ServerSentEventStream<TestEvent> stream = new ServerSentEventStream<>(response, jsonCodec(TestEvent.class))) {
            assertThat(stream.readEvent()).contains(new ServerSentEvent<>(Optional.empty(), Optional.empty(), new TestEvent("first", 1), Optional.of(Duration.ofMillis(1500))));
            assertThat(stream.readEvent()).contains(new ServerSentEvent<>(Optional.empty(), Optional.empty(), new TestEvent("second", 2), Optional.empty()));
        }
    }

    @Test
    void testEmptyDataIsNotDispatched()
    {
        String input =
                """
                event: ping
                data:

                data: {"message":"next","count":2}

                """;
        TestingStreamingResponse response = response(input);

        List<ServerSentEvent<TestEvent>> events;
        try (ServerSentEventStream<TestEvent> stream = new ServerSentEventStream<>(response, jsonCodec(TestEvent.class))) {
            events = StreamSupport.stream(stream.spliterator(), false).toList();
        }

        assertThat(events).containsExactly(
                new ServerSentEvent<>(Optional.empty(), Optional.empty(), new TestEvent("next", 2), Optional.empty()));
        assertThat(response.isClosed()).isTrue();
    }

    @Test
    void testIncompleteEventIsNotDispatchedAtEndOfStream()
    {
        TestingStreamingResponse response = response("data: {\"message\":\"incomplete\",\"count\":1}");

        try (ServerSentEventStream<TestEvent> stream = new ServerSentEventStream<>(response, jsonCodec(TestEvent.class))) {
            assertThat(stream.readEvent()).isEmpty();
        }

        assertThat(response.isClosed()).isTrue();
    }

    @Test
    void testMalformedJsonClosesResponse()
    {
        TestingStreamingResponse response = response("data: {malformed\n\n");
        ServerSentEventStream<TestEvent> stream = new ServerSentEventStream<>(response, jsonCodec(TestEvent.class));

        assertThatThrownBy(stream::hasNext)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Unable to create ")
                .hasMessageEndingWith(" from server-sent event data")
                .hasMessageContaining(TestEvent.class.getName())
                .hasMessageNotContaining("malformed")
                .hasNoCause();
        assertThat(response.isClosed()).isTrue();
    }

    @Test
    void testEventStreamContentTypeDetection()
    {
        assertThat(isEventStreamContent(new TestingResponse(OK, ImmutableListMultimap.of(HeaderNames.CONTENT_TYPE, "text/event-stream"), new byte[0]))).isTrue();
        assertThat(isEventStreamContent(new TestingResponse(OK, ImmutableListMultimap.of(HeaderNames.CONTENT_TYPE, "Text/Event-Stream; charset=utf-8"), new byte[0]))).isTrue();
        assertThat(isEventStreamContent(new TestingResponse(OK, ImmutableListMultimap.of(HeaderNames.CONTENT_TYPE, "application/json"), new byte[0]))).isFalse();
        assertThat(isEventStreamContent(new TestingResponse(OK, ImmutableListMultimap.of(HeaderNames.CONTENT_TYPE, "not a media type"), new byte[0]))).isFalse();
        assertThat(isEventStreamContent(new TestingResponse(OK, ImmutableListMultimap.of(), new byte[0]))).isFalse();
    }

    @Test
    void testDefaultEventDataLimitBoundary()
    {
        String value = "a".repeat(DEFAULT_MAX_EVENT_DATA_BYTES - 2);
        TestingStreamingResponse response = response("data: \"" + value + "\"\n\n");

        try (ServerSentEventStream<String> stream = new ServerSentEventStream<>(response, jsonCodec(String.class))) {
            assertThat(stream.readEvent()).contains(new ServerSentEvent<>(Optional.empty(), Optional.empty(), value, Optional.empty()));
        }

        assertThat(response.isClosed()).isTrue();
    }

    @Test
    void testOneByteOverDefaultEventDataLimitClosesResponse()
    {
        String value = "a".repeat(DEFAULT_MAX_EVENT_DATA_BYTES - 1);
        TestingStreamingResponse response = response("data: \"" + value + "\"\n\n");
        ServerSentEventStream<String> stream = new ServerSentEventStream<>(response, jsonCodec(String.class));

        assertThatThrownBy(stream::readEvent)
                .isInstanceOf(UncheckedIOException.class)
                .hasRootCauseMessage("Server-sent event data exceeds maximum size of %s bytes", DEFAULT_MAX_EVENT_DATA_BYTES);
        assertThat(response.isClosed()).isTrue();
    }

    @Test
    void testEventDataLimitCountsUtf8Bytes()
    {
        String boundary = "é".repeat((DEFAULT_MAX_EVENT_DATA_BYTES - 2) / 2);
        TestingStreamingResponse boundaryResponse = response("data: \"" + boundary + "\"\n\n");
        try (ServerSentEventStream<String> stream = new ServerSentEventStream<>(boundaryResponse, jsonCodec(String.class))) {
            assertThat(stream.readEvent()).contains(new ServerSentEvent<>(Optional.empty(), Optional.empty(), boundary, Optional.empty()));
        }

        TestingStreamingResponse oversizedResponse = response("data: \"" + boundary + "a\"\n\n");
        ServerSentEventStream<String> oversizedStream = new ServerSentEventStream<>(oversizedResponse, jsonCodec(String.class));
        assertThatThrownBy(oversizedStream::readEvent)
                .isInstanceOf(UncheckedIOException.class)
                .hasRootCauseMessage("Server-sent event data exceeds maximum size of %s bytes", DEFAULT_MAX_EVENT_DATA_BYTES);
        assertThat(oversizedResponse.isClosed()).isTrue();
    }

    private static TestingStreamingResponse response(String content)
    {
        return new TestingStreamingResponse(new ByteArrayInputStream(content.getBytes(UTF_8)));
    }

    record TestEvent(String message, int count) {}

    private static final class TestingStreamingResponse
            extends TestingResponse
            implements StreamingResponse
    {
        private final AtomicBoolean closed = new AtomicBoolean();

        private TestingStreamingResponse(InputStream input)
        {
            super(OK, ImmutableListMultimap.of(HeaderNames.CONTENT_TYPE, "text/event-stream"), input);
        }

        @Override
        public void close()
        {
            closed.set(true);
        }

        private boolean isClosed()
        {
            return closed.get();
        }
    }
}
