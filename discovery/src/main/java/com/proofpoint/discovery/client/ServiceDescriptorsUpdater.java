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

import com.google.common.util.concurrent.CheckedFuture;
import com.proofpoint.log.Logger;
import com.proofpoint.units.Duration;

import javax.annotation.PostConstruct;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.proofpoint.discovery.client.announce.DiscoveryAnnouncementClient.DEFAULT_DELAY;

public final class ServiceDescriptorsUpdater
{
    private final static Logger log = Logger.get(ServiceDescriptorsUpdater.class);

    private final ServiceDescriptorsUpdateable target;
    private final String type;
    private final String pool;
    private final DiscoveryLookupClient discoveryClient;
    private final AtomicReference<ServiceDescriptors> serviceDescriptors = new AtomicReference<>();
    private final ScheduledExecutorService executor;

    private final AtomicBoolean serverUp = new AtomicBoolean(true);
    private final AtomicBoolean started = new AtomicBoolean(false);

    public ServiceDescriptorsUpdater(ServiceDescriptorsUpdateable target, String type, ServiceSelectorConfig selectorConfig, DiscoveryLookupClient discoveryClient, ScheduledExecutorService executor)
    {
        checkNotNull(target, "target is null");
        checkNotNull(type, "type is null");
        checkNotNull(selectorConfig, "selectorConfig is null");
        checkNotNull(discoveryClient, "discoveryClient is null");
        checkNotNull(executor, "executor is null");

        this.target = target;
        this.type = type;
        this.pool = selectorConfig.getPool();
        this.discoveryClient = discoveryClient;
        this.executor = executor;
    }

    @PostConstruct
    public void start()
    {
        if (started.compareAndSet(false, true)) {
            checkState(!executor.isShutdown(), "CachingServiceSelector has been destroyed");

            // if discovery is available, get the initial set of servers before starting
            try {
                refresh().checkedGet(30, TimeUnit.SECONDS);
            }
            catch (Exception ignored) {
            }
        }
    }

    private CheckedFuture<ServiceDescriptors, DiscoveryException> refresh()
    {
        final ServiceDescriptors oldDescriptors = this.serviceDescriptors.get();

        final CheckedFuture<ServiceDescriptors, DiscoveryException> future;
        if (oldDescriptors == null) {
            future = discoveryClient.getServices(type, pool);
        }
        else {
            future = discoveryClient.refreshServices(oldDescriptors);
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
                    target.updateServiceDescriptors(newDescriptors.getServiceDescriptors());
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
