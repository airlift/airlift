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
import io.airlift.http.client.StringResponseHandler.StringResponse;

import javax.annotation.Nullable;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.airlift.http.client.ResponseHandlerUtils.propagate;
import static io.airlift.http.client.ResponseHandlerUtils.readResponseBytes;
import static java.nio.charset.StandardCharsets.UTF_8;

public class StringResponseHandler
        implements ResponseHandler<StringResponse, RuntimeException>
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
        throw propagate(request, exception);
    }

    @Override
    public StringResponse handle(Request request, Response response)
    {
        byte[] bytes = readResponseBytes(request, response);

        Charset charset = Optional.ofNullable(response.getHeader(CONTENT_TYPE))
                .map(MediaType::parse)
                .flatMap(mediaType -> mediaType.charset().toJavaUtil())
                .orElse(UTF_8);

        return new StringResponse(response.getStatusCode(), response.getHeaders(), new String(bytes, charset));
    }

    public static class StringResponse
    {
        private final int statusCode;
        private final ListMultimap<HeaderName, String> headers;
        private final String body;

        public StringResponse(int statusCode, ListMultimap<HeaderName, String> headers, String body)
        {
            this.statusCode = statusCode;
            this.headers = ImmutableListMultimap.copyOf(headers);
            this.body = body;
        }

        public int getStatusCode()
        {
            return statusCode;
        }

        public String getBody()
        {
            return body;
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
