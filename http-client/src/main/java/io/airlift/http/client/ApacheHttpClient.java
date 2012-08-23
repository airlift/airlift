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

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.io.CountingInputStream;
import com.google.common.io.CountingOutputStream;
import io.airlift.units.Duration;
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
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Beta
public class ApacheHttpClient implements io.airlift.http.client.HttpClient
{
    private final RequestStats stats = new RequestStats();
    private final HttpClient httpClient;
    private final List<HttpRequestFilter> requestFilters;

    public ApacheHttpClient()
    {
        this(new HttpClientConfig());
    }

    public ApacheHttpClient(HttpClientConfig config)
    {
        this(config, Collections.<HttpRequestFilter>emptySet());
    }

    public ApacheHttpClient(HttpClientConfig config, Set<HttpRequestFilter> requestFilters)
    {
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(requestFilters, "requestFilters is null");

        PoolingClientConnectionManager connectionManager = new PoolingClientConnectionManager();
        connectionManager.setMaxTotal(config.getMaxConnections());
        connectionManager.setDefaultMaxPerRoute(config.getMaxConnectionsPerServer());

        BasicHttpParams httpParams = new BasicHttpParams();
        httpParams.setParameter(CoreConnectionPNames.SO_TIMEOUT, (int) config.getReadTimeout().toMillis());
        httpParams.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, (int) config.getConnectTimeout().toMillis());
        httpParams.setParameter(CoreConnectionPNames.SO_LINGER, 0); // do we need this?

        this.httpClient = new DefaultHttpClient(connectionManager, httpParams);
        this.requestFilters = ImmutableList.copyOf(requestFilters);
    }

    @VisibleForTesting
    List<HttpRequestFilter> getRequestFilters()
    {
        return requestFilters;
    }

    @Managed
    @Flatten
    public RequestStats getStats()
    {
        return stats;
    }

    public <T, E extends Exception> T execute(Request request, final ResponseHandler<T, E> responseHandler)
            throws E
    {
        Preconditions.checkNotNull(request, "request is null");
        Preconditions.checkNotNull(responseHandler, "responseHandler is null");

        for (HttpRequestFilter requestFilter : requestFilters) {
            request = requestFilter.filterRequest(request);
        }

        final long requestStart = System.nanoTime();
        final GenericHttpRequest genericHttpRequest = new GenericHttpRequest(request);
        final Request finalRequest = request;
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
                                T value = responseHandler.handle(finalRequest, response);
                                return value;
                            }
                            catch (Exception e) {
                                throw new ExceptionFromResponseHandler(e);
                            }
                            finally {
                                Duration responseProcessingTime = Duration.nanosSince(responseStart);
                                Duration requestProcessingTime = new Duration(responseStart - requestStart, TimeUnit.NANOSECONDS);

                                stats.record(finalRequest.getMethod(),
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
                    throw (E) e.getCause();
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

            if (request.getBodyGenerator() != null) {
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
