package com.proofpoint.experimental.discovery.client;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.CheckedFuture;
import com.proofpoint.log.Logger;
import com.proofpoint.units.Duration;

import javax.annotation.PostConstruct;
import java.net.ConnectException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.String.format;

public class ServiceSelectorImpl implements ServiceSelector
{
    private final static Logger log = Logger.get(ServiceSelectorImpl.class);
    private static final Random random = new SecureRandom();

    private final String type;
    private final String pool;
    private final DiscoveryClient client;
    private final AtomicReference<ServiceDescriptors> serviceDescriptors = new AtomicReference<ServiceDescriptors>();
    private final ScheduledExecutorService executor;

    public ServiceSelectorImpl(String type, ServiceSelectorConfig selectorConfig, DiscoveryClient client, ScheduledExecutorService executor)
    {
        Preconditions.checkNotNull(type, "type is null");
        Preconditions.checkNotNull(selectorConfig, "selectorConfig is null");
        Preconditions.checkNotNull(client, "client is null");
        Preconditions.checkNotNull(executor, "executor is null");

        this.type = type;
        this.pool = selectorConfig.getPool();
        this.client = client;
        this.executor = executor;
    }

    @PostConstruct
    public void start()
    {
        // make sure update runs at least every minutes
        // this will help the system restart if a task
        // hangs or dies without being rescheduled
        executor.scheduleWithFixedDelay(new Runnable()
        {
            @Override
            public void run()
            {
                scheduleRefresh(new Duration(0, TimeUnit.SECONDS));
            }
        }, 0, 1, TimeUnit.MINUTES);
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
    public ServiceDescriptor selectService()
    {
        List<ServiceDescriptor> services = selectAllServices();
        if (services.isEmpty()) {
            throw new IllegalStateException(format("No %s services from pool %s available", type, pool));
        }
        int index = random.nextInt(services.size());
        return services.get(index);
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

    private void scheduleRefresh(Duration delay)
    {
        final ServiceDescriptors oldDescriptors = this.serviceDescriptors.get();
        executor.schedule(new Callable<Void>()
        {
            @Override
            public Void call()
                    throws Exception
            {
                final CheckedFuture<ServiceDescriptors, DiscoveryException> future;
                if (oldDescriptors == null) {
                    future = client.getServices(type, pool);
                }
                else {
                    future = client.refreshServices(oldDescriptors);
                }

                future.addListener(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        try {
                            ServiceDescriptors newDescriptors = future.checkedGet();
                            boolean updated = serviceDescriptors.compareAndSet(oldDescriptors, newDescriptors);
                            if (updated) {
                                scheduleRefresh(newDescriptors.getMaxAge());
                            }
                        }
                        catch (DiscoveryException e) {
                            if (Throwables.getRootCause(e) instanceof ConnectException) {
                                log.debug(e, "Can not connect to discovery server");
                            } else {
                                log.error(e);
                            }
                        }
                    }
                }, executor);

                return null;

            }
        }, (long) delay.toMillis(), TimeUnit.MILLISECONDS);
    }
}
