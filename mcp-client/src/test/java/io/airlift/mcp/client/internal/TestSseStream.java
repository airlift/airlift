package io.airlift.mcp.client.internal;

import io.airlift.mcp.client.settings.NotificationConsumer;
import io.airlift.mcp.model.JsonRpcResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.airlift.mcp.client.McpMapper.jsonMapper;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestSseStream
{
    @Test
    public void testSingleLineResponse()
            throws IOException
    {
        JsonRpcResponse<Object> response = read(
                """
                data: {"jsonrpc":"2.0","id":"1","result":{"value":123}}

                """);

        assertThat(response.id()).isEqualTo("1");
        assertThat(response.result()).contains(Map.of("value", 123));
    }

    @Test
    public void testMultiLineDataIsOneEvent()
            throws IOException
    {
        // an event may split its payload across multiple data: lines (e.g. pretty-printed JSON);
        // they are concatenated and dispatched as one event at the blank line
        JsonRpcResponse<Object> response = read(
                """
                data: {
                data:   "jsonrpc": "2.0",
                data:   "id": "1",
                data:   "result": {"value": 123}
                data: }

                """);

        assertThat(response.id()).isEqualTo("1");
        assertThat(response.result()).contains(Map.of("value", 123));
    }

    @Test
    public void testNotificationsAreDeliveredBeforeTheResponse()
            throws IOException
    {
        List<String> notifications = new CopyOnWriteArrayList<>();

        JsonRpcResponse<Object> response = read(
                """
                data: {"jsonrpc":"2.0","method":"notifications/message","params":{"level":"info","data":"hey"}}

                data: {"jsonrpc":"2.0","id":"1","result":{}}

                """,
                (_, method, _) -> notifications.add(method));

        assertThat(notifications).containsExactly("notifications/message");
        assertThat(response.id()).isEqualTo("1");
    }

    @Test
    public void testHeartbeatsAndPaddingAreIgnored()
            throws IOException
    {
        // comment lines and consecutive blank lines are legal padding - commonly sent as
        // keep-alives by servers and proxies - and must not be dispatched as events
        JsonRpcResponse<Object> response = read(
                """
                :ka


                :ka

                data: {"jsonrpc":"2.0","id":"1","result":{}}

                """);

        assertThat(response.id()).isEqualTo("1");
    }

    @Test
    public void testUnknownFieldsAreIgnored()
            throws IOException
    {
        JsonRpcResponse<Object> response = read(
                """
                event: message
                id: 42
                retry: 1000
                data: {"jsonrpc":"2.0","id":"1","result":{}}

                """);

        assertThat(response.id()).isEqualTo("1");
    }

    @Test
    public void testEndOfStreamMidEventIsTruncation()
    {
        // per the SSE spec an event is only dispatched at its terminating blank line - a stream
        // that ends with pending data was truncated and the incomplete event must not be dispatched
        assertThatThrownBy(() -> read(
                """
                data: {"jsonrpc":"2.0","id":"1","result":{}}"""))
                .isInstanceOf(EOFException.class)
                .hasMessageContaining("Incomplete data");
    }

    @Test
    public void testStreamWithoutResponseFails()
    {
        assertThatThrownBy(() -> read(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No response received");

        // notifications alone do not satisfy a request/response read
        assertThatThrownBy(() -> read(
                """
                data: {"jsonrpc":"2.0","method":"notifications/message","params":{}}

                """))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No response received");
    }

    @Test
    public void testInterruptClosesTheStreamOnce()
            throws IOException
    {
        CloseTrackingInputStream inputStream = new CloseTrackingInputStream(stream(""));
        SseStream sseStream = new SseStream(inputStream, jsonMapper(), (_, _, _) -> {});

        assertThat(sseStream.isInterrupted()).isFalse();

        sseStream.interrupt();
        assertThat(sseStream.isInterrupted()).isTrue();
        assertThat(inputStream.closeCount).isEqualTo(1);

        // interruption is idempotent - the stream is closed exactly once
        sseStream.interrupt();
        assertThat(inputStream.closeCount).isEqualTo(1);
    }

    private static JsonRpcResponse<Object> read(String content)
            throws IOException
    {
        return read(content, (_, _, _) -> {});
    }

    private static JsonRpcResponse<Object> read(String content, NotificationConsumer notificationConsumer)
            throws IOException
    {
        return new SseStream(stream(content), jsonMapper(), notificationConsumer).read();
    }

    private static InputStream stream(String content)
    {
        return new ByteArrayInputStream(content.getBytes(UTF_8));
    }

    private static class CloseTrackingInputStream
            extends FilterInputStream
    {
        private int closeCount;

        CloseTrackingInputStream(InputStream delegate)
        {
            super(delegate);
        }

        @Override
        public void close()
                throws IOException
        {
            closeCount++;
            super.close();
        }
    }
}
