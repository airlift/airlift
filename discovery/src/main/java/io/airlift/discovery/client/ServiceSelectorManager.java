package io.airlift.discovery.client;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.CheckedFuture;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    public void refresh()
    {
        List<CheckedFuture<ServiceDescriptors, DiscoveryException>> futures = new ArrayList<>();

        for (ServiceSelector selector : serviceSelectors) {
            if (selector instanceof CachingServiceSelector) {
                futures.add(((CachingServiceSelector) selector).refresh());
            }
        }

        for (CheckedFuture<ServiceDescriptors, DiscoveryException> future : futures) {
            future.checkedGet();
        }
    }
}
