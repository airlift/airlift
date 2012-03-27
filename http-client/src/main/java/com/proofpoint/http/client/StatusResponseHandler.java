package com.proofpoint.http.client;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;

import java.net.ConnectException;
import java.net.URI;
import java.util.List;

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
    public RuntimeException handleException(Request request, Exception exception)
    {
        if (exception instanceof ConnectException) {
            return new RuntimeException("Server refused connection: " + request.getUri().toASCIIString());
        }
        return Throwables.propagate(exception);
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
        private final ListMultimap<String, String> headers;

        public StatusResponse(int statusCode, String statusMessage, ListMultimap<String, String> headers)
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
