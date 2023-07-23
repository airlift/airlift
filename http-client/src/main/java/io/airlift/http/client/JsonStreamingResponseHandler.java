package io.airlift.http.client;

import com.fasterxml.jackson.core.JsonToken;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.primitives.Ints;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonCodecParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.NoSuchElementException;
import java.util.Set;

import static io.airlift.http.client.JsonResponseHandler.validateResponse;
import static io.airlift.http.client.ResponseHandlerUtils.propagate;
import static java.util.Objects.requireNonNull;

public class JsonStreamingResponseHandler<T>
        implements StreamingResponseHandler<T>
{
    private final Set<Integer> successfulResponseCodes;
    private final JsonCodec<T> codec;

    public static <T> JsonStreamingResponseHandler<T> createJsonStreamingResponse(JsonCodec<T> codec)
    {
        return new JsonStreamingResponseHandler<>(codec, 200, 201, 202, 203, 204, 205, 206);
    }

    public static <T> JsonStreamingResponseHandler<T> createJsonStreamingResponse(JsonCodec<T> codec, int firstSuccessfulResponseCode, int... otherSuccessfulResponseCodes)
    {
        return new JsonStreamingResponseHandler<>(codec, firstSuccessfulResponseCode, otherSuccessfulResponseCodes);
    }

    private JsonStreamingResponseHandler(JsonCodec<T> codec, int firstSuccessfulResponseCode, int... otherSuccessfulResponseCodes)
    {
        this.codec = requireNonNull(codec, "codec is null");
        successfulResponseCodes = ImmutableSet.<Integer>builder().add(firstSuccessfulResponseCode).addAll(Ints.asList(otherSuccessfulResponseCodes)).build();
    }

    @Override
    public StreamingResponseIterator<T> handle(Request request, StreamingResponse response)
    {
        try {
            validateResponse(request, response, successfulResponseCodes);
        }
        catch (UnexpectedResponseException e) {
            throw cleanup(request, null, response, e);
        }

        JsonCodecParser<T> jsonCodecParser = null;
        try {
            jsonCodecParser = codec.newParser(response.getInputStream());
            if (jsonCodecParser.nextToken() != JsonToken.START_ARRAY) {
                throw new UnexpectedResponseException("Expected JSON array", request, response);
            }
            jsonCodecParser.nextToken();
        }
        catch (IOException e) {
            throw cleanup(request, jsonCodecParser, response, new UncheckedIOException(e));
        }

        return new StreamingIterator<>(request, response, jsonCodecParser);
    }

    private static RuntimeException cleanup(Request request, JsonCodecParser<?> jsonCodecParser, StreamingResponse response, RuntimeException cause)
    {
        if ((jsonCodecParser != null) && !jsonCodecParser.isClosed()) {
            try {
                jsonCodecParser.close();
            }
            catch (Throwable t) {
                if (cause == null) {
                    cause = propagate(request, t);
                }
                else {
                    cause.addSuppressed(propagate(request, t));
                }
            }
        }

        try {
            response.close();
        }
        catch (Throwable t) {
            if (cause == null) {
                cause = propagate(request, t);
            }
            else {
                cause.addSuppressed(propagate(request, t));
            }
        }

        return cause;
    }

    private static class StreamingIterator<T>
            implements StreamingResponseIterator<T>
    {
        private final Request request;
        private final StreamingResponse response;
        private final JsonCodecParser<T> jsonCodecParser;

        private StreamingIterator(Request request, StreamingResponse response, JsonCodecParser<T> jsonCodecParser)
        {
            this.request = request;
            this.response = requireNonNull(response, "response is null");
            this.jsonCodecParser = requireNonNull(jsonCodecParser, "parser is null");
        }

        @Override
        public int getStatusCode()
        {
            return response.getStatusCode();
        }

        @Override
        public ListMultimap<HeaderName, String> getHeaders()
        {
            return response.getHeaders();
        }

        @Override
        public long getBytesRead()
        {
            return response.getBytesRead();
        }

        @Override
        public InputStream getInputStream()
                throws IOException
        {
            return response.getInputStream();
        }

        @Override
        public void close()
        {
            RuntimeException exception = cleanup(request, jsonCodecParser, response, null);
            if (exception != null) {
                throw exception;
            }
        }

        @Override
        public boolean hasNext()
        {
            boolean hasNext = jsonCodecParser.hasCurrentToken() && (jsonCodecParser.currentToken() != JsonToken.END_ARRAY);
            if (!hasNext) {
                close();
            }
            return hasNext;
        }

        @Override
        public T next()
        {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            try {
                T value = jsonCodecParser.readValue();
                jsonCodecParser.nextToken();
                return value;
            }
            catch (IOException e) {
                throw cleanup(request, jsonCodecParser, response, new UncheckedIOException(e));
            }
        }
    }
}
