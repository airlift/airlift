package com.proofpoint.http.client;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;

import java.util.List;

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
        final StringBuilder sb = new StringBuilder();
        sb.append("UnexpectedResponseException");
        sb.append("{request=").append(request);
        sb.append(", statusCode=").append(statusCode);
        sb.append(", statusMessage='").append(statusMessage).append('\'');
        sb.append(", headers=").append(headers);
        sb.append('}');
        return sb.toString();
    }
}
