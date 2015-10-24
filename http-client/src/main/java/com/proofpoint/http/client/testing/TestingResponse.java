package com.proofpoint.http.client.testing;

import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.CountingInputStream;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.proofpoint.http.client.HttpStatus;
import com.proofpoint.http.client.Response;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

public class TestingResponse
        implements Response
{
    private final HttpStatus status;
    private final ListMultimap<String, String> headers;
    private final CountingInputStream countingInputStream;

    /**
     * @deprecated use {@link TestingResponse#mockResponse()}{@code .status(status).headers(header).body(bytes).build()}
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public TestingResponse(HttpStatus status, ListMultimap<String, String> headers, byte[] bytes)
    {
        this(status, headers, new ByteArrayInputStream(checkNotNull(bytes, "bytes is null")));
    }

    /**
     * @deprecated use {@link TestingResponse#mockResponse()}{@code .status(status).headers(header).body(input).build()}
     */
    @Deprecated
    public TestingResponse(HttpStatus status, ListMultimap<String, String> headers, InputStream input)
    {
        this.status = requireNonNull(status, "status is null");
        this.headers = ImmutableListMultimap.copyOf(requireNonNull(headers, "headers is null"));
        this.countingInputStream = new CountingInputStream(requireNonNull(input, "input is null"));
    }

    @Override
    public int getStatusCode()
    {
        return status.code();
    }

    @Override
    public String getStatusMessage()
    {
        return status.reason();
    }

    @Override
    public String getHeader(String name)
    {
        List<String> list = getHeaders().get(name);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public ListMultimap<String, String> getHeaders()
    {
        return headers;
    }

    @Override
    public long getBytesRead()
    {
        return countingInputStream.getCount();
    }

    @Override
    public InputStream getInputStream()
            throws IOException
    {
        return countingInputStream;
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper(this)
                .add("statusCode", getStatusCode())
                .add("statusMessage", getStatusMessage())
                .add("headers", getHeaders())
                .toString();
    }

    public static ListMultimap<String, String> contentType(MediaType type)
    {
        return ImmutableListMultimap.of(HttpHeaders.CONTENT_TYPE, type.toString());
    }

    /**
     * Returns a response with the specified status.
     */
    @SuppressWarnings("deprecation")
    public static Response mockResponse(HttpStatus status)
    {
        return new TestingResponse(status, ImmutableListMultimap.<String, String>of(), new byte[0]);
    }

    /**
     * Returns a response, encoding the provided content in UTF-8.
     *
     * @param status the status
     * @param type the type for the Content-Type: header
     * @param content the content for the body
     */
    @SuppressWarnings("deprecation")
    public static Response mockResponse(HttpStatus status, MediaType type, String content)
    {
        return new TestingResponse(status, contentType(type), content.getBytes(Charsets.UTF_8));
    }

    /**
     * Returns a new builder.
     */
    public static Builder mockResponse()
    {
        return new Builder();
    }

    /**
     * A builder for creating {@link TestingResponse} instances.
     */
    public static class Builder
    {
        private static final byte[] ZERO_LENGTH_BYTES = new byte[0];

        private HttpStatus status;
        private final ListMultimap<String, String> headers = ArrayListMultimap.create();
        private byte[] bytes;
        private InputStream inputStream;

        private Builder()
        {
        }

        /**
         * Sets the response's status.
         *
         * If this method is not called, the builder uses {@link HttpStatus#OK}
         * if the body is set or {@link HttpStatus#NO_CONTENT} if the body is not set.
         */
        public Builder status(HttpStatus status)
        {
            checkState(this.status == null, "status is already set");
            this.status = requireNonNull(status, "status is null");
            return this;
        }

        /**
         * Adds a header to the response. May be called multiple times.
         */
        public Builder header(String field, String value)
        {
            headers.put(requireNonNull(field, "field is null"), requireNonNull(value, "value is null"));
            return this;
        }

        /**
         * Adds headers to the response. May be called multiple times.
         */
        public Builder headers(ListMultimap<String, String> headers)
        {
            this.headers.putAll(requireNonNull(headers, "headers is null"));
            return this;
        }

        /**
         * Sets the response's body to a byte array.
         */
        public Builder body(byte[] bytes)
        {
            requireNonNull(bytes, "bytes is null");
            checkState(this.bytes == null && this.inputStream == null, "body is already set");
            this.bytes = Arrays.copyOf(bytes, bytes.length);
            return this;
        }

        /**
         * Sets the response's body to an {@link InputStream}.
         */
        public Builder body(InputStream inputStream)
        {
            checkState(this.bytes == null && this.inputStream == null, "body is already set");
            this.inputStream = requireNonNull(inputStream, "inputStream is null");
            return this;
        }

        /**
         * Returns a newly created TestingResponse.
         */
        @SuppressWarnings("deprecation")
        public TestingResponse build()
        {
            if (status == null) {
                if (bytes == null && this.inputStream == null) {
                    status = HttpStatus.NO_CONTENT;
                }
                else {
                    status = HttpStatus.OK;
                }
            }

            if (inputStream != null) {
                return new TestingResponse(status, headers, inputStream);
            }

            return new TestingResponse(status, headers, firstNonNull(bytes, ZERO_LENGTH_BYTES));
        }
    }
}
