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
import com.proofpoint.discovery.client.ServiceUnavailableException;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.ResponseHandler;

import javax.inject.Inject;
import java.net.URI;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class BalancingHttpClient implements HttpClient
{

    private final HttpServiceSelector serviceSelector;
    private final HttpClient httpClient;
    private final int maxRetries;

    @Inject
    public BalancingHttpClient(HttpServiceSelector serviceSelector, HttpClient httpClient, BalancingHttpClientConfig config)
    {
        this.serviceSelector = checkNotNull(serviceSelector, "serviceSelector is null");
        this.httpClient = checkNotNull(httpClient, "httpClient is null");
        maxRetries = checkNotNull(config, "config is null").getMaxRetries();
    }


    @Override
    public <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
            throws E
    {
        checkArgument(!request.getUri().isAbsolute(), request.getUri() + " is not a relative URI");

        List<URI> uris = serviceSelector.selectHttpService();
        if (uris.isEmpty()) {
            throw new ServiceUnavailableException(serviceSelector.getType(), serviceSelector.getPool());
        }

        int retriesLeft = maxRetries;

        RetryingResponseHandler<T, E> retryingResponseHandler = new RetryingResponseHandler<>(request, responseHandler);

        for (int attempt = 0; ; ++attempt) {
            URI uri = uris.get(attempt % uris.size());
            // TODO - skip if uri is persistently failing

            Request subRequest = Request.Builder.fromRequest(request)
                    .setUri(uri.resolve(request.getUri()))
                    .build();

            if (retriesLeft > 0) {
                --retriesLeft;
                try {
                    return httpClient.execute(subRequest, retryingResponseHandler);
                }
                catch (InnerHandlerException e) {
                    //noinspection unchecked
                    throw (E) e.getCause();
                }
                catch (RetryException ignored) {
                }
            }
            else {
                return httpClient.execute(subRequest, responseHandler);
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
