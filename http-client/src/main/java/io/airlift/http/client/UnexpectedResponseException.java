/*
 * Copyright 2010 Proofpoint, Inc.
 *
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
import com.google.common.net.MediaType;
import jakarta.annotation.Nullable;

import java.net.URI;
import java.nio.charset.Charset;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.MoreObjects.toStringHelper;
import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.HeaderNames.RETRY_AFTER;
import static io.airlift.http.client.ResponseHandlerUtils.sanitizeUri;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class UnexpectedResponseException
        extends RuntimeException
{
    public static final int DEFAULT_MAX_RESPONSE_BODY_BYTES = 64 * 1024;

    private final URI requestUri;
    private final String requestMethod;
    private final int statusCode;
    private final ListMultimap<HeaderName, String> headers;
    private final byte[] responseBody;
    private final boolean responseBodyTruncated;

    public UnexpectedResponseException(Request request, Response response)
    {
        this("HTTP " + response.getStatusCode(),
                request,
                response.getStatusCode(),
                ImmutableListMultimap.copyOf(response.getHeaders()));
    }

    public UnexpectedResponseException(String message, Request request, Response response)
    {
        this(message,
                request,
                response.getStatusCode(),
                ImmutableListMultimap.copyOf(response.getHeaders()));
    }

    public UnexpectedResponseException(String message, Request request, int statusCode, ListMultimap<HeaderName, String> headers)
    {
        this(builder(message, statusCode)
                .setRequest(request)
                .setHeaders(headers));
    }

    protected UnexpectedResponseException(Builder builder)
    {
        super(builder.message);
        if (builder.cause != null) {
            initCause(builder.cause);
        }
        this.requestUri = builder.request != null ? sanitizeUri(builder.request.getUri()) : null;
        this.requestMethod = builder.request != null ? builder.request.getMethod() : null;
        this.statusCode = builder.statusCode;
        this.headers = ImmutableListMultimap.copyOf(builder.headers);
        this.responseBody = builder.responseBody.clone();
        this.responseBodyTruncated = builder.responseBodyTruncated;
    }

    public static Builder builder(String message, int statusCode)
    {
        return new Builder(message, statusCode);
    }

    @Nullable
    public URI getRequestUri()
    {
        return requestUri;
    }

    @Nullable
    public String getRequestMethod()
    {
        return requestMethod;
    }

    public int getStatusCode()
    {
        return statusCode;
    }

    @Deprecated
    @Nullable
    public String getHeader(String name)
    {
        return getHeader(HeaderName.of(name));
    }

    @Nullable
    public String getHeader(HeaderName name)
    {
        List<String> values = getHeaders().get(name);
        return values.isEmpty() ? null : values.getFirst();
    }

    @Deprecated
    public List<String> getHeaders(String name)
    {
        return getHeaders(HeaderName.of(name));
    }

    public List<String> getHeaders(HeaderName name)
    {
        return headers.get(name);
    }

    public ListMultimap<HeaderName, String> getHeaders()
    {
        return headers;
    }

    /**
     * The delay the server asked for through {@code Retry-After}, given as delay-seconds or as an HTTP-date
     * (RFC 9110) relative to {@code now}. Empty when the header is absent, negative, or unparseable.
     */
    public Optional<Duration> getRetryAfter(Instant now)
    {
        String value = getHeader(RETRY_AFTER);
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        try {
            long seconds = Long.parseLong(trimmed);
            if (seconds < 0) {
                // delay-seconds is a non-negative integer
                return Optional.empty();
            }
            return Optional.of(seconds > Long.MAX_VALUE / 1000 ? Duration.ofMillis(Long.MAX_VALUE) : Duration.ofSeconds(seconds));
        }
        catch (NumberFormatException _) {
            // not delay-seconds
        }
        try {
            Instant at = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return Optional.of(at.isBefore(now) ? Duration.ZERO : Duration.between(now, at));
        }
        catch (DateTimeException _) {
            return Optional.empty();
        }
    }

    public Optional<String> getContentType()
    {
        return Optional.ofNullable(getHeader(CONTENT_TYPE));
    }

    public byte[] getResponseBodyBytes()
    {
        return responseBody.clone();
    }

    public String getResponseBody()
    {
        return new String(responseBody, getResponseCharset());
    }

    public boolean isResponseBodyTruncated()
    {
        return responseBodyTruncated;
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("message", getLocalizedMessage())
                .add("request", requestMethod + " " + requestUri)
                .add("statusCode", statusCode)
                .toString();
    }

    private Charset getResponseCharset()
    {
        try {
            return getContentType()
                    .map(MediaType::parse)
                    .map(MediaType::charset)
                    .flatMap(optional -> optional.toJavaUtil())
                    .orElse(UTF_8);
        }
        catch (RuntimeException _) {
            return UTF_8;
        }
    }

    public static final class Builder
    {
        private final String message;
        private final int statusCode;
        private Request request;
        private ListMultimap<HeaderName, String> headers = ImmutableListMultimap.of();
        private byte[] responseBody = new byte[0];
        private boolean responseBodyTruncated;
        private Throwable cause;

        private Builder(String message, int statusCode)
        {
            this.message = message;
            this.statusCode = statusCode;
        }

        public Builder setRequest(@Nullable Request request)
        {
            this.request = request;
            return this;
        }

        public Builder setHeaders(ListMultimap<HeaderName, String> headers)
        {
            this.headers = requireNonNull(headers, "headers is null");
            return this;
        }

        /**
         * The response body kept for diagnostics, and whether it was cut short of the full body.
         */
        public Builder setResponseBody(byte[] responseBody, boolean truncated)
        {
            this.responseBody = requireNonNull(responseBody, "responseBody is null");
            this.responseBodyTruncated = truncated;
            return this;
        }

        public Builder setCause(@Nullable Throwable cause)
        {
            this.cause = cause;
            return this;
        }

        public UnexpectedResponseException build()
        {
            return new UnexpectedResponseException(this);
        }
    }
}
