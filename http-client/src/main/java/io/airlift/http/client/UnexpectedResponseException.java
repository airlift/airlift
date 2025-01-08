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
import jakarta.annotation.Nullable;

import java.net.URI;
import java.util.List;

import static com.google.common.base.MoreObjects.toStringHelper;

public class UnexpectedResponseException
        extends RuntimeException
{
    private final URI requestUri;
    private final String requestMethod;
    private final int statusCode;
    private final ListMultimap<HeaderName, String> headers;

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
        super(message);
        this.requestUri = request != null ? request.getUri() : null;
        this.requestMethod = request != null ? request.getMethod() : null;
        this.statusCode = statusCode;
        this.headers = ImmutableListMultimap.copyOf(headers);
    }

    public int getStatusCode()
    {
        return statusCode;
    }

    @Nullable
    public String getHeader(String name)
    {
        List<String> values = getHeaders().get(HeaderName.of(name));
        return values.isEmpty() ? null : values.get(0);
    }

    public List<String> getHeaders(String name)
    {
        return headers.get(HeaderName.of(name));
    }

    public ListMultimap<HeaderName, String> getHeaders()
    {
        return headers;
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("message", getLocalizedMessage())
                .add("request", requestMethod + " " + requestUri)
                .add("statusCode", statusCode)
                .add("headers", headers)
                .toString();
    }
}
