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
package com.proofpoint.http.client.balancing;

import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.ResponseHandler;

import javax.inject.Inject;
import java.net.URI;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.proofpoint.http.client.HttpUriBuilder.uriBuilderFrom;

public final class BalancingHttpClient implements HttpClient
{
    private final HttpServiceBalancer pool;
    private final HttpClient httpClient;
    private final int maxAttempts;

    @Inject
    public BalancingHttpClient(@ForBalancingHttpClient HttpServiceBalancer pool, @ForBalancingHttpClient HttpClient httpClient, BalancingHttpClientConfig config)
    {
        this.pool = checkNotNull(pool, "pool is null");
        this.httpClient = checkNotNull(httpClient, "httpClient is null");
        maxAttempts = checkNotNull(config, "config is null").getMaxAttempts();
    }


    @Override
    public <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
            throws E
    {
        checkArgument(!request.getUri().isAbsolute(), request.getUri() + " is not a relative URI");
        checkArgument(request.getUri().getHost() == null, request.getUri() + " has a host component");
        String path = request.getUri().getPath();
        checkArgument(path == null || !path.startsWith("/"), request.getUri() + " path starts with '/'");

        HttpServiceAttempt attempt;
        try {
            attempt = pool.createAttempt();
        }
        catch (RuntimeException e) {
            throw responseHandler.handleException(request, e);
        }
        int attemptsLeft = maxAttempts;

        RetryingResponseHandler<T, E> retryingResponseHandler = new RetryingResponseHandler<>(request, responseHandler);

        for (;;) {
            URI uri = uriBuilderFrom(attempt.getUri())
                    .appendPath(request.getUri().getPath())
                    .build();

            Request subRequest = Request.Builder.fromRequest(request)
                    .setUri(uri)
                    .build();

            if (attemptsLeft > 1) {
                --attemptsLeft;
                try {
                    T t = httpClient.execute(subRequest, retryingResponseHandler);
                    attempt.markGood();
                    return t;
                }
                catch (InnerHandlerException e) {
                    attempt.markBad();
                    //noinspection unchecked
                    throw (E) e.getCause();
                }
                catch (FailureStatusException e) {
                    attempt.markBad();
                    return (T) e.result;
                }
                catch (RetryException ignored) {
                    attempt.markBad();
                    try {
                        attempt = attempt.tryNext();
                    }
                    catch (RuntimeException e) {
                        throw responseHandler.handleException(request, e);
                    }
                }
            }
            else {
                try {
                    T t = httpClient.execute(subRequest, responseHandler);
                    attempt.markGood();
                    return t;
                }
                catch (RuntimeException e) {
                    attempt.markBad();
                    throw e;
                }
                catch (Exception e) {
                    attempt.markBad();
                    // noinspection unchecked
                    throw (E) e;
                }
            }
        }
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
}
