package io.airlift.discovery.client;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import io.airlift.node.NodeInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.util.Objects.requireNonNull;

public class MergingServiceSelector
        implements ServiceSelector
{
    private final ServiceSelector selector;
    private final Announcer announcer;
    private final NodeInfo nodeInfo;

    public MergingServiceSelector(ServiceSelector selector, Announcer announcer, NodeInfo nodeInfo)
    {
        this.selector = requireNonNull(selector, "selector is null");
        this.announcer = requireNonNull(announcer, "announcer is null");
        this.nodeInfo = requireNonNull(nodeInfo, "nodeInfo is null");
    }

    @Override
    public String getType()
    {
        return selector.getType();
    }

    @Override
    public String getPool()
    {
        return selector.getPool();
    }

    @Override
    public List<ServiceDescriptor> selectAllServices()
    {
        return merge(announcer.getServiceAnnouncements(), selector.selectAllServices());
    }

    @Override
    public ListenableFuture<List<ServiceDescriptor>> refresh()
    {
        return FluentFuture.from(selector.refresh())
                .transform(descriptors -> merge(announcer.getServiceAnnouncements(), descriptors), directExecutor());
    }

    private List<ServiceDescriptor> merge(Set<ServiceAnnouncement> serviceAnnouncements, List<ServiceDescriptor> serviceDescriptors)
    {
        Set<ServiceDescriptor> set = new HashSet<>();
        for (ServiceAnnouncement announcement : serviceAnnouncements) {
            ServiceDescriptor descriptor = announcement.toServiceDescriptor(nodeInfo);
            if (descriptor.type().equals(getType()) && descriptor.pool().equals(getPool())) {
                set.add(descriptor);
            }
        }
        set.addAll(serviceDescriptors);
        return ImmutableList.copyOf(set);
    }
}
