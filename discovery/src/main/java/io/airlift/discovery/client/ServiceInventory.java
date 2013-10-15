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
package io.airlift.discovery.client;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request.Builder;
import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;
import io.airlift.node.NodeInfo;
import io.airlift.units.Duration;
import org.weakref.jmx.Managed;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.collect.Lists.newArrayList;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static java.nio.file.Files.readAllBytes;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

public class ServiceInventory
{
    private static final Logger log = Logger.get(ServiceInventory.class);

    private final String environment;
    private final URI serviceInventoryUri;
    private final Duration updateInterval;
    private final NodeInfo nodeInfo;
    private final JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec;
    private final HttpClient httpClient;

    private final AtomicReference<List<ServiceDescriptor>> serviceDescriptors = new AtomicReference<List<ServiceDescriptor>>(ImmutableList.<ServiceDescriptor>of());
    private final ScheduledExecutorService executorService = newSingleThreadScheduledExecutor(daemonThreadsNamed("service-inventory-%s"));
    private final AtomicBoolean serverUp = new AtomicBoolean(true);
    private ScheduledFuture<?> scheduledFuture;

    @Inject
    public ServiceInventory(ServiceInventoryConfig config,
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
        updateInterval = config.getUpdateInterval();
        this.serviceDescriptorsCodec = serviceDescriptorsCodec;
        this.httpClient = httpClient;

        if (serviceInventoryUri != null) {
            String scheme = serviceInventoryUri.getScheme().toLowerCase();
            Preconditions.checkArgument(scheme.equals("http") || scheme.equals("https") || scheme.equals("file"), "Service inventory uri must have a http, https, or file scheme");

            try {
                updateServiceInventory();
            }
            catch (Exception ignored) {
            }
        }
    }

    @PostConstruct
    public synchronized void start()
    {
        if (serviceInventoryUri == null || scheduledFuture != null) {
            return;
        }
        scheduledFuture = executorService.scheduleAtFixedRate(new Runnable()
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
        }, updateInterval.toMillis(), updateInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public synchronized void stop()
    {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
            scheduledFuture = null;
        }
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

    @Managed
    public final void updateServiceInventory()
    {
        if (serviceInventoryUri == null) {
            return;
        }

        try {
            ServiceDescriptorsRepresentation serviceDescriptorsRepresentation;
            if (serviceInventoryUri.getScheme().toLowerCase().startsWith("http")) {
                Builder requestBuilder = prepareGet()
                        .setUri(serviceInventoryUri)
                        .setHeader("User-Agent", nodeInfo.getNodeId());
                serviceDescriptorsRepresentation = httpClient.execute(requestBuilder.build(), createJsonResponseHandler(serviceDescriptorsCodec));
            }
            else {
                File file = new File(serviceInventoryUri);
                serviceDescriptorsRepresentation = serviceDescriptorsCodec.fromJson(readAllBytes(file.toPath()));
            }

            if (!environment.equals(serviceDescriptorsRepresentation.getEnvironment())) {
                logServerError("Expected environment to be %s, but was %s", environment, serviceDescriptorsRepresentation.getEnvironment());
            }

            List<ServiceDescriptor> descriptors = newArrayList(serviceDescriptorsRepresentation.getServiceDescriptors());
            Collections.shuffle(descriptors);
            serviceDescriptors.set(ImmutableList.copyOf(descriptors));

            if (serverUp.compareAndSet(false, true)) {
                log.info("ServiceInventory connect succeeded");
            }
        }
        catch (Exception e) {
            logServerError("Error loading service inventory from %s", serviceInventoryUri.toASCIIString());
        }
    }

    private void logServerError(String message, Object... args)
    {
        if (serverUp.compareAndSet(true, false)) {
            log.error(message, args);
        }
    }
}
