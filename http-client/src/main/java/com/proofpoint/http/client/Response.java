package com.proofpoint.http.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map.Entry;

public class Response
{
    private final HttpURLConnection connection;
    private final int statusCode;
    private final String statusMessage;
    private ListMultimap<String, String> headers;

    public Response(HttpURLConnection connection)
            throws IOException
    {
        Preconditions.checkNotNull(connection, "connection is null");
        this.connection = connection;
        this.statusCode = connection.getResponseCode();
        this.statusMessage = connection.getResponseMessage();
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
        if (headers == null) {
            ImmutableListMultimap.Builder<String, String> builder = ImmutableListMultimap.builder();
            for (Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
                if (entry.getKey() != null) { // HttpUrlConnection returns an header entry for the status line with null key
                    builder.putAll(entry.getKey(), Lists.reverse(entry.getValue()));
                }
            }
            this.headers = builder.build();
        }
        return headers;
    }

    public InputStream getInputStream()
            throws IOException
    {
        // Yes, the URL APIs are this dumb.
        if (statusCode < 400) {
            return connection.getInputStream();
        }
        else {
            return connection.getErrorStream();
        }
    }
}
