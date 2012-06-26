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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.CheckedFuture;
import com.proofpoint.log.Logger;
import com.proofpoint.units.Duration;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.proofpoint.discovery.client.DiscoveryAnnouncementClient.DEFAULT_DELAY;

public class CachingServiceSelector implements ServiceSelector
{
    private final static Logger log = Logger.get(CachingServiceSelector.class);

    private final String type;
    private final String pool;
    private final DiscoveryLookupClient lookupClient;
    private final AtomicReference<ServiceDescriptors> serviceDescriptors = new AtomicReference<ServiceDescriptors>();
    private final ScheduledExecutorService executor;
    private final AtomicBoolean serverUp = new AtomicBoolean(true);

    private final AtomicBoolean started = new AtomicBoolean(false);

    public CachingServiceSelector(String type, ServiceSelectorConfig selectorConfig, DiscoveryLookupClient lookupClient, ScheduledExecutorService executor)
    {
        Preconditions.checkNotNull(type, "type is null");
        Preconditions.checkNotNull(selectorConfig, "selectorConfig is null");
        Preconditions.checkNotNull(lookupClient, "client is null");
        Preconditions.checkNotNull(executor, "executor is null");

        this.type = type;
        this.pool = selectorConfig.getPool();
        this.lookupClient = lookupClient;
        this.executor = executor;
    }

    @PostConstruct
    public void start()
            throws TimeoutException
    {
        if (started.compareAndSet(false, true)) {
            Preconditions.checkState(!executor.isShutdown(), "CachingServiceSelector has been destroyed");

            // if discovery is available, get the initial set of servers before starting
            try {
                refresh().checkedGet(30, TimeUnit.SECONDS);
            }
            catch (Exception ignored) {
            }
        }
    }

    @Override
    public String getType()
    {
        return type;
    }

    @Override
    public String getPool()
    {
        return pool;
    }

    @Override
    public List<ServiceDescriptor> selectAllServices()
    {
        ServiceDescriptors serviceDescriptors = this.serviceDescriptors.get();
        if (serviceDescriptors == null) {
            return ImmutableList.of();
        }
        return serviceDescriptors.getServiceDescriptors();
    }

    private CheckedFuture<ServiceDescriptors, DiscoveryException> refresh()
    {
        final ServiceDescriptors oldDescriptors = this.serviceDescriptors.get();

        final CheckedFuture<ServiceDescriptors, DiscoveryException> future;
        if (oldDescriptors == null) {
            future = lookupClient.getServices(type, pool);
        }
        else {
            future = lookupClient.refreshServices(oldDescriptors);
        }

        future.addListener(new Runnable()
        {
            @Override
            public void run()
            {
                Duration delay = DEFAULT_DELAY;
                try {
                    ServiceDescriptors newDescriptors = future.checkedGet();
                    delay = newDescriptors.getMaxAge();
                    serviceDescriptors.set(newDescriptors);
                    if (serverUp.compareAndSet(false, true)) {
                        log.info("Discovery server connect succeeded for refresh (%s/%s)", type, pool);
                    }
                }
                catch (DiscoveryException e) {
                    if (serverUp.compareAndSet(true, false)) {
                        log.error("Cannot connect to discovery server for refresh (%s/%s): %s", type, pool, e.getMessage());
                    }
                    log.debug(e, "Cannot connect to discovery server for refresh (%s/%s)", type, pool);
                }
                finally {
                    scheduleRefresh(delay);
                }
            }
        }, executor);

        return future;
    }


    private void scheduleRefresh(Duration delay)
    {
        // already stopped?  avoids rejection exception
        if (executor.isShutdown()) {
            return;
        }
        executor.schedule(new Runnable() {
            @Override
            public void run()
            {
                refresh();
            }
        }, (long) delay.toMillis(), TimeUnit.MILLISECONDS);
    }
}
