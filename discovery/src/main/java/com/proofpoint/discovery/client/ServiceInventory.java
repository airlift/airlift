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

import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.proofpoint.http.client.balancing.HttpServiceBalancerImpl;
import com.proofpoint.discovery.client.balancing.HttpServiceUpdaterAdapter;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

public class ServiceInventory
{
    private static final Logger log = Logger.get(ServiceInventory.class);

    private final String environment;
    private final URI serviceInventoryUri;
    private final Duration updateInterval;
    private final JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec;
    private final ServiceDescriptorsListener discoveryUpdateable;

    private final AtomicReference<List<ServiceDescriptor>> serviceDescriptors = new AtomicReference<List<ServiceDescriptor>>(ImmutableList.<ServiceDescriptor>of());
    private final ScheduledExecutorService executorService = newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("service-inventory-%s").setDaemon(true).build());
    private final AtomicBoolean serverUp = new AtomicBoolean(true);
    private ScheduledFuture<?> scheduledFuture = null;

    @Inject
    public ServiceInventory(ServiceInventoryConfig serviceInventoryConfig,
            DiscoveryClientConfig discoveryClientConfig,
            NodeInfo nodeInfo,
            JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec,
            @ServiceType("discovery") HttpServiceBalancerImpl discoveryBalancer)
    {
        checkNotNull(serviceInventoryConfig, "serviceInventoryConfig is null");
        checkNotNull(discoveryClientConfig, "discoveryClientConfig is null");
        checkNotNull(serviceDescriptorsCodec, "serviceDescriptorsCodec is null");
        checkNotNull(discoveryBalancer, "discoveryBalancer is null");

        this.environment = nodeInfo.getEnvironment();
        this.serviceInventoryUri = serviceInventoryConfig.getServiceInventoryUri();
        updateInterval = serviceInventoryConfig.getUpdateInterval();
        this.serviceDescriptorsCodec = serviceDescriptorsCodec;
        this.discoveryUpdateable = new HttpServiceUpdaterAdapter(discoveryBalancer);

        if (serviceInventoryUri != null) {
            String scheme = serviceInventoryUri.getScheme().toLowerCase();
            checkArgument(scheme.equals("file"), "Service inventory uri must have a file scheme");

            try {
                updateServiceInventory();
            }
            catch (Exception ignored) {
            }
        }
        else {
            @SuppressWarnings("deprecation") URI uri = discoveryClientConfig.getDiscoveryServiceURI();
            if (uri != null) {
                discoveryBalancer.updateHttpUris(ImmutableSet.of(uri));
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
        }, (long) updateInterval.toMillis(), (long) updateInterval.toMillis(), TimeUnit.MILLISECONDS);
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
            File file = new File(serviceInventoryUri);
            String json = Files.toString(file, Charsets.UTF_8);
            serviceDescriptorsRepresentation = serviceDescriptorsCodec.fromJson(json);

            if (!environment.equals(serviceDescriptorsRepresentation.getEnvironment())) {
                logServerError("Expected environment to be %s, but was %s", environment, serviceDescriptorsRepresentation.getEnvironment());
            }

            List<ServiceDescriptor> descriptors = newArrayList(serviceDescriptorsRepresentation.getServiceDescriptors());
            Collections.shuffle(descriptors);
            serviceDescriptors.set(ImmutableList.copyOf(descriptors));
            discoveryUpdateable.updateServiceDescriptors(Collections2.filter(descriptors, new Predicate<ServiceDescriptor>()
            {
                @Override
                public boolean apply(ServiceDescriptor input)
                {
                    return "discovery".equals(input.getType());
                }
            }));

            if (serverUp.compareAndSet(false, true)) {
                log.info("ServiceInventory connect succeeded");
            }
        }
        catch (Exception e) {
            logServerError(e, "Error loading service inventory from %s", serviceInventoryUri.toASCIIString());
        }
    }

    private void logServerError(String message, Object... args)
    {
        if (serverUp.compareAndSet(true, false)) {
            log.error(message, args);
        }
    }
    private void logServerError(Exception e, String message, Object... args)
    {
        if (serverUp.compareAndSet(true, false)) {
            log.error(e, message, args);
        }
    }

}
