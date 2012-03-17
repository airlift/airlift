package com.proofpoint.http.client;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.google.common.io.CountingInputStream;
import com.google.common.io.CountingOutputStream;
import com.proofpoint.units.Duration;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.util.List;
import java.util.Map.Entry;

@Beta
@Deprecated
public class JavaUrlHttpClient implements HttpClient
{
    private final RequestStats stats = new RequestStats();
    private final Duration connectTimeout;
    private final Duration readTimeout;

    @Inject
    public JavaUrlHttpClient(HttpClientConfig config)
    {
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(config.getConnectTimeout(), "config.getConnectTimeout() is null");
        Preconditions.checkNotNull(config.getReadTimeout(), "config.getReadTimeout() is null");

        connectTimeout = config.getConnectTimeout();
        readTimeout = config.getReadTimeout();
    }

    @Override
    @Managed
    @Flatten
    public RequestStats getStats()
    {
        return stats;
    }

    @Override
    public <T, E extends Exception> T execute(final Request request, final ResponseHandler<T, E> responseHandler)
            throws E
    {
        Preconditions.checkNotNull(request, "request is null");
        Preconditions.checkNotNull(responseHandler, "responseHandler is null");

        long requestStart = System.nanoTime();

        CountingOutputStream outputStream = null;
        JavaUrlResponse response = null;
        try {
            try {
                HttpURLConnection urlConnection = (HttpURLConnection) request.getUri().toURL().openConnection(Proxy.NO_PROXY);

                urlConnection.setConnectTimeout((int) connectTimeout.toMillis());
                urlConnection.setReadTimeout((int) readTimeout.toMillis());

                urlConnection.setRequestMethod(request.getMethod());
                for (Entry<String, String> entry : request.getHeaders().entries()) {
                    urlConnection.addRequestProperty(entry.getKey(), entry.getValue());
                }

                if (request.getBodyGenerator() != null) {
                    urlConnection.setDoOutput(true);
                    urlConnection.setChunkedStreamingMode(4096);
                    outputStream = new CountingOutputStream(urlConnection.getOutputStream());
                    request.getBodyGenerator().write(outputStream);
                    outputStream.close();
                }

                // Get the response
                response = new JavaUrlResponse(urlConnection);
            }
            catch (Exception e) {
                throw responseHandler.handleException(request, e);
            }

            Duration requestProcessingTime = Duration.nanosSince(requestStart);
            long responseStart = System.nanoTime();
            try {
                return responseHandler.handle(request, response);
            }
            catch (Exception e) {
                // Do not ask the handler to "handle" an exception it produced
                try {
                    throw (E) e;
                }
                catch (ClassCastException classCastException) {
                }
                throw responseHandler.handleException(request, e);
            }
            finally {
                Duration responseProcessingTime = Duration.nanosSince(responseStart);
                long bytesWritten = outputStream != null ? outputStream.getCount() : 0;
                stats.record(request.getMethod(),
                        response.getStatusCode(),
                        bytesWritten,
                        response.getBytesRead(),
                        requestProcessingTime,
                        responseProcessingTime);
            }
        }
        finally {
            Closeables.closeQuietly(outputStream);
            JavaUrlResponse.dispose(response);
        }
    }

    private static class JavaUrlResponse implements Response
    {
        private final HttpURLConnection connection;
        private final int statusCode;
        private final String statusMessage;
        private ListMultimap<String, String> headers;
        private CountingInputStream inputStream;
        private boolean disposed = false;

        public JavaUrlResponse(HttpURLConnection connection)
                throws IOException
        {
            Preconditions.checkNotNull(connection, "connection is null");
            this.connection = connection;
            this.statusCode = connection.getResponseCode();
            this.statusMessage = connection.getResponseMessage();
        }

        @Override
        public int getStatusCode()
        {
            Preconditions.checkState(!disposed, "connection closed");
            return statusCode;
        }

        @Override
        public String getStatusMessage()
        {
            Preconditions.checkState(!disposed, "connection closed");
            return statusMessage;
        }

        @Override
        public String getHeader(String name)
        {
            Preconditions.checkState(!disposed, "connection closed");

            List<String> values = getHeaders().get(name);
            if (values.isEmpty()) {
                return null;
            }
            return values.get(0);
        }

        @Override
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

        @Override
        public long getBytesRead()
        {
            Preconditions.checkState(!disposed, "connection closed");

            if (inputStream == null) {
                return 0;
            }
            return inputStream.getCount();
        }

        @Override
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

        static void dispose(JavaUrlResponse response)
        {
            if (response != null) {
                response.dispose();
            }
        }
    }
}
