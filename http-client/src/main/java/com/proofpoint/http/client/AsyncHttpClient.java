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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.AbstractCheckedFuture;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

@Beta
public class AsyncHttpClient
        implements Closeable
{
    private final HttpClient httpClient;
    private final ListeningExecutorService executor;
    private final List<HttpRequestFilter> requestFilters;

    public AsyncHttpClient(HttpClient httpClient, ExecutorService executor)
    {
        this(httpClient, executor, Collections.<HttpRequestFilter>emptySet());
    }

    public AsyncHttpClient(HttpClient httpClient, ExecutorService executor, Set<HttpRequestFilter> requestFilters)
    {
        Preconditions.checkNotNull(httpClient, "httpClient is null");
        Preconditions.checkNotNull(executor, "executor is null");
        Preconditions.checkNotNull(requestFilters, "requestFilters is null");

        this.httpClient = httpClient;
        this.executor = MoreExecutors.listeningDecorator(executor);
        this.requestFilters = ImmutableList.copyOf(requestFilters);
    }

    @VisibleForTesting
    List<HttpRequestFilter> getRequestFilters()
    {
        return requestFilters;
    }

    @PreDestroy
    @Override
    public void close()
    {
        httpClient.close();
    }

    @Managed
    @Flatten
    public RequestStats getStats()
    {
        return httpClient.getStats();
    }

    public <T, E extends Exception> CheckedFuture<T, E> execute(Request request, ResponseHandler<T, E> responseHandler)
            throws E
    {
        Preconditions.checkNotNull(request, "request is null");
        Preconditions.checkNotNull(responseHandler, "responseHandler is null");

        for (HttpRequestFilter requestFilter : requestFilters) {
            request = requestFilter.filterRequest(request);
        }

        ListenableFuture<T> listenableFuture = executor.submit(new HttpExecution<T>(request, responseHandler));
        return new ResponseFuture<T, E>(request, responseHandler, listenableFuture);
    }

    private class HttpExecution<T>
            implements Callable<T>
    {
        private final Request request;
        private final ResponseHandler<T, ?> responseHandler;

        public HttpExecution(Request request, ResponseHandler<T, ?> responseHandler)
        {
            this.request = request;
            this.responseHandler = responseHandler;
        }

        public T call()
                throws Exception
        {
            try {
                return httpClient.execute(request, responseHandler);
            }
            catch (Exception e) {
                throw new ExceptionFromHttpClient(e);
            }
        }
    }

    private static class ResponseFuture<T, E extends Exception>
            extends AbstractCheckedFuture<T, E>
    {
        private final Request request;
        private final ResponseHandler<T, E> responseHandler;

        private ResponseFuture(Request request, ResponseHandler<T, E> responseHandler, ListenableFuture<T> delegate)
        {
            super(delegate);
            this.request = request;
            this.responseHandler = responseHandler;
        }

        @Override
        protected E mapException(Exception e)
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
