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

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;

import java.net.URI;
import java.util.List;
import java.util.Map.Entry;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Objects.equal;

@Beta
public class Request
{
    private final URI uri;
    private final String method;
    private final ListMultimap<String, String> headers;
    private final BodyGenerator bodyGenerator;

    public Request(URI uri, String method, ListMultimap<String, String> headers, BodyGenerator bodyGenerator)
    {
        Preconditions.checkNotNull(uri, "uri is null");
        Preconditions.checkNotNull(uri.getHost(), "uri does not have a host: %s", uri);
        Preconditions.checkNotNull(method, "method is null");
        Preconditions.checkNotNull(uri.getScheme(), "uri does not have a scheme: %s", uri);
        String scheme = uri.getScheme().toLowerCase();
        Preconditions.checkArgument(!"http".equals(scheme) || !"https".equals(scheme), "uri scheme must be http or https: %s", uri);

        this.uri = validateUri(uri);
        this.method = method;
        this.headers = ImmutableListMultimap.copyOf(headers);
        this.bodyGenerator = bodyGenerator;
    }

    public static Request.Builder builder() {
        return new Builder();
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
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        }
        return null;
    }

    public ListMultimap<String, String> getHeaders()
    {
        return headers;
    }

    public BodyGenerator getBodyGenerator()
    {
        return bodyGenerator;
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("uri", uri)
                .add("method", method)
                .add("headers", headers)
                .add("bodyGenerator", bodyGenerator)
                .toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof Request)) {
            return false;
        }
        Request r = (Request) o;
        return equal(uri, r.uri) &&
                equal(method, r.method) &&
                equal(headers, r.headers) &&
                equal(bodyGenerator, r.bodyGenerator);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(uri, method, headers, bodyGenerator);
    }

    @Beta
    public static class Builder
    {
        public static Builder prepareHead() {
            return new Builder().setMethod("HEAD");
        }

        public static Builder prepareGet() {
            return new Builder().setMethod("GET");
        }

        public static Builder preparePost() {
            return new Builder().setMethod("POST");
        }

        public static Builder preparePut() {
            return new Builder().setMethod("PUT");
        }

        public static Builder prepareDelete() {
            return new Builder().setMethod("DELETE");
        }

        public static Builder fromRequest(Request request) {
            Builder requestBuilder = new Builder();
            requestBuilder.setMethod(request.getMethod());
            requestBuilder.setBodyGenerator(request.getBodyGenerator());
            requestBuilder.setUri(request.getUri());

            for (Entry<String, String> entry : request.getHeaders().entries()) {
                requestBuilder.addHeader(entry.getKey(), entry.getValue());
            }
            return requestBuilder;
        }

        private URI uri;
        private String method;
        private final ListMultimap<String, String> headers = ArrayListMultimap.create();
        private BodyGenerator bodyGenerator;

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

        public Builder setBodyGenerator(BodyGenerator bodyGenerator)
        {
            this.bodyGenerator = bodyGenerator;
            return this;
        }

        public Request build() {
            return new Request(uri, method, headers, bodyGenerator);
        }
    }

    private static URI validateUri(URI uri)
    {
        Preconditions.checkArgument(uri.getPort() != 0, "Cannot make requests to HTTP port 0");
        return uri;
    }
}
