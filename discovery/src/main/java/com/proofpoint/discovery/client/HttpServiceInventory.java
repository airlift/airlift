package com.proofpoint.discovery.client;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestBuilder;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.collect.Lists.newArrayList;
import static com.proofpoint.http.client.RequestBuilder.prepareGet;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static javax.ws.rs.core.Response.Status.OK;

public class HttpServiceInventory
{
    private static final Logger log = Logger.get(HttpServiceInventory.class);

    private final String environment;
    private final URI serviceInventoryUri;
    private final NodeInfo nodeInfo;
    private final JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec;
    private final HttpClient httpClient;

    private final AtomicReference<List<ServiceDescriptor>> serviceDescriptors = new AtomicReference<List<ServiceDescriptor>>(ImmutableList.<ServiceDescriptor>of());
    private final ScheduledExecutorService executorService = newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("service-inventory-%s").setDaemon(true).build());
    private final AtomicBoolean serverUp = new AtomicBoolean(true);

    @Inject
    public HttpServiceInventory(ServiceInventoryConfig config,
            NodeInfo nodeInfo,
            JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec,
            @ForDiscoveryClient HttpClient httpClient)
    {
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(nodeInfo, "nodeInfo is null");
        Preconditions.checkNotNull(serviceDescriptorsCodec, "serviceDescriptorsCodec is null");
        Preconditions.checkNotNull(httpClient, "httpClient is null");

        this.nodeInfo = nodeInfo;
        this.environment = nodeInfo.getEnvironment();
        this.serviceInventoryUri = config.getServiceInventoryUri();
        this.serviceDescriptorsCodec = serviceDescriptorsCodec;
        this.httpClient = httpClient;

        if (serviceInventoryUri != null) {
            try {
                updateServiceInventory().checkedGet(10, TimeUnit.SECONDS);
            }
            catch (Exception ignored) {
            }
        }
    }

    @PostConstruct
    public void start()
    {
        if (serviceInventoryUri == null) {
            return;
        }
        executorService.scheduleAtFixedRate(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    updateServiceInventory();
                }
                catch (Throwable e) {
                    log.error(e, "Unexpected exception from service inventory update");
                }
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    public Iterable<ServiceDescriptor> getServiceDescriptors()
    {
        return serviceDescriptors.get();
    }

    public Iterable<ServiceDescriptor> getServiceDescriptors(final String type)
    {
        return Iterables.filter(getServiceDescriptors(), new Predicate<ServiceDescriptor>()
        {
            @Override
            public boolean apply(ServiceDescriptor serviceDescriptor)
            {
                return serviceDescriptor.getType().equals(type);
            }
        });
    }

    public Iterable<ServiceDescriptor> getServiceDescriptors(final String type, final String pool)
    {
        return Iterables.filter(getServiceDescriptors(), new Predicate<ServiceDescriptor>()
        {
            @Override
            public boolean apply(ServiceDescriptor serviceDescriptor)
            {
                return serviceDescriptor.getType().equals(type) &&
                        serviceDescriptor.getPool().equals(pool);
            }
        });
    }

    private CheckedFuture<Void, RuntimeException> updateServiceInventory()
    {
        RequestBuilder requestBuilder = prepareGet()
                .setUri(serviceInventoryUri)
                .setHeader("User-Agent", nodeInfo.getNodeId());

        return httpClient.execute(requestBuilder.build(), new ServiceInventoryResponseHandler(environment, serviceDescriptors, serverUp, serviceDescriptorsCodec));
    }

    private static class ServiceInventoryResponseHandler implements ResponseHandler<Void, RuntimeException>
    {
        private final String environment;
        private final JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec;
        private final AtomicReference<List<ServiceDescriptor>> serviceDescriptors;
        private final AtomicBoolean serverUp;

        private ServiceInventoryResponseHandler(String environment,
                AtomicReference<List<ServiceDescriptor>> serviceDescriptors,
                AtomicBoolean serverUp,
                JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec)
        {
            this.environment = environment;
            this.serviceDescriptorsCodec = serviceDescriptorsCodec;
            this.serviceDescriptors = serviceDescriptors;
            this.serverUp = serverUp;
        }

        @Override
        public RuntimeException handleException(Request request, Exception exception)
        {
            if (serverUp.compareAndSet(true, false) && !log.isDebugEnabled()) {
                log.error("ServiceInventory failed: %s", exception.getMessage());
            } else {
                log.debug(exception, "ServiceInventory failed");
            }
            return null;
        }

        @Override
        public Void handle(Request request, Response response)
        {
            if (OK.getStatusCode() != response.getStatusCode()) {
                logServerError("ServiceInventory failed with status code %s", response.getStatusCode());
                return null;
            }

            String json;
            try {
                json = CharStreams.toString(new InputStreamReader(response.getInputStream(), Charsets.UTF_8));
            }
            catch (IOException e) {
                logServerError("Invalid ServiceInventory json");
                return null;
            }

            ServiceDescriptorsRepresentation serviceDescriptorsRepresentation = serviceDescriptorsCodec.fromJson(json);
            if (!environment.equals(serviceDescriptorsRepresentation.getEnvironment())) {
                logServerError("Expected environment to be %s, but was %s", environment, serviceDescriptorsRepresentation.getEnvironment());
                return null;
            }

            List<ServiceDescriptor> descriptors = newArrayList(serviceDescriptorsRepresentation.getServiceDescriptors());
            Collections.shuffle(descriptors);
            serviceDescriptors.set(ImmutableList.copyOf(descriptors));

            if (serverUp.compareAndSet(false, true)) {
                log.info("ServiceInventory connect succeeded");
            }

            return null;
        }

        private void logServerError(String message, Object... args)
        {
            if (serverUp.compareAndSet(true, false)) {
                log.error(message, args);
            }
        }
    }
}
