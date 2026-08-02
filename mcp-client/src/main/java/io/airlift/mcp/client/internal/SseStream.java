package io.airlift.mcp.client.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.airlift.mcp.client.settings.NotificationConsumer;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

class SseStream
{
    private final InputStream inputStream;
    private final BufferedReader reader;
    private final JsonMapper jsonMapper;
    private final NotificationConsumer notificationConsumer;
    private final AtomicBoolean interrupted = new AtomicBoolean();

    SseStream(InputStream inputStream, JsonMapper jsonMapper, NotificationConsumer notificationConsumer)
    {
        this.inputStream = requireNonNull(inputStream, "inputStream is null");
        this.reader = new BufferedReader(new InputStreamReader(inputStream, UTF_8));
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
        this.notificationConsumer = requireNonNull(notificationConsumer, "notificationConsumer is null");
    }

    void interrupt()
    {
        if (!interrupted.compareAndSet(false, true)) {
            return;
        }

        // close the raw input stream, not the reader - BufferedReader.close() synchronizes on the
        // same monitor as an in-progress readLine() and would block until the read returns, while
        // closing the underlying stream unblocks the read
        try {
            inputStream.close();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    boolean isInterrupted()
    {
        return interrupted.get();
    }

    JsonRpcResponse<Object> read()
            throws IOException
    {
        JsonRpcResponse<Object> response = null;
        StringBuilder currentResponseBuilder = new StringBuilder();

        while (true) {
            String line = reader.readLine();
            if (line == null) {
                if (!currentResponseBuilder.isEmpty()) {
                    throw new EOFException("Unexpected end of stream. Incomplete data in SSE stream.");
                }
                break;
            }

            if (line.isBlank()) {
                response = checkResponse(currentResponseBuilder);
                if (response != null) {
                    break;
                }
            }
            else if (line.startsWith("data:")) {
                String json = line.substring("data:".length()).trim();
                currentResponseBuilder.append(json).append('\n');
            }
        }

        if (response == null) {
            throw new IllegalStateException("No response received");
        }

        return response;
    }

    private JsonRpcResponse<Object> checkResponse(StringBuilder json)
            throws IOException
    {
        if (json.isEmpty()) {
            return null;
        }

        JsonNode tree = jsonMapper.readTree(json.toString());
        json.setLength(0);

        if (tree.has("method")) {
            JsonRpcRequest<Object> notification = jsonMapper.treeToValue(tree, new TypeReference<>() {});
            notificationConsumer.accept(notification.id(), notification.method(), notification.params());
            return null;
        }

        return jsonMapper.treeToValue(tree, new TypeReference<>() {});
    }
}
