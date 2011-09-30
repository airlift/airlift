package com.proofpoint.discovery.client;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.inject.Inject;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestBuilder;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static com.proofpoint.http.client.RequestBuilder.prepareDelete;
import static com.proofpoint.http.client.RequestBuilder.prepareGet;
import static com.proofpoint.http.client.RequestBuilder.preparePut;
import static com.proofpoint.json.JsonCodec.jsonCodec;
import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.NOT_MODIFIED;
import static javax.ws.rs.core.Response.Status.OK;

public class HttpDiscoveryClient implements DiscoveryClient
{
    private final String environment;
    private final URI discoveryServiceURI;
    private final NodeInfo nodeInfo;
    private final JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec;
    private final JsonCodec<Announcement> announcementCodec;
    private final HttpClient httpClient;

    public HttpDiscoveryClient(DiscoveryClientConfig config,
            NodeInfo nodeInfo,
            HttpClient httpClient)
    {
        this(config, nodeInfo, jsonCodec(ServiceDescriptorsRepresentation.class), jsonCodec(Announcement.class), httpClient);
    }

    @Inject
    public HttpDiscoveryClient(DiscoveryClientConfig config,
            NodeInfo nodeInfo,
            JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec,
            JsonCodec<Announcement> announcementCodec,
            @ForDiscoverClient HttpClient httpClient)
    {
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(nodeInfo, "nodeInfo is null");
        Preconditions.checkNotNull(serviceDescriptorsCodec, "serviceDescriptorsCodec is null");
        Preconditions.checkNotNull(announcementCodec, "announcementCodec is null");
        Preconditions.checkNotNull(httpClient, "httpClient is null");

        this.nodeInfo = nodeInfo;
        this.environment = nodeInfo.getEnvironment();
        this.discoveryServiceURI = config.getDiscoveryServiceURI();
        this.serviceDescriptorsCodec = serviceDescriptorsCodec;
        this.announcementCodec = announcementCodec;
        this.httpClient = httpClient;
    }

    @Override
    public CheckedFuture<Duration, DiscoveryException> announce(Set<ServiceAnnouncement> services)
    {
        Preconditions.checkNotNull(services, "services is null");

        Announcement announcement = new Announcement(nodeInfo.getEnvironment(), nodeInfo.getNodeId(), nodeInfo.getPool(), nodeInfo.getLocation(), services);
        Request request = preparePut()
                .setUri(URI.create(discoveryServiceURI + "/v1/announcement/" + nodeInfo.getNodeId()))
                .setHeader("Content-Type", MediaType.APPLICATION_JSON)
                .setBodyGenerator(jsonBodyGenerator(announcementCodec, announcement))
                .build();
        return httpClient.execute(request, new DiscoveryResponseHandler<Duration>("Announcement")
        {
            @Override
            public Duration handle(Request request, Response response)
                    throws DiscoveryException
            {
                int statusCode = response.getStatusCode();
                if (!isSuccess(statusCode)) {
                    throw new DiscoveryException(String.format("Announcement failed with status code %s: %s", statusCode, getBodyForError(response)));
                }

                Duration maxAge = extractMaxAge(response);
                return maxAge;
            }
        });
    }

    private boolean isSuccess(int statusCode)
    {
        return statusCode / 100 == 2;
    }

    private static String getBodyForError(Response response)
    {
        try {
            return CharStreams.toString(new InputStreamReader(response.getInputStream(), Charsets.UTF_8));
        }
        catch (IOException e) {
            return "(error getting body)";
        }
    }

    @Override
    public CheckedFuture<Void, DiscoveryException> unannounce()
    {
        Request request = prepareDelete()
                .setUri(URI.create(discoveryServiceURI + "/v1/announcement/" + nodeInfo.getNodeId()))
                .build();
        return httpClient.execute(request, new DiscoveryResponseHandler<Void>("Unannouncement"));
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

        RequestBuilder requestBuilder = prepareGet().setUri(URI.create(discoveryServiceURI + "/v1/service/" + type + "/" + pool));
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

    public static class ServiceDescriptorsRepresentation
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
            final StringBuilder sb = new StringBuilder();
            sb.append("ServiceDescriptorsRepresentation");
            sb.append("{environment='").append(environment).append('\'');
            sb.append(", serviceDescriptorList=").append(serviceDescriptors);
            sb.append('}');
            return sb.toString();
        }
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
