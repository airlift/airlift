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
package com.proofpoint.http.client;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.proofpoint.units.Duration;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.DefaultHttpAsyncClient;
import org.apache.http.impl.nio.conn.PoolingClientAsyncConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.client.HttpAsyncClient;
import org.apache.http.nio.client.methods.HttpAsyncMethods;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Beta
public class AsyncHttpClient
        implements Closeable
{
    private final RequestStats stats = new RequestStats();
    private final HttpAsyncClient httpClient;
    private final List<HttpRequestFilter> requestFilters;
    private final int readTimeout;
    private final int connectTimeout;

    public AsyncHttpClient()
    {
        this(new HttpClientConfig());
    }

    public AsyncHttpClient(HttpClientConfig config)
    {
        this(config, Collections.<HttpRequestFilter>emptySet());
    }

    public AsyncHttpClient(HttpClientConfig config, Set<? extends HttpRequestFilter> requestFilters)
    {
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(requestFilters, "requestFilters is null");

        readTimeout = (int) config.getReadTimeout().toMillis();
        connectTimeout = (int) config.getConnectTimeout().toMillis();

        try {
            IOReactorConfig ioReactorConfig = new IOReactorConfig();
            ioReactorConfig.setSoTimeout(readTimeout);
            ioReactorConfig.setConnectTimeout(connectTimeout);
            ioReactorConfig.setSoLinger(0); // do we need this?
            ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor(ioReactorConfig);

            PoolingClientAsyncConnectionManager connectionManager = new PoolingClientAsyncConnectionManager(ioReactor);
            connectionManager.setMaxTotal(config.getMaxConnections());
            connectionManager.setDefaultMaxPerRoute(config.getMaxConnectionsPerServer());

            DefaultHttpAsyncClient defaultHttpAsyncClient = new DefaultHttpAsyncClient(connectionManager);
            defaultHttpAsyncClient.setKeepAliveStrategy(new FixedIntervalKeepAliveStrategy(config.getKeepAliveInterval()));
            this.httpClient = defaultHttpAsyncClient;
        }
        catch (IOReactorException e) {
            throw Throwables.propagate(e);
        }

        this.requestFilters = ImmutableList.copyOf(requestFilters);
        httpClient.start();
    }

    @VisibleForTesting
    List<HttpRequestFilter> getRequestFilters()
    {
        return requestFilters;
    }

    @PreDestroy
    @Override
    public void close()
            throws IOException
    {
        httpClient.getConnectionManager().shutdown();
    }

    @Managed
    @Flatten
    public RequestStats getStats()
    {
        return stats;
    }

    public <T, E extends Exception> CheckedFuture<T, E> execute(Request request, final ResponseHandler<T, E> responseHandler)
            throws E
    {
        Preconditions.checkNotNull(request, "request is null");
        Preconditions.checkNotNull(responseHandler, "responseHandler is null");

        for (HttpRequestFilter requestFilter : requestFilters) {
            request = requestFilter.filterRequest(request);
        }

        StatsHttpUriRequest httpUriRequest = StatsHttpUriRequest.createGenericHttpRequest(request);

        // TODO: remove this when http client isn't so broken
        HttpParams httpParams = httpUriRequest.getParams();
        httpParams.setParameter(CoreConnectionPNames.SO_TIMEOUT, readTimeout);
        httpParams.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, connectTimeout);
        httpParams.setParameter(CoreConnectionPNames.SO_LINGER, 0); // do we need this?

        HttpHost httpHost = URIUtils.extractHost(request.getUri());
        Preconditions.checkArgument(httpHost != null, "Request uri does not contain a host %s", request.getUri());

        HttpResponseFuture<T, E> future = new HttpResponseFuture<>(httpUriRequest, request, responseHandler);

        Future<HttpResponse> requestTaskControl = httpClient.execute(
                HttpAsyncMethods.create(httpHost, httpUriRequest),
                HttpAsyncMethods.createConsumer(),
                new BasicHttpContext(),
                future.getFutureCallback());
        future.setRequestTaskControl(requestTaskControl);
        return Futures.makeChecked(future, toExceptionMapper(request, responseHandler));
    }

    private static <T, E extends Exception> Function<Exception, E> toExceptionMapper(final Request request, final ResponseHandler<T, E> responseHandler)
    {
        return new Function<Exception, E>()
        {
            public E apply(Exception e)
            {
                if (e instanceof ExecutionException) {
                    Throwable cause = e.getCause();
                    // Do not ask the handler to "handle" an exception it produced
                    if (cause instanceof ExceptionFromHttpClient) {
                        try {
                            return (E) cause.getCause();
                        }
                        catch (ClassCastException classCastException) {
                            // this should never happen but generics suck so be safe
                            // handler will be notified of the same exception again below
                        }
                    }
                    if (cause instanceof Exception) {
                        e = (Exception) cause;
                    }
                }
                return responseHandler.handleException(request, e);
            }
        };
    }

    private class HttpResponseFuture<T, E extends Exception>
            extends AbstractFuture<T>
    {
        private final long requestStart = System.nanoTime();
        private final StatsHttpUriRequest httpUriRequest;
        private final Request request;
        private final ResponseHandler<T, E> responseHandler;

        private final AtomicReference<Future<?>> requestTaskControl = new AtomicReference<Future<?>>();
        private final AtomicBoolean wasInterrupted = new AtomicBoolean();

        private HttpResponseFuture(StatsHttpUriRequest httpUriRequest, Request request, ResponseHandler<T, E> responseHandler)
        {
            this.httpUriRequest = httpUriRequest;
            this.request = request;
            this.responseHandler = responseHandler;
        }

        void setRequestTaskControl(Future<?> requestTaskControl)
        {
            if (!this.requestTaskControl.compareAndSet(null, requestTaskControl)) {
                throw new IllegalStateException("requestTaskControl already set");
            }
            if (isCancelled()) {
                requestTaskControl.cancel(wasInterrupted.get());
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning)
        {
            if (mayInterruptIfRunning) {
                wasInterrupted.set(true);
            }
            Future<?> taskControl = requestTaskControl.get();
            if (taskControl != null) {
                taskControl.cancel(wasInterrupted.get());
            }

            return super.cancel(mayInterruptIfRunning);
        }

        private FutureCallback<HttpResponse> getFutureCallback()
        {
            return new FutureCallback<HttpResponse>()
            {
                @Override
                public void completed(HttpResponse httpResponse)
                {
                    // this time will not include the data fetching portion of the response,
                    // since the response is fully cached in memory at this point
                    long responseStart = System.nanoTime();

                    Response response = new ApacheHttpClient.MyResponse(httpResponse);
                    try {
                        T value = responseHandler.handle(request, response);
                        set(value);
                    }
                    catch (Exception e) {
                        setException(new ExceptionFromHttpClient(e));
                    }
                    finally {
                        Duration responseProcessingTime = Duration.nanosSince(responseStart);
                        Duration requestProcessingTime = new Duration(responseStart - requestStart, TimeUnit.NANOSECONDS);

                        stats.record(request.getMethod(),
                                response.getStatusCode(),
                                httpUriRequest.getBytesWritten(),
                                response.getBytesRead(),
                                requestProcessingTime,
                                responseProcessingTime);
                    }
                }

                @Override
                public void failed(Exception e)
                {
                    setException(e);
                }

                @Override
                public void cancelled()
                {
                    cancel(true);
                }
            };
        }
    }

    private static class ExceptionFromHttpClient
            extends Exception
    {
        private ExceptionFromHttpClient(Exception cause)
        {
            super(Preconditions.checkNotNull(cause, "cause is null"));
        }
    }
}
