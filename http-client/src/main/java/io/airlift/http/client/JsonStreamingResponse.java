package io.airlift.http.client;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import io.airlift.json.JsonCodec;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;
import static io.airlift.http.client.JsonResponseHandler.validateResponse;
import static java.util.Objects.requireNonNull;

public class JsonStreamingResponse<T>
{
    private final ObjectReader objectReader;
    private final Request request;
    private final ResponseSupplier responseSupplier;
    private final Set<Integer> successfulResponseCodes;
    private final ObjectMapper objectMapper;
    private CloseableResponse response;

    public interface ResponseSupplier
    {
        CloseableResponse executeStreaming(Request request)
                throws Exception;
    }

    public static <T> JsonStreamingResponse<T> createJsonStreamingResponse(Request request, JsonCodec<T> codec, ResponseSupplier responseSupplier)
    {
        return new JsonStreamingResponse<>(request, codec, responseSupplier, 200, 201, 202, 203, 204, 205, 206);
    }

    public static <T> JsonStreamingResponse<T> createJsonStreamingResponse(Request request, JsonCodec<T> codec, ResponseSupplier responseSupplier, int firstSuccessfulResponseCode, int... otherSuccessfulResponseCodes)
    {
        return new JsonStreamingResponse<>(request, codec, responseSupplier, firstSuccessfulResponseCode, otherSuccessfulResponseCodes);
    }

    public static <T> Iterator<T> startJsonStreamingResponse(Request request, JsonCodec<T> codec, ResponseSupplier responseSupplier)
    {
        return createJsonStreamingResponse(request, codec, responseSupplier).start();
    }

    public static <T> Iterator<T> startJsonStreamingResponse(Request request, JsonCodec<T> codec, ResponseSupplier responseSupplier, int firstSuccessfulResponseCode, int... otherSuccessfulResponseCodes)
    {
        return createJsonStreamingResponse(request, codec, responseSupplier, firstSuccessfulResponseCode, otherSuccessfulResponseCodes).start();
    }

    private JsonStreamingResponse(Request request, JsonCodec<T> codec, ResponseSupplier responseSupplier, int firstSuccessfulResponseCode, int... otherSuccessfulResponseCodes)
    {
        this.request = requireNonNull(request, "request is null");
        this.responseSupplier = requireNonNull(responseSupplier, "responseSupplier is null");

        objectMapper = codec.mapper();
        JavaType javaType = objectMapper.getTypeFactory().constructType(codec.getType());
        objectReader = objectMapper.readerFor(javaType);

        successfulResponseCodes = ImmutableSet.<Integer>builder().add(firstSuccessfulResponseCode).addAll(Ints.asList(otherSuccessfulResponseCodes)).build();
    }

    public interface CloseableIterator<T>
            extends Iterator<T>, Closeable
    {
        @Override
        void close();
    }

    public CloseableIterator<T> start()
    {
        checkState(response == null, "Already started");

        try {
            response = responseSupplier.executeStreaming(request);
        }
        catch (Exception e) {
            throw cleanup(null, new RuntimeException("Could not start request: " + request, e));
        }

        try {
            validateResponse(request, response, successfulResponseCodes);
        }
        catch (UnexpectedResponseException e) {
            throw cleanup(null, e);
        }

        JsonParser parser = null;
        try {
            parser = objectMapper.createParser(new InputStreamReader(response.getInputStream(), StandardCharsets.UTF_8));
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new UnexpectedResponseException("Expected JSON array", request, response);
            }
            parser.nextToken();
        }
        catch (IOException e) {
            throw cleanup(parser, new UncheckedIOException(e));
        }

        return new StreamingIterator(parser);
    }

    private RuntimeException cleanup(JsonParser parser, RuntimeException cause)
    {
        if ((parser != null) && !parser.isClosed()) {
            try {
                parser.close();
            }
            catch (IOException e) {
                if (cause != null) {
                    cause.addSuppressed(e);
                }
                else {
                    cause = new UncheckedIOException(e);
                }
            }
            catch (Throwable t) {
                if (cause != null) {
                    cause.addSuppressed(t);
                }
                else {
                    cause = new RuntimeException(t);
                }
            }
        }

        try {
            if (response != null) {
                response.close();
                response = null;
            }
        }
        catch (Exception e) {
            if (cause != null) {
                cause.addSuppressed(e);
            }
            else {
                cause = new RuntimeException(e);
            }
        }

        return cause;
    }

    private class StreamingIterator
            implements CloseableIterator<T>
    {
        private final JsonParser parser;

        private StreamingIterator(JsonParser parser)
        {
            this.parser = requireNonNull(parser, "parser is null");
        }

        @Override
        public void close()
        {
            RuntimeException exception = cleanup(parser, null);
            if (exception != null) {
                throw exception;
            }
        }

        @Override
        public boolean hasNext()
        {
            boolean hasNext = (response != null) && parser.hasCurrentToken() && (parser.currentToken() != JsonToken.END_ARRAY);
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
                T value = objectReader.readValue(parser);
                parser.nextToken();
                return value;
            }
            catch (IOException e) {
                throw cleanup(parser, new UncheckedIOException(e));
            }
        }
    }
}
