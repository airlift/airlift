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
package com.proofpoint.http.client;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;

import java.util.List;

import static com.google.common.base.MoreObjects.toStringHelper;

@Beta
public class UnexpectedResponseException extends RuntimeException
{
    private final Request request;
    private final int statusCode;
    private final String statusMessage;
    private final ListMultimap<String, String> headers;

    public UnexpectedResponseException(Request request, Response response)
    {
        this(String.format("%d: %s", response.getStatusCode(), response.getStatusMessage()),
                request,
                response.getStatusCode(),
                response.getStatusMessage(),
                ImmutableListMultimap.copyOf(response.getHeaders()));
    }

    public UnexpectedResponseException(String message, Request request, Response response)
    {
        this(message,
                request,
                response.getStatusCode(),
                response.getStatusMessage(),
                ImmutableListMultimap.copyOf(response.getHeaders()));
    }

    public UnexpectedResponseException(String message, Request request, int statusCode, String statusMessage, ListMultimap<String, String> headers)
    {
        super(message);
        this.request = request;
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.headers = ImmutableListMultimap.copyOf(headers);
    }

    public int getStatusCode()
    {
        return statusCode;
    }

    public String getStatusMessage()
    {
        return statusMessage;
    }

    public String getHeader(String name)
    {
        List<String> values = getHeaders().get(name);
        if (values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    public ListMultimap<String, String> getHeaders()
    {
        return headers;
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("request", request)
                .add("statusCode", statusCode)
                .add("statusMessage", statusMessage)
                .add("headers", headers)
                .toString();
    }
}
