/*
 * Copyright 2013 Proofpoint, Inc.
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
package com.proofpoint.discovery.client.http;


import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.proofpoint.discovery.client.HttpServiceSelector;
import com.proofpoint.discovery.client.ServiceUnavailableException;
import com.proofpoint.http.client.AsyncHttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.ResponseHandler;

import javax.inject.Inject;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

public final class BalancingAsyncHttpClient implements AsyncHttpClient
{

    private final HttpServiceSelector serviceSelector;
    private final AsyncHttpClient httpClient;
    private final int maxRetries;

    @Inject
    public BalancingAsyncHttpClient(@ForBalancingHttpClient HttpServiceSelector serviceSelector, @ForBalancingHttpClient AsyncHttpClient httpClient, BalancingHttpClientConfig config)
    {
        this.serviceSelector = checkNotNull(serviceSelector, "serviceSelector is null");
        this.httpClient = checkNotNull(httpClient, "httpClient is null");
        maxRetries = checkNotNull(config, "config is null").getMaxRetries();
    }


    @Override
    public <T, E extends Exception> AsyncHttpResponseFuture<T, E> executeAsync(Request request, ResponseHandler<T, E> responseHandler)
    {
        checkArgument(!request.getUri().isAbsolute(), request.getUri() + " is not a relative URI");
        checkArgument(request.getUri().getHost() == null, request.getUri() + " has a host component");
        String path = request.getUri().getPath();
        checkArgument(path == null || !path.startsWith("/"), request.getUri() + " path starts with '/'");

        List<URI> uris = serviceSelector.selectHttpService();
        if (uris.isEmpty()) {
            throw new ServiceUnavailableException(serviceSelector.getType(), serviceSelector.getPool());
        }

        int attempt = 0;

        return attemptQuery(request, responseHandler, uris, attempt, maxRetries);
    }

    private <T, E extends Exception> AsyncHttpResponseFuture<T, E> attemptQuery(Request request, ResponseHandler<T, E> responseHandler, List<URI> uris, int attempt, int retriesLeft)
    {
        RetryingResponseHandler<T, E> retryingResponseHandler = new RetryingResponseHandler<>(request, responseHandler);

        URI uri = uris.get(attempt % uris.size());
        if (uri.getPath() == null || uri.getPath().isEmpty()) {
            uri = uri.resolve("/");
        }
        ++attempt;
        // TODO - skip if uri is persistently failing

        Request subRequest = Request.Builder.fromRequest(request)
                .setUri(uri.resolve(request.getUri()))
                .build();

        if (retriesLeft > 0) {
            --retriesLeft;
            AsyncHttpResponseFuture<T, RetryException> future = httpClient.executeAsync(subRequest, retryingResponseHandler);
            return new RetryFuture<>(future, request, responseHandler, uris, attempt, uri, retriesLeft);
        }
        else {
            return httpClient.executeAsync(subRequest, responseHandler);
        }
    }

    @Override
    public <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
            throws E
    {
        return executeAsync(request, responseHandler).checkedGet();
    }

    @Override
    public RequestStats getStats()
    {
        return httpClient.getStats();
    }

    @Override
    public void close()
    {
        httpClient.close();
    }

    private class RetryFuture<T, E extends Exception>
            extends AbstractFuture<T>
            implements AsyncHttpResponseFuture<T, E>
    {

        private final Request request;
        private final ResponseHandler<T,E> responseHandler;
        private final int attempt;
        private final URI uri;
        private AsyncHttpResponseFuture<T, ?> subFuture;
        private final Object subFutureLock = new Object();
        boolean retried = false;

        public RetryFuture(final AsyncHttpResponseFuture<T, RetryException> future, final Request request, final ResponseHandler<T, E> responseHandler, final List<URI> uris, final int attempt, URI uri, final int retriesLeft)
        {
            this.request = request;
            this.responseHandler = responseHandler;
            subFuture = future;
            this.attempt = attempt;
            this.uri = uri;
            future.addListener(new Runnable()
            {
                @Override
                public void run()
                {
                    try {
                        set(future.checkedGet());
                    }
                    catch (InnerHandlerException e) {
                        setException(e.getCause());
                    }
                    catch (RetryException e) {
                        final AsyncHttpResponseFuture<T, ?> attemptFuture;
                        synchronized (subFutureLock) {
                            try {
                                attemptFuture = attemptQuery(request, responseHandler, uris, attempt, retriesLeft);
                            }
                            catch (RuntimeException e1) {
                                setException(e1); // todo send to responsehandler here?
                                return;
                            }
                            retried = true;
                            subFuture = attemptFuture;
                        }
                        attemptFuture.addListener(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                try {
                                    set(attemptFuture.checkedGet());
                                }
                                catch (Exception e1) {
                                    setException(e1);
                                }
                            }
                        }, MoreExecutors.sameThreadExecutor());
                    }
                }
            }, MoreExecutors.sameThreadExecutor());
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning)
        {

            if (super.cancel(mayInterruptIfRunning)) {
                synchronized (subFutureLock) {
                    if (subFuture != null) {
                        subFuture.cancel(mayInterruptIfRunning);
                    }
                }
                return true;
            }
            return false;
        }

        @Override
        public T checkedGet()
                throws E
        {
            try {
                return get();
            }
            catch (InterruptedException | CancellationException | ExecutionException e) {
                throw mapException(e);
            }
        }

        @Override
        public T checkedGet(long timeout, TimeUnit unit)
                throws TimeoutException, E
        {
            try {
                return get(timeout, unit);
            }
            catch (InterruptedException | CancellationException | ExecutionException e) {
                throw mapException(e);
            }
        }

        private E mapException(Exception e)
        {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            if (e instanceof ExecutionException) {
                return (E) e.getCause();
            }

            return responseHandler.handleException(request, e);
        }

        @Override
        public String getState()
        {
            synchronized (subFutureLock) {
                if (retried) {
                    return subFuture.getState();
                }
                return format("Attempt %s to %s: %s", attempt, uri, subFuture.getState());
            }
        }
    }
}
