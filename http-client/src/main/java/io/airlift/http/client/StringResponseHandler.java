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

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.ByteStreams;
import com.google.common.net.MediaType;
import io.airlift.http.client.StringResponseHandler.StringResponse;

import java.io.IOException;
import java.net.ConnectException;
import java.util.List;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;

public class StringResponseHandler implements ResponseHandler<StringResponse, RuntimeException>
{
    private static final StringResponseHandler STRING_RESPONSE_HANDLER = new StringResponseHandler();

    public static StringResponseHandler createStringResponseHandler()
    {
        return STRING_RESPONSE_HANDLER;
    }

    private StringResponseHandler()
    {
    }

    @Override
    public StringResponse handleException(Request request, Exception exception)
    {
        if (exception instanceof ConnectException) {
            throw new RuntimeException("Server refused connection: " + request.getUri().toASCIIString(), exception);
        }
        if (exception instanceof RuntimeException) {
            throw (RuntimeException) exception;
        }
        throw new RuntimeException(exception);
    }

    @Override
    public StringResponse handle(Request request, Response response)
    {
        try {
            String contentType = response.getHeader(CONTENT_TYPE);

            if (contentType != null) {
                MediaType mediaType = MediaType.parse(contentType);
                return new StringResponse(
                        response.getStatusCode(),
                        response.getStatusMessage(),
                        response.getHeaders(),
                        new String(ByteStreams.toByteArray(response.getInputStream()), mediaType.charset().or(Charsets.UTF_8)));
            }

            return new StringResponse(
                    response.getStatusCode(),
                    response.getStatusMessage(),
                    response.getHeaders(),
                    new String(ByteStreams.toByteArray(response.getInputStream()), Charsets.UTF_8));
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public static class StringResponse
    {
        private final int statusCode;
        private final String statusMessage;
        private final ListMultimap<String, String> headers;
        private final String body;

        public StringResponse(int statusCode, String statusMessage, ListMultimap<String, String> headers, String body)
        {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.headers = ImmutableListMultimap.copyOf(headers);
            this.body = body;
        }

        public int getStatusCode()
        {
            return statusCode;
        }

        public String getStatusMessage()
        {
            return statusMessage;
        }

        public String getBody()
        {
            return body;
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
    }
}
