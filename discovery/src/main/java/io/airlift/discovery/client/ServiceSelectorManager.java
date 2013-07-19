package io.airlift.discovery.client;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;

import javax.inject.Inject;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static com.google.common.base.Preconditions.checkNotNull;

@Beta
public class ServiceSelectorManager
{
    private final Set<ServiceSelector> serviceSelectors;

    @Inject
    public ServiceSelectorManager(Set<ServiceSelector> serviceSelectors)
    {
        checkNotNull(serviceSelectors, "serviceSelectors is null");
        this.serviceSelectors = ImmutableSet.copyOf(serviceSelectors);
    }

    public Set<ServiceSelector> getServiceSelectors()
    {
        return serviceSelectors;
    }

    public void forceRefresh()
    {
        for (CheckedFuture<ServiceDescriptors, DiscoveryException> future : initiateRefresh()) {
            future.checkedGet();
        }
    }

    public void attemptRefresh()
    {
        try {
            Futures.successfulAsList(initiateRefresh()).get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException ignored) {
        }
    }

    private List<CheckedFuture<ServiceDescriptors, DiscoveryException>> initiateRefresh()
    {
        ImmutableList.Builder<CheckedFuture<ServiceDescriptors, DiscoveryException>> futures = ImmutableList.builder();
        for (ServiceSelector selector : serviceSelectors) {
            if (selector instanceof CachingServiceSelector) {
                futures.add(((CachingServiceSelector) selector).refresh());
            }
        }
        return futures.build();
    }
}
