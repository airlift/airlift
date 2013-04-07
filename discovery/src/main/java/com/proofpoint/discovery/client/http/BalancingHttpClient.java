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


import com.proofpoint.discovery.client.HttpServiceSelector;
import com.proofpoint.discovery.client.balance.HttpServiceAttempt;
import com.proofpoint.discovery.client.balance.HttpServiceBalancer;
import com.proofpoint.discovery.client.balance.HttpServiceBalancerImpl;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.ResponseHandler;

import javax.inject.Inject;
import java.net.URI;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class BalancingHttpClient implements HttpClient
{

    private final HttpServiceBalancer pool;
    private final HttpClient httpClient;
    private final int maxRetries;

    @Inject
    public BalancingHttpClient(@ForBalancingHttpClient HttpServiceSelector serviceSelector, @ForBalancingHttpClient HttpClient httpClient, BalancingHttpClientConfig config)
    {
        pool = new HttpServiceBalancerImpl(serviceSelector);
        this.httpClient = checkNotNull(httpClient, "httpClient is null");
        maxRetries = checkNotNull(config, "config is null").getMaxRetries();
    }


    @Override
    public <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
            throws E
    {
        checkArgument(!request.getUri().isAbsolute(), request.getUri() + " is not a relative URI");
        checkArgument(request.getUri().getHost() == null, request.getUri() + " has a host component");
        String path = request.getUri().getPath();
        checkArgument(path == null || !path.startsWith("/"), request.getUri() + " path starts with '/'");

        HttpServiceAttempt attempt = pool.createAttempt();
        int retriesLeft = maxRetries;

        RetryingResponseHandler<T, E> retryingResponseHandler = new RetryingResponseHandler<>(request, responseHandler);

        for (;;) {
            URI uri = attempt.getUri();
            if (uri.getPath() == null || uri.getPath().isEmpty()) {
                uri = uri.resolve("/");
            }

            Request subRequest = Request.Builder.fromRequest(request)
                    .setUri(uri.resolve(request.getUri()))
                    .build();

            if (retriesLeft > 0) {
                --retriesLeft;
                try {
                    T t = httpClient.execute(subRequest, retryingResponseHandler);
                    attempt.markGood();
                    return t;
                }
                catch (InnerHandlerException e) {
                    attempt.markBad(); // todo We don't retry on handler exceptions. Should we mark bad?
                    //noinspection unchecked
                    throw (E) e.getCause();
                }
                catch (RetryException ignored) {
                    attempt.markBad();
                    attempt = attempt.tryNext();
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
