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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;
import io.opentelemetry.api.trace.SpanBuilder;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.util.Objects.requireNonNull;

public final class Request
{
    private final Optional<HttpVersion> httpVersion;
    private final URI uri;
    private final String method;
    private final ListMultimap<String, String> headers = MultimapBuilder
            .treeKeys(CASE_INSENSITIVE_ORDER)
            .arrayListValues()
            .build();
    private final Optional<Duration> requestTimeout;
    private final Optional<Duration> idleTimeout;
    private final BodyGenerator bodyGenerator;
    private final Optional<DataSize> maxContentLength;
    private final Optional<SpanBuilder> spanBuilder;
    private final boolean followRedirects;

    private Request(
            Optional<HttpVersion> httpVersion,
            URI uri,
            String method,
            ListMultimap<String, String> headers,
            Optional<Duration> requestTimeout,
            Optional<Duration> idleTimeout,
            BodyGenerator bodyGenerator,
            Optional<DataSize> maxContentLength,
            Optional<SpanBuilder> spanBuilder,
            boolean followRedirects)
    {
        requireNonNull(uri, "uri is null");
        checkArgument(uri.getHost() != null, "uri does not have a host: %s", uri);
        checkArgument(uri.getScheme() != null, "uri does not have a scheme: %s", uri);
        String scheme = uri.getScheme().toLowerCase();
        checkArgument("http".equals(scheme) || "https".equals(scheme), "uri scheme must be http or https: %s", uri);
        requireNonNull(method, "method is null");

        this.httpVersion = requireNonNull(httpVersion, "httpVersion is null");
        this.uri = validateUri(uri);
        this.method = method;
        this.headers.putAll(headers);
        this.requestTimeout = requireNonNull(requestTimeout, "requestTimeout is null");
        this.idleTimeout = requireNonNull(idleTimeout, "idleTimeout is null");
        this.bodyGenerator = bodyGenerator;
        this.maxContentLength = requireNonNull(maxContentLength, "maxContentLength is null");
        this.spanBuilder = requireNonNull(spanBuilder, "spanBuilder is null");
        this.followRedirects = followRedirects;
    }

    public static Request.Builder builder()
    {
        return new Builder();
    }

    public Optional<HttpVersion> getHttpVersion()
    {
        return httpVersion;
    }

    public URI getUri()
    {
        return uri;
    }

    public String getMethod()
    {
        return method;
    }

    public String getHeader(String name)
    {
        List<String> values = headers.get(name);
        if (!values.isEmpty()) {
            return values.getFirst();
        }
        return null;
    }

    public ListMultimap<String, String> getHeaders()
    {
        return headers;
    }

    public Optional<Duration> getRequestTimeout()
    {
        return requestTimeout;
    }

    public Optional<Duration> getIdleTimeout()
    {
        return idleTimeout;
    }

    public Optional<DataSize> getMaxContentLength()
    {
        return maxContentLength;
    }

    public BodyGenerator getBodyGenerator()
    {
        return bodyGenerator;
    }

    public Optional<SpanBuilder> getSpanBuilder()
    {
        return spanBuilder;
    }

    public boolean isFollowRedirects()
    {
        return followRedirects;
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("version", httpVersion.map(HttpVersion::name).orElse("unspecified"))
                .add("uri", uri)
                .add("method", method)
                .add("headers", headers)
                .add("timeout", requestTimeout.orElse(null))
                .add("idleTimeout", idleTimeout.orElse(null))
                .add("maxContentLength", maxContentLength)
                .add("bodyGenerator", bodyGenerator)
                .add("spanBuilder", spanBuilder.isPresent() ? "present" : "empty")
                .add("followRedirects", followRedirects)
                .toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof Request)) {
            return false;
        }
        Request r = (Request) o;
        return Objects.equals(httpVersion, r.httpVersion) &&
                Objects.equals(uri, r.uri) &&
                Objects.equals(method, r.method) &&
                Objects.equals(headers, r.headers) &&
                Objects.equals(requestTimeout, r.requestTimeout) &&
                Objects.equals(idleTimeout, r.idleTimeout) &&
                Objects.equals(maxContentLength, r.maxContentLength) &&
                Objects.equals(bodyGenerator, r.bodyGenerator) &&
                Objects.equals(spanBuilder, r.spanBuilder) &&
                Objects.equals(followRedirects, r.followRedirects);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(
                httpVersion,
                uri,
                method,
                headers,
                requestTimeout,
                idleTimeout,
                maxContentLength,
                bodyGenerator,
                spanBuilder,
                followRedirects);
    }

    public static final class Builder
    {
        public static Builder prepareHead()
        {
            return new Builder().setMethod("HEAD");
        }

        public static Builder prepareGet()
        {
            return new Builder().setMethod("GET");
        }

        public static Builder preparePost()
        {
            return new Builder().setMethod("POST");
        }

        public static Builder preparePut()
        {
            return new Builder().setMethod("PUT");
        }

        public static Builder prepareDelete()
        {
            return new Builder().setMethod("DELETE");
        }

        public static Builder preparePatch()
        {
            return new Builder().setMethod("PATCH");
        }

        public static Builder fromRequest(Request request)
        {
            Builder builder = new Builder()
                    .setUri(request.getUri())
                    .setMethod(request.getMethod())
                    .addHeaders(request.getHeaders())
                    .setBodyGenerator(request.getBodyGenerator())
                    .setSpanBuilder(request.getSpanBuilder().orElse(null))
                    .setFollowRedirects(request.isFollowRedirects())
                    .setVersion(request.getHttpVersion().orElse(null));

            request.getRequestTimeout().ifPresent(builder::setRequestTimeout);
            request.getIdleTimeout().ifPresent(builder::setIdleTimeout);
            request.getMaxContentLength().ifPresent(builder::setMaxContentLength);

            return builder;
        }

        private URI uri;
        private String method;
        private final ListMultimap<String, String> headers = ArrayListMultimap.create();
        private BodyGenerator bodyGenerator;
        private SpanBuilder spanBuilder;
        private Optional<HttpVersion> version = Optional.empty();
        private boolean followRedirects = true;
        private boolean preserveAuthorizationOnRedirect;
        private Optional<Duration> requestTimeout = Optional.empty();
        private Optional<Duration> idleTimeout = Optional.empty();
        private Optional<DataSize> maxContentLength = Optional.empty();

        public Builder setUri(URI uri)
        {
            this.uri = validateUri(uri);
            return this;
        }

        public Builder setMethod(String method)
        {
            this.method = method;
            return this;
        }

        public Builder setHeader(String name, String value)
        {
            this.headers.removeAll(name);
            this.headers.put(name, value);
            return this;
        }

        public Builder addHeader(String name, String value)
        {
            this.headers.put(name, value);
            return this;
        }

        public Builder addHeaders(Multimap<String, String> headers)
        {
            this.headers.putAll(headers);
            return this;
        }

        public Builder setBodyGenerator(BodyGenerator bodyGenerator)
        {
            this.bodyGenerator = bodyGenerator;
            return this;
        }

        public Builder setSpanBuilder(SpanBuilder spanBuilder)
        {
            this.spanBuilder = spanBuilder;
            return this;
        }

        public Builder setFollowRedirects(boolean followRedirects)
        {
            this.followRedirects = followRedirects;
            return this;
        }

        public Builder setVersion(HttpVersion version)
        {
            this.version = Optional.ofNullable(version);
            return this;
        }

        public Builder setRequestTimeout(Duration timeout)
        {
            this.requestTimeout = Optional.ofNullable(timeout);
            return this;
        }

        public Builder setIdleTimeout(Duration timeout)
        {
            this.idleTimeout = Optional.ofNullable(timeout);
            return this;
        }

        public Builder setMaxContentLength(DataSize maxContentLength)
        {
            this.maxContentLength = Optional.ofNullable(maxContentLength);
            return this;
        }

        public Request build()
        {
            return new Request(
                    version,
                    uri,
                    method,
                    headers,
                    requestTimeout,
                    idleTimeout,
                    bodyGenerator,
                    maxContentLength,
                    Optional.ofNullable(spanBuilder),
                    followRedirects);
        }
    }

    private static URI validateUri(URI uri)
    {
        checkArgument(uri.getPort() != 0, "Cannot make requests to HTTP port 0");
        return uri;
    }
}
