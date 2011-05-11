package com.proofpoint.discovery.client;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.Response;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.proofpoint.discovery.client.DiscoveryFutures.toDiscoveryFuture;
import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.NOT_MODIFIED;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.OK;

public class HttpDiscoveryClient implements DiscoveryClient
{

    private final String environment;
    private final URI discoveryServiceURI;
    private final NodeInfo nodeInfo;
    private final AsyncHttpClient client;
    private final JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec;
    private final JsonCodec<Announcement> announcementCodec;

    @Inject
    public HttpDiscoveryClient(DiscoveryClientConfig config,
            NodeInfo nodeInfo,
            JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec,
            JsonCodec<Announcement> announcementCodec)
    {
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(nodeInfo, "nodeInfo is null");
        Preconditions.checkNotNull(serviceDescriptorsCodec, "serviceDescriptorsCodec is null");
        Preconditions.checkNotNull(announcementCodec, "announcementCodec is null");

        this.nodeInfo = nodeInfo;
        this.environment = nodeInfo.getEnvironment();
        this.discoveryServiceURI = config.getDiscoveryServiceURI();
        this.serviceDescriptorsCodec = serviceDescriptorsCodec;
        this.announcementCodec = announcementCodec;
        client = new AsyncHttpClient();
    }

    @Override
    public CheckedFuture<Duration, DiscoveryException> announce(Set<ServiceAnnouncement> services)
    {
        Preconditions.checkNotNull(services, "services is null");

        ListenableFuture<Duration> durationFuture;
        try {
            Announcement announcement = new Announcement(nodeInfo.getEnvironment(), nodeInfo.getNodeId(), nodeInfo.getLocation(), services);
            String json = announcementCodec.toJson(announcement);
            ListenableFuture<Response> future = toGuavaListenableFuture(
                    client.preparePut(discoveryServiceURI + "/v1/announcement/" + nodeInfo.getNodeId())
                            .setHeader("Content-Type", MediaType.APPLICATION_JSON)
                            .setBody(json)
                            .execute());

            durationFuture = Futures.transform(future, new Function<Response, Duration>()
            {
                @Override
                public Duration apply(Response response)
                {
                    Duration maxAge = extractMaxAge(response);
                    int statusCode = response.getStatusCode();
                    if (OK.getStatusCode() != statusCode && CREATED.getStatusCode() != statusCode && NO_CONTENT.getStatusCode() != statusCode) {
                        throw new DiscoveryException(String.format("Announcement failed with status code %s", statusCode));
                    }

                    return maxAge;
                }
            });

        }
        catch (Exception e) {
            durationFuture = Futures.immediateFailedFuture(e);
        }

        return toDiscoveryFuture("Announcement", durationFuture);
    }

    @Override
    public CheckedFuture<Void, DiscoveryException> unannounce()
    {
        ListenableFuture<Void> voidFuture;
        try {
            ListenableFuture<Response> future = toGuavaListenableFuture(client.prepareDelete(discoveryServiceURI + "/v1/announcement/" + nodeInfo.getNodeId()).execute());
            voidFuture = Futures.transform(future, Functions.<Void>constant(null));
        }
        catch (Exception e) {
            voidFuture = Futures.immediateFailedFuture(e);
        }
        return toDiscoveryFuture("Unannouncement", voidFuture);
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
        ListenableFuture<ServiceDescriptors> serviceDescriptorsFuture;
        try {
            Preconditions.checkNotNull(type, "type is null");

            BoundRequestBuilder request = client.prepareGet(discoveryServiceURI + "/v1/service/" + type + "/" + pool);
            if (serviceDescriptors != null) {
                request.setHeader(HttpHeaders.ETAG, serviceDescriptors.getETag());
            }

            serviceDescriptorsFuture = Futures.transform(toGuavaListenableFuture(request.execute()),
                    new Function<Response, ServiceDescriptors>()
                    {
                        @Override
                        public ServiceDescriptors apply(Response response)
                        {
                            Duration maxAge = extractMaxAge(response);
                            String eTag = response.getHeader(HttpHeaders.ETAG);

                            if (NOT_MODIFIED.getStatusCode() == response.getStatusCode() && serviceDescriptors != null) {
                                return new ServiceDescriptors(serviceDescriptors, maxAge, eTag);
                            }

                            if (OK.getStatusCode() != response.getStatusCode()) {
                                throw new DiscoveryException(format("Lookup of %s failed with status code %s", serviceDescriptors.getType(), response.getStatusCode()));
                            }


                            String json;
                            try {
                                json = response.getResponseBody();
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
        catch (Exception e) {
            serviceDescriptorsFuture = Futures.immediateFailedFuture(e);
        }
        return toDiscoveryFuture(format("Lookup of %s", type), serviceDescriptorsFuture);
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

    static class ServiceDescriptorsRepresentation
    {
        private final String environment;
        private final List<ServiceDescriptor> serviceDescriptors;

        @JsonCreator
        public ServiceDescriptorsRepresentation(
                @JsonProperty("environment") String environment,
                @JsonProperty("services") List<ServiceDescriptor> serviceDescriptors)
        {
            Preconditions.checkNotNull(serviceDescriptors);
            Preconditions.checkNotNull(environment);
            this.environment = environment;
            this.serviceDescriptors = ImmutableList.copyOf(serviceDescriptors);
        }

        public String getEnvironment()
        {
            return environment;
        }

        public List<ServiceDescriptor> getServiceDescriptors()
        {
            return serviceDescriptors;
        }

        @Override
        public String toString()
        {
            final StringBuffer sb = new StringBuffer();
            sb.append("ServiceDescriptorsRepresentation");
            sb.append("{environment='").append(environment).append('\'');
            sb.append(", serviceDescriptorList=").append(serviceDescriptors);
            sb.append('}');
            return sb.toString();
        }
    }

    private static <T> ListenableFuture<T> toGuavaListenableFuture(final com.ning.http.client.ListenableFuture<T> asyncClientFuture)
    {
        return new ListenableFuture<T>()
        {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning)
            {
                return asyncClientFuture.cancel(mayInterruptIfRunning);
            }

            @Override
            public void addListener(Runnable listener, Executor executor)
            {
                asyncClientFuture.addListener(listener, executor);
            }

            @Override
            public boolean isCancelled()
            {
                return asyncClientFuture.isCancelled();
            }

            @Override
            public boolean isDone()
            {
                return asyncClientFuture.isDone();
            }

            @Override
            public T get()
                    throws InterruptedException, ExecutionException
            {
                return asyncClientFuture.get();
            }

            @Override
            public T get(long timeout, TimeUnit timeUnit)
                    throws InterruptedException, ExecutionException, TimeoutException
            {
                return asyncClientFuture.get(timeout, timeUnit);
            }
        };
    }
}
