package io.airlift.discovery.client;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import javax.inject.Inject;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static java.util.Objects.requireNonNull;

@Beta
public class ServiceSelectorManager
{
    private final Set<ServiceSelector> serviceSelectors;

    @Inject
    public ServiceSelectorManager(Set<ServiceSelector> serviceSelectors)
    {
        requireNonNull(serviceSelectors, "serviceSelectors is null");
        this.serviceSelectors = ImmutableSet.copyOf(serviceSelectors);
    }

    public Set<ServiceSelector> getServiceSelectors()
    {
        return serviceSelectors;
    }

    public void forceRefresh()
    {
        for (ListenableFuture<?> future : initiateRefresh()) {
            Futures.getUnchecked(future);
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

    private List<ListenableFuture<?>> initiateRefresh()
    {
        ImmutableList.Builder<ListenableFuture<?>> futures = ImmutableList.builder();
        for (ServiceSelector selector : serviceSelectors) {
            futures.add(selector.refresh());
        }
        return futures.build();
    }
}
