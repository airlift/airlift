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
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.airlift.log.Logger;
import io.airlift.units.Duration;

import javax.annotation.PostConstruct;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static io.airlift.discovery.client.DiscoveryAnnouncementClient.DEFAULT_DELAY;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class CachingServiceSelector
        implements ServiceSelector
{
    private static final Logger log = Logger.get(CachingServiceSelector.class);

    private final String type;
    private final String pool;
    private final DiscoveryLookupClient lookupClient;
    private final AtomicReference<ServiceDescriptors> serviceDescriptors = new AtomicReference<>();
    private final ScheduledExecutorService executor;

    private final ExponentialBackOff errorBackOff;

    private final AtomicBoolean started = new AtomicBoolean(false);

    public CachingServiceSelector(String type, ServiceSelectorConfig selectorConfig, DiscoveryLookupClient lookupClient, ScheduledExecutorService executor)
    {
        requireNonNull(type, "type is null");
        requireNonNull(selectorConfig, "selectorConfig is null");
        requireNonNull(lookupClient, "client is null");
        requireNonNull(executor, "executor is null");

        this.type = type;
        this.pool = selectorConfig.getPool();
        this.lookupClient = lookupClient;
        this.executor = executor;
        this.errorBackOff = new ExponentialBackOff(
                new Duration(1, MILLISECONDS),
                new Duration(1, SECONDS),
                String.format("Discovery server connect succeeded for refresh (%s/%s)", type, pool),
                String.format("Cannot connect to discovery server for refresh (%s/%s)", type, pool),
                log);
    }

    @PostConstruct
    public void start()
    {
        if (started.compareAndSet(false, true)) {
            Preconditions.checkState(!executor.isShutdown(), "CachingServiceSelector has been destroyed");

            // if discovery is available, get the initial set of servers before starting
            try {
                refresh().get(1, TimeUnit.SECONDS);
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

    @Override
    public ListenableFuture<List<ServiceDescriptor>> refresh()
    {
        ServiceDescriptors oldDescriptors = this.serviceDescriptors.get();

        ListenableFuture<ServiceDescriptors> future;
        if (oldDescriptors == null) {
            future = lookupClient.getServices(type, pool);
        }
        else {
            future = lookupClient.refreshServices(oldDescriptors);
        }

        future = chainedCallback(future, new FutureCallback<ServiceDescriptors>()
        {
            @Override
            public void onSuccess(ServiceDescriptors newDescriptors)
            {
                serviceDescriptors.set(newDescriptors);
                errorBackOff.success();

                Duration delay = newDescriptors.getMaxAge();
                if (delay == null) {
                    delay = DEFAULT_DELAY;
                }
                scheduleRefresh(delay);
            }

            @Override
            public void onFailure(Throwable t)
            {
                Duration duration = errorBackOff.failed(t);
                scheduleRefresh(duration);
            }
        }, executor);

        return Futures.transform(future, ServiceDescriptors::getServiceDescriptors, directExecutor());
    }

    private void scheduleRefresh(Duration delay)
    {
        // already stopped?  avoids rejection exception
        if (executor.isShutdown()) {
            return;
        }
        executor.schedule(this::refresh, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static <V> ListenableFuture<V> chainedCallback(
            ListenableFuture<V> future,
            final FutureCallback<? super V> callback,
            Executor executor)
    {
        final SettableFuture<V> done = SettableFuture.create();
        Futures.addCallback(future, new FutureCallback<V>()
        {
            @Override
            public void onSuccess(V result)
            {
                try {
                    callback.onSuccess(result);
                }
                finally {
                    done.set(result);
                }
            }

            @Override
            public void onFailure(Throwable t)
            {
                try {
                    callback.onFailure(t);
                }
                finally {
                    done.setException(t);
                }
            }
        }, executor);
        return done;
    }
}
