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
import io.airlift.http.client.StatusResponseHandler.StatusResponse;

import javax.annotation.Nullable;
import java.util.List;

import static io.airlift.http.client.ResponseHandlerUtils.propagate;

public class StatusResponseHandler implements ResponseHandler<StatusResponse, RuntimeException>
{
    private static final StatusResponseHandler statusResponseHandler = new StatusResponseHandler();

    public static StatusResponseHandler createStatusResponseHandler()
    {
        return statusResponseHandler;
    }

    private StatusResponseHandler()
    {
    }

    @Override
    public StatusResponse handleException(Request request, Exception exception)
    {
        throw propagate(request, exception);
    }

    @Override
    public StatusResponse handle(Request request, Response response)
    {
        return new StatusResponse(response.getStatusCode(), response.getStatusMessage(), response.getHeaders());
    }

    public static class StatusResponse
    {
        private final int statusCode;
        private final String statusMessage;
        private final ListMultimap<HeaderName, String> headers;

        public StatusResponse(int statusCode, String statusMessage, ListMultimap<HeaderName, String> headers)
        {
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
    }
}
