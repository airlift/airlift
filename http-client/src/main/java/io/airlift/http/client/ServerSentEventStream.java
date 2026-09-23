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

import io.airlift.json.JsonCodec;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.http.client.ResponseHandlerUtils.getResponseStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;

/**
 * A single-pass, incrementally decoded server-sent event stream.
 * Closing the stream releases the underlying HTTP response. The stream does not reconnect or replay events.
 */
public final class ServerSentEventStream<T>
        implements Closeable,
                   Iterable<ServerSentEvent<T>>,
                   Iterator<ServerSentEvent<T>>
{
    public static final int DEFAULT_MAX_EVENT_DATA_BYTES = 1024 * 1024;

    private static final int MAX_FIELD_PREFIX_BYTES = "data: ".length();

    private final StreamingResponse response;
    private final JsonCodec<T> codec;
    private final PushbackInputStream input;
    private final int maxEventDataBytes;

    private String lastEventId = "";
    private ServerSentEvent<T> nextEvent;
    private boolean nextEventReady;
    private boolean closed;
    private boolean initialUtf8BomChecked;

    public ServerSentEventStream(StreamingResponse response, JsonCodec<T> codec)
    {
        this(response, codec, DEFAULT_MAX_EVENT_DATA_BYTES);
    }

    ServerSentEventStream(StreamingResponse response, JsonCodec<T> codec, int maxEventDataBytes)
    {
        this.response = requireNonNull(response, "response is null");
        this.codec = requireNonNull(codec, "codec is null");
        checkArgument(maxEventDataBytes > 0, "maxEventDataBytes must be positive");
        checkArgument(maxEventDataBytes <= Integer.MAX_VALUE - MAX_FIELD_PREFIX_BYTES - 1, "maxEventDataBytes is too large");
        this.maxEventDataBytes = maxEventDataBytes;
        this.input = new PushbackInputStream(getResponseStream(response), 3);
    }

    @Override
    public Iterator<ServerSentEvent<T>> iterator()
    {
        return this;
    }

    @Override
    public boolean hasNext()
    {
        if (nextEventReady) {
            return true;
        }
        if (closed) {
            return false;
        }

        try {
            nextEvent = readNextEventFromInput();
            nextEventReady = nextEvent != null;
            return nextEventReady;
        }
        catch (IOException e) {
            closeAfterFailure();
            throw new UncheckedIOException("Failed reading server-sent event stream", e);
        }
        catch (RuntimeException e) {
            closeAfterFailure();
            throw e;
        }
    }

    @Override
    public ServerSentEvent<T> next()
    {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        ServerSentEvent<T> event = nextEvent;
        nextEvent = null;
        nextEventReady = false;
        return event;
    }

    /**
     * Blocks until the next complete event is available or the stream ends.
     */
    public Optional<ServerSentEvent<T>> readEvent()
    {
        if (nextEventReady) {
            return Optional.of(next());
        }
        if (closed) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(readNextEventFromInput());
        }
        catch (IOException e) {
            closeAfterFailure();
            throw new UncheckedIOException("Failed reading server-sent event stream", e);
        }
        catch (RuntimeException e) {
            closeAfterFailure();
            throw e;
        }
    }

    private ServerSentEvent<T> readNextEventFromInput()
            throws IOException
    {
        stripInitialUtf8Bom();

        StringBuilder data = new StringBuilder();
        int dataBytes = 0;
        boolean hasData = false;
        Optional<String> eventType = Optional.empty();
        Optional<Duration> retry = Optional.empty();

        while (true) {
            byte[] line = readLine(input, maxEventDataBytes + MAX_FIELD_PREFIX_BYTES + 1);
            if (line == null) {
                close();
                return null;
            }
            if (line.length == 0) {
                if (!hasData || data.isEmpty()) {
                    // per the specification, an empty data buffer dispatches nothing
                    eventType = Optional.empty();
                    hasData = false;
                    continue;
                }

                T decoded;
                try {
                    decoded = codec.fromJson(data.toString());
                }
                catch (IllegalArgumentException e) {
                    // the parser's cause quotes server-controlled data, so report only the target type like the safe JSON handlers
                    throw new IllegalArgumentException("Unable to create %s from server-sent event data".formatted(codec.getType()));
                }
                return new ServerSentEvent<>(eventType, optionalNonEmpty(lastEventId), decoded, retry);
            }
            if (line[0] == ':') {
                continue;
            }

            int separator = indexOf(line, (byte) ':');
            int valueStart;
            if (separator < 0) {
                separator = line.length;
                valueStart = line.length;
            }
            else {
                valueStart = separator + 1;
                if (valueStart < line.length && line[valueStart] == ' ') {
                    valueStart++;
                }
            }

            String value;
            if (matchesAscii(line, separator, "data")) {
                int valueBytes = line.length - valueStart;
                int separatorBytes = hasData ? 1 : 0;
                if (valueBytes > maxEventDataBytes - dataBytes - separatorBytes) {
                    throw new IOException("Server-sent event data exceeds maximum size of %s bytes".formatted(maxEventDataBytes));
                }
                if (hasData) {
                    data.append('\n');
                }
                data.append(decodeUtf8(line, valueStart));
                dataBytes += separatorBytes + valueBytes;
                hasData = true;
                continue;
            }

            value = decodeUtf8(line, valueStart);
            if (matchesAscii(line, separator, "event")) {
                eventType = optionalNonEmpty(value);
            }
            else if (matchesAscii(line, separator, "id")) {
                if (value.indexOf('\0') < 0) {
                    lastEventId = value;
                }
            }
            else if (matchesAscii(line, separator, "retry")) {
                Optional<Duration> parsedRetry = parseRetry(value);
                if (parsedRetry.isPresent()) {
                    retry = parsedRetry;
                }
            }
        }
    }

    private void stripInitialUtf8Bom()
            throws IOException
    {
        if (initialUtf8BomChecked) {
            return;
        }
        initialUtf8BomChecked = true;

        byte[] prefix = input.readNBytes(3);
        if (prefix.length != 3 || prefix[0] != (byte) 0xEF || prefix[1] != (byte) 0xBB || prefix[2] != (byte) 0xBF) {
            input.unread(prefix);
        }
    }

    private static byte[] readLine(PushbackInputStream input, int maxLineBytes)
            throws IOException
    {
        ByteArrayOutputStream line = new ByteArrayOutputStream(Math.min(maxLineBytes, 1024));
        while (true) {
            int value = input.read();
            if (value < 0) {
                return line.size() == 0 ? null : line.toByteArray();
            }
            if (value == '\n') {
                return line.toByteArray();
            }
            if (value == '\r') {
                int next = input.read();
                if (next >= 0 && next != '\n') {
                    input.unread(next);
                }
                return line.toByteArray();
            }
            if (line.size() == maxLineBytes) {
                throw new IOException("Server-sent event line exceeds maximum size of %s bytes".formatted(maxLineBytes));
            }
            line.write(value);
        }
    }

    private static int indexOf(byte[] value, byte target)
    {
        for (int index = 0; index < value.length; index++) {
            if (value[index] == target) {
                return index;
            }
        }
        return -1;
    }

    private static boolean matchesAscii(byte[] line, int length, String expected)
    {
        if (length != expected.length()) {
            return false;
        }
        for (int index = 0; index < length; index++) {
            if (line[index] != expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static String decodeUtf8(byte[] value, int offset)
    {
        // the specification decodes with U+FFFD replacement; line breaks cannot occur inside a multi-byte sequence
        return new String(value, offset, value.length - offset, UTF_8);
    }

    private static Optional<Duration> parseRetry(String value)
    {
        if (value.isEmpty() || !value.chars().allMatch(character -> character >= '0' && character <= '9')) {
            return Optional.empty();
        }
        try {
            return Optional.of(Duration.ofMillis(Long.parseLong(value)));
        }
        catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> optionalNonEmpty(String value)
    {
        return Optional.ofNullable(value).filter(not(String::isEmpty));
    }

    private void closeAfterFailure()
    {
        try {
            close();
        }
        catch (RuntimeException closeFailure) {
            // Preserve the parsing failure, which is more useful to the caller.
        }
    }

    @Override
    public void close()
    {
        if (!closed) {
            closed = true;
            response.close();
        }
    }
}
