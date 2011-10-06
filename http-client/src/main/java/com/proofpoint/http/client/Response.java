package com.proofpoint.http.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.google.common.io.CountingInputStream;

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
    private CountingInputStream inputStream;

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

    public long getBytesRead()
    {
        if (inputStream == null) {
            return 0;
        }
        return inputStream.getCount();
    }

    public InputStream getInputStream()
            throws IOException
    {
        if (inputStream == null) {
            // Yes, the URL APIs are this dumb.
            if (statusCode < 400) {
                inputStream = new CountingInputStream(connection.getInputStream());
            }
            else {
                inputStream = new CountingInputStream(connection.getErrorStream());
            }
        }
        return inputStream;
    }

    static void dispose(Response response)
    {
        if (response != null) {
            Closeables.closeQuietly(response.inputStream);
            response.inputStream = null;
        }
    }
}
