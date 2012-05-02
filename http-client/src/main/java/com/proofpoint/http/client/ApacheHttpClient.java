package com.proofpoint.http.client;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.CountingInputStream;
import com.google.common.io.CountingOutputStream;
import com.proofpoint.units.Duration;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

@Beta
public class ApacheHttpClient implements com.proofpoint.http.client.HttpClient
{
    private final RequestStats stats = new RequestStats();
    private final HttpClient httpClient;

    public ApacheHttpClient()
    {
        this(new HttpClientConfig());
    }

    public ApacheHttpClient(HttpClientConfig config)
    {
        Preconditions.checkNotNull(config, "config is null");

        PoolingClientConnectionManager connectionManager = new PoolingClientConnectionManager();
        connectionManager.setMaxTotal(config.getMaxConnections());
        connectionManager.setDefaultMaxPerRoute(config.getMaxConnectionsPerServer());

        BasicHttpParams httpParams = new BasicHttpParams();
        httpParams.setParameter(CoreConnectionPNames.SO_TIMEOUT, (int) config.getReadTimeout().toMillis());
        httpParams.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, (int) config.getConnectTimeout().toMillis());
        httpParams.setParameter(CoreConnectionPNames.SO_LINGER, 0); // do we need this?

        httpClient = new DefaultHttpClient(connectionManager, httpParams);
    }

    @Managed
    @Flatten
    public RequestStats getStats()
    {
        return stats;
    }

    public <T, E extends Exception> T execute(final Request request, final ResponseHandler<T, E> responseHandler)
            throws E
    {
        Preconditions.checkNotNull(request, "request is null");
        Preconditions.checkNotNull(responseHandler, "responseHandler is null");

        final long requestStart = System.nanoTime();
        final GenericHttpRequest genericHttpRequest = new GenericHttpRequest(request);
        try {
            T value = httpClient.execute(
                    genericHttpRequest,
                    new org.apache.http.client.ResponseHandler<T>()
                    {
                        @Override
                        public T handleResponse(HttpResponse httpResponse)
                                throws IOException
                        {
                            long responseStart = System.nanoTime();

                            Response response = new MyResponse(httpResponse);
                            try {
                                T value = responseHandler.handle(request, response);
                                return value;
                            }
                            catch (Exception e) {
                                throw new ExceptionFromResponseHandler(e);
                            }
                            finally {
                                Duration responseProcessingTime = Duration.nanosSince(responseStart);
                                Duration requestProcessingTime = new Duration(responseStart - requestStart, TimeUnit.NANOSECONDS);

                                stats.record(request.getMethod(),
                                        response.getStatusCode(),
                                        genericHttpRequest.getBytesWritten(),
                                        response.getBytesRead(),
                                        requestProcessingTime,
                                        responseProcessingTime);
                            }
                        }
                    });
            return value;
        }
        catch (Exception e) {
            if (e instanceof ExceptionFromResponseHandler) {
                try {
                    throw (E) e;
                }
                catch (ClassCastException classCastException) {
                    // this should never happen but generics suck so be safe
                    // handler will be notified of the same exception again below
                }
            }
            else if (e instanceof ConnectTimeoutException) {
                // apache http client eats the socket timeout exception
                SocketTimeoutException socketTimeoutException = new SocketTimeoutException(e.getMessage());
                socketTimeoutException.setStackTrace(e.getStackTrace());
                throw responseHandler.handleException(request, socketTimeoutException);
            }
            throw responseHandler.handleException(request, e);
        }
    }

    private static class GenericHttpRequest extends HttpEntityEnclosingRequestBase
    {
        private final String method;
        private CountingOutputStream countingOutputStream;

        public GenericHttpRequest(final Request request)
        {
            this.method = request.getMethod();
            setURI(request.getUri());

            for (Entry<String, String> entry : request.getHeaders().entries()) {
                addHeader(entry.getKey(), entry.getValue());
            }

            setEntity(new HttpEntity()
            {
                @Override
                public boolean isRepeatable()
                {
                    return true;
                }

                @Override
                public boolean isChunked()
                {
                    return true;
                }

                @Override
                public long getContentLength()
                {
                    return -1;
                }

                @Override
                public Header getContentType()
                {
                    return null;
                }

                @Override
                public Header getContentEncoding()
                {
                    return null;
                }

                @Override
                public InputStream getContent()
                        throws IOException, IllegalStateException
                {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void writeTo(OutputStream out)
                        throws IOException
                {
                    if (request.getBodyGenerator() != null) {
                        try {
                            countingOutputStream = new CountingOutputStream(out);
                            request.getBodyGenerator().write(countingOutputStream);
                        }
                        catch (Exception e) {
                            Throwables.propagateIfPossible(e, IOException.class);
                            throw new IOException(e);
                        }
                    }
                }

                @Override
                public boolean isStreaming()
                {
                    return true;
                }

                @Override
                public void consumeContent()
                {
                }
            });
        }

        @Override
        public String getMethod()
        {
            return method;
        }

        public long getBytesWritten()
        {
            if (countingOutputStream == null) {
                return 0;
            }
            return countingOutputStream.getCount();
        }
    }

    private static class ExceptionFromResponseHandler extends IOException
    {
        private ExceptionFromResponseHandler(Exception cause)
        {
            super(Preconditions.checkNotNull(cause, "cause is null"));
        }
    }

    private static class MyResponse implements Response
    {
        private final HttpResponse response;
        private CountingInputStream countingInputStream;

        public MyResponse(HttpResponse response)
        {
            this.response = response;
        }

        @Override
        public int getStatusCode()
        {
            return response.getStatusLine().getStatusCode();
        }

        @Override
        public String getStatusMessage()
        {
            return response.getStatusLine().getReasonPhrase();
        }

        @Override
        public String getHeader(String name)
        {
            Header header = response.getFirstHeader(name);
            if (header != null) {
                return header.getValue();
            }

            return null;
        }

        @Override
        public ListMultimap<String, String> getHeaders()
        {
            ArrayListMultimap<String, String> multimap = ArrayListMultimap.create();
            for (Header header : response.getAllHeaders()) {
                multimap.put(header.getName(), header.getValue());
            }
            return multimap;
        }

        @Override
        public long getBytesRead()
        {
            if (countingInputStream == null) {
                return 0;
            }
            return countingInputStream.getCount();
        }

        @Override
        public InputStream getInputStream()
                throws IOException
        {
            if (countingInputStream == null) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    InputStream content = entity.getContent();
                    if (content != null) {
                        countingInputStream = new CountingInputStream(content);
                    }
                }
            }

            if (countingInputStream == null) {
                throw new IOException("No input stream");
            }
            return countingInputStream;
        }
    }
}
