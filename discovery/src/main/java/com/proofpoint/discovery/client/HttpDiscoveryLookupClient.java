package com.proofpoint.discovery.client;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.inject.Inject;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestBuilder;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;

import javax.inject.Provider;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.discovery.client.DiscoveryAnnouncementClient.DEFAULT_DELAY;
import static com.proofpoint.http.client.RequestBuilder.prepareGet;
import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.NOT_MODIFIED;
import static javax.ws.rs.core.Response.Status.OK;

public class HttpDiscoveryLookupClient implements DiscoveryLookupClient
{
    private final String environment;
    private final Provider<URI> discoveryServiceURI;
    private final NodeInfo nodeInfo;
    private final JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec;
    private final HttpClient httpClient;

    @Inject
    public HttpDiscoveryLookupClient(@ForDiscoveryClient Provider<URI> discoveryServiceURI,
            NodeInfo nodeInfo,
            JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec,
            @ForDiscoveryClient HttpClient httpClient)
    {
        Preconditions.checkNotNull(discoveryServiceURI, "discoveryServiceURI is null");
        Preconditions.checkNotNull(nodeInfo, "nodeInfo is null");
        Preconditions.checkNotNull(serviceDescriptorsCodec, "serviceDescriptorsCodec is null");
        Preconditions.checkNotNull(httpClient, "httpClient is null");

        this.nodeInfo = nodeInfo;
        this.environment = nodeInfo.getEnvironment();
        this.discoveryServiceURI = discoveryServiceURI;
        this.serviceDescriptorsCodec = serviceDescriptorsCodec;
        this.httpClient = httpClient;
    }

    @Flatten
    @Managed
    public RequestStats getStats()
    {
        return httpClient.getStats();
    }

    @Override
    public CheckedFuture<ServiceDescriptors, DiscoveryException> getServices(String type)
    {
        Preconditions.checkNotNull(type, "type is null");
        return lookup(type, null, null);
    }

    @Override
    public CheckedFuture<ServiceDescriptors, DiscoveryException> getServices(String type, String pool)
    {
        Preconditions.checkNotNull(type, "type is null");
        Preconditions.checkNotNull(pool, "pool is null");
        return lookup(type, pool, null);
    }

    @Override
    public CheckedFuture<ServiceDescriptors, DiscoveryException> refreshServices(ServiceDescriptors serviceDescriptors)
    {
        Preconditions.checkNotNull(serviceDescriptors, "serviceDescriptors is null");
        return lookup(serviceDescriptors.getType(), serviceDescriptors.getPool(), serviceDescriptors);
    }

    private CheckedFuture<ServiceDescriptors, DiscoveryException> lookup(final String type, final String pool, final ServiceDescriptors serviceDescriptors)
    {
        Preconditions.checkNotNull(type, "type is null");

        URI uri = discoveryServiceURI.get();
        if (uri == null) {
            return Futures.immediateFailedCheckedFuture(new DiscoveryException("No discovery servers are available"));
        }

        uri = URI.create(uri + "/v1/service/" + type + "/");
        if (pool != null) {
            uri = uri.resolve(pool);
        }

        RequestBuilder requestBuilder = prepareGet()
                .setUri(uri)
                .setHeader("User-Agent", nodeInfo.getNodeId());
        if (serviceDescriptors != null && serviceDescriptors.getETag() != null) {
            requestBuilder.setHeader(HttpHeaders.ETAG, serviceDescriptors.getETag());
        }
        return httpClient.execute(requestBuilder.build(), new DiscoveryResponseHandler<ServiceDescriptors>(format("Lookup of %s", type))
        {
            @Override
            public ServiceDescriptors handle(Request request, Response response)
            {
                Duration maxAge = extractMaxAge(response);
                String eTag = response.getHeader(HttpHeaders.ETAG);

                if (NOT_MODIFIED.getStatusCode() == response.getStatusCode() && serviceDescriptors != null) {
                    return new ServiceDescriptors(serviceDescriptors, maxAge, eTag);
                }

                if (OK.getStatusCode() != response.getStatusCode()) {
                    throw new DiscoveryException(format("Lookup of %s failed with status code %s", type, response.getStatusCode()));
                }


                String json;
                try {
                    json = CharStreams.toString(new InputStreamReader(response.getInputStream(), Charsets.UTF_8));
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

        protected DiscoveryResponseHandler(String name)
        {
            this.name = name;
        }

        @Override
        public T handle(Request request, Response response)
        {
            return null;
        }

        @Override
        public final DiscoveryException handleException(Request request, Exception exception)
        {
            if (exception instanceof InterruptedException) {
                return new DiscoveryException(name + " was interrupted");
            }
            if (exception instanceof CancellationException) {
                return new DiscoveryException(name + " was canceled");
            }
            if (exception instanceof DiscoveryException) {
                throw (DiscoveryException) exception;
            }

            return new DiscoveryException(name + " failed", exception);
        }
    }
}
