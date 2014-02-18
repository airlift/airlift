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
import org.weakref.jmx.Flatten;

import javax.inject.Inject;
import java.net.URI;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class BalancingHttpClient
        implements HttpClient
{
    final HttpServiceBalancer pool;
    private final HttpClient httpClient;
    final int maxAttempts;

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
            return responseHandler.handleException(request, e);
        }
        int attemptsLeft = maxAttempts;

        RetryingResponseHandler<T, E> retryingResponseHandler = new RetryingResponseHandler<>(request, responseHandler, false);

        for (;;) {
            URI uri = attempt.getUri();
            if (!uri.toString().endsWith("/")) {
                uri = URI.create(uri.toString() + '/');
            }
            uri = uri.resolve(request.getUri());

            Request subRequest = Request.Builder.fromRequest(request)
                    .setUri(uri)
                    .build();

            if (attemptsLeft <= 1) {
                retryingResponseHandler = new RetryingResponseHandler<>(request, responseHandler, true);
            }

            --attemptsLeft;
            try {
                T t = httpClient.execute(subRequest, retryingResponseHandler);
                attempt.markGood();
                return t;
            }
            catch (InnerHandlerException e) {
                attempt.markBad(e.getFailureCategory());
                //noinspection unchecked
                throw (E) e.getCause();
            }
            catch (FailureStatusException e) {
                attempt.markBad(e.getFailureCategory());
                //noinspection unchecked
                return (T) e.result;
            }
            catch (RetryException e) {
                attempt.markBad(e.getFailureCategory());
                try {
                    attempt = attempt.next();
                }
                catch (RuntimeException e1) {
                    return responseHandler.handleException(request, e1);
                }
            }
        }
    }

    @Flatten
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
