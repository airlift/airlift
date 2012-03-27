package com.proofpoint.http.client;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.CharStreams;
import com.proofpoint.http.client.FullJsonResponseHandler.JsonResponse;
import com.proofpoint.json.JsonCodec;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URI;
import java.util.List;

public class FullJsonResponseHandler<T> implements ResponseHandler<JsonResponse<T>, RuntimeException>
{
    public static <T> FullJsonResponseHandler<T> createFullJsonResponseHandler(JsonCodec<T> jsonCodec)
    {
        return new FullJsonResponseHandler<T>(jsonCodec);
    }

    private final JsonCodec<T> jsonCodec;

    private FullJsonResponseHandler(JsonCodec<T> jsonCodec)
    {
        this.jsonCodec = jsonCodec;
    }

    @Override
    public RuntimeException handleException(Request request, Exception exception)
    {
        if (exception instanceof ConnectException) {
            return new RuntimeException("Server refused connection: " + request.getUri().toASCIIString());
        }
        return Throwables.propagate(exception);
    }

    @Override
    public JsonResponse<T> handle(Request request, Response response)
    {
        String contentType = response.getHeader("Content-Type");
        if (!"application/json".equals(contentType)) {
            return new JsonResponse<T>(response.getStatusCode(), response.getStatusMessage(), response.getHeaders());
        }
        try {
            String json = CharStreams.toString(new InputStreamReader(response.getInputStream(), Charsets.UTF_8));
            T value = jsonCodec.fromJson(json);

            return new JsonResponse<T>(response.getStatusCode(), response.getStatusMessage(), response.getHeaders(), value);
        }
        catch (IOException e) {
            throw new RuntimeException("Error reading JSON response from server", e);
        }
    }

    public static class JsonResponse<T>
    {
        private final int statusCode;
        private final String statusMessage;
        private final ListMultimap<String, String> headers;
        private final boolean hasValue;
        private final T value;

        public JsonResponse(int statusCode, String statusMessage, ListMultimap<String, String> headers)
        {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.headers = ImmutableListMultimap.copyOf(headers);

            this.hasValue = false;
            this.value = null;
        }

        public JsonResponse(int statusCode, String statusMessage, ListMultimap<String, String> headers, T value)
        {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.headers = ImmutableListMultimap.copyOf(headers);

            this.hasValue = true;
            this.value = value;
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

        public boolean hasValue()
        {
            return hasValue;
        }

        public T getValue()
        {
            Preconditions.checkState(hasValue, "Response does not contain a JSON value");
            return value;
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("JsonResponse");
            sb.append("{statusCode=").append(statusCode);
            sb.append(", statusMessage='").append(statusMessage).append('\'');
            sb.append(", headers=").append(headers);
            sb.append(", hasValue=").append(hasValue);
            sb.append(", value=").append(value);
            sb.append('}');
            return sb.toString();
        }
    }
}
