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
package com.proofpoint.discovery.client;

import com.google.common.io.ByteStreams;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import com.proofpoint.http.client.CacheControl;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.Request.Builder;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;
import org.weakref.jmx.Flatten;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.proofpoint.discovery.client.announce.DiscoveryAnnouncementClient.DEFAULT_DELAY;
import static com.proofpoint.http.client.HttpStatus.NOT_MODIFIED;
import static com.proofpoint.http.client.HttpStatus.OK;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static java.lang.String.format;

public class HttpDiscoveryLookupClient implements DiscoveryLookupClient
{
    private final String environment;
    private final NodeInfo nodeInfo;
    private final JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec;
    private final HttpClient httpClient;

    @Inject
    public HttpDiscoveryLookupClient(NodeInfo nodeInfo,
            JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec,
            @ForDiscoveryClient HttpClient httpClient,
            ServiceInventory serviceInventory)
    {
        checkNotNull(nodeInfo, "nodeInfo is null");
        checkNotNull(serviceDescriptorsCodec, "serviceDescriptorsCodec is null");
        checkNotNull(httpClient, "httpClient is null");
        // serviceInventory injected only to ensure it is constructed first and feeds
        // the list of discovery servers into httpClient before we use httpClient

        this.nodeInfo = nodeInfo;
        this.environment = nodeInfo.getEnvironment();
        this.serviceDescriptorsCodec = serviceDescriptorsCodec;
        this.httpClient = httpClient;
    }

    @Flatten
    public RequestStats getStats()
    {
        return httpClient.getStats();
    }

    @Override
    public ListenableFuture<ServiceDescriptors> getServices(String type)
    {
        checkNotNull(type, "type is null");
        return lookup(type, null, null);
    }

    @Override
    public ListenableFuture<ServiceDescriptors> getServices(String type, String pool)
    {
        checkNotNull(type, "type is null");
        checkNotNull(pool, "pool is null");
        return lookup(type, pool, null);
    }

    @Override
    public ListenableFuture<ServiceDescriptors> refreshServices(ServiceDescriptors serviceDescriptors)
    {
        checkNotNull(serviceDescriptors, "serviceDescriptors is null");
        return lookup(serviceDescriptors.getType(), serviceDescriptors.getPool(), serviceDescriptors);
    }

    private ListenableFuture<ServiceDescriptors> lookup(final String type, final String pool, final ServiceDescriptors serviceDescriptors)
    {
        checkNotNull(type, "type is null");

        URI uri = URI.create("v1/service/" + type + "/");
        if (pool != null) {
            uri = uri.resolve(pool);
        }

        Builder requestBuilder = prepareGet()
                .setUri(uri)
                .setHeader("User-Agent", nodeInfo.getNodeId());
        if (serviceDescriptors != null && serviceDescriptors.getETag() != null) {
            requestBuilder.setHeader(HttpHeaders.ETAG, serviceDescriptors.getETag());
        }
        return httpClient.executeAsync(requestBuilder.build(), new DiscoveryResponseHandler<ServiceDescriptors>(format("Lookup of %s", type))
        {
            @Override
            public ServiceDescriptors handle(Request request, Response response)
            {
                Duration maxAge = extractMaxAge(response);
                String eTag = response.getHeader(HttpHeaders.ETAG);

                if (NOT_MODIFIED.code() == response.getStatusCode() && serviceDescriptors != null) {
                    return new ServiceDescriptors(serviceDescriptors, maxAge, eTag);
                }

                if (OK.code() != response.getStatusCode()) {
                    throw new DiscoveryException(format("Lookup of %s failed with status code %s", type, response.getStatusCode()));
                }

                byte[] json;
                try {
                    json = ByteStreams.toByteArray(response.getInputStream());
                }
                catch (IOException e) {
                    throw new DiscoveryException(format("Lookup of %s failed", type), e);
                }

                ServiceDescriptorsRepresentation serviceDescriptorsRepresentation = serviceDescriptorsCodec.fromJson(json);
                if (!environment.equals(serviceDescriptorsRepresentation.getEnvironment())) {
                    throw new DiscoveryException(format("Expected environment to be %s, but was %s", environment, serviceDescriptorsRepresentation.getEnvironment()));
                }

                return new ServiceDescriptors(
                        type,
                        pool,
                        serviceDescriptorsRepresentation.getServiceDescriptors(),
                        maxAge,
                        eTag);
            }
        });
    }

    private Duration extractMaxAge(Response response)
    {
        String header = response.getHeader(HttpHeaders.CACHE_CONTROL);
        if (header != null) {
            CacheControl cacheControl = CacheControl.valueOf(header);
            if (cacheControl.getMaxAge() > 0) {
                return new Duration(cacheControl.getMaxAge(), TimeUnit.SECONDS);
            }
        }
        return DEFAULT_DELAY;
    }

    private class DiscoveryResponseHandler<T> implements ResponseHandler<T, DiscoveryException>
    {
        private final String name;

        DiscoveryResponseHandler(String name)
        {
            this.name = name;
        }

        @Override
        public T handle(Request request, Response response)
        {
            return null;
        }

        @Override
        public final T handleException(Request request, Exception exception)
        {
            if (exception instanceof InterruptedException) {
                throw new DiscoveryException(name + " was interrupted");
            }
            if (exception instanceof CancellationException) {
                throw new DiscoveryException(name + " was canceled");
            }
            if (exception instanceof DiscoveryException) {
                throw (DiscoveryException) exception;
            }

            throw new DiscoveryException(name + " failed", exception);
        }
    }
}
