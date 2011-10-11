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
    private boolean disposed = false;

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
        Preconditions.checkState(!disposed, "connection closed");
        return statusCode;
    }

    public String getStatusMessage()
    {
        Preconditions.checkState(!disposed, "connection closed");
        return statusMessage;
    }

    public String getHeader(String name)
    {
        Preconditions.checkState(!disposed, "connection closed");

        List<String> values = getHeaders().get(name);
        if (values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    public ListMultimap<String, String> getHeaders()
    {
        Preconditions.checkState(!disposed, "connection closed");

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
        Preconditions.checkState(!disposed, "connection closed");

        if (inputStream == null) {
            return 0;
        }
        return inputStream.getCount();
    }

    public InputStream getInputStream()
            throws IOException
    {
        Preconditions.checkState(!disposed, "connection closed");

        return getInputStreamInternal();
    }

    private InputStream getInputStreamInternal()
            throws IOException
    {
        if (inputStream == null) {
            // Yes, the URL APIs are this dumb.
            IOException problem = null;
            InputStream in = null;
            try {
                in = connection.getInputStream();
            }
            catch (IOException e) {
                problem = e;
            }

            if (in == null) {
                in = connection.getErrorStream();
            }
            if (in == null) {
                throw new IOException("No input stream", problem);
            }
            inputStream = new CountingInputStream(in);
        }
        return inputStream;
    }

    /**
     * This buffer is shared by all threads for the dispose process.
     * This is ok since no one ever reads from this buffer.
     */
    private final static byte[] junk = new byte[4096];

    public void dispose()
    {
        if (disposed) {
            return;
        }

        InputStream inputStream = null;
        try {
            // consume all input so connection can be reused
            inputStream = getInputStreamInternal();
            while (inputStream.read(junk) >= 0) {
            }
        }
        catch (IOException ignored) {
        }
        finally {
            Closeables.closeQuietly(inputStream);
            this.inputStream = null;
            disposed = true;
        }
    }

    static void dispose(Response response)
    {
        if (response != null) {
            response.dispose();
        }
    }
}
