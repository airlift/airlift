package io.airlift.discovery.client;

import com.google.common.collect.ImmutableList;
import io.airlift.node.NodeInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public class MergingServiceSelector
        implements ServiceSelector
{
    private final ServiceSelector selector;
    private final Announcer announcer;
    private final NodeInfo nodeInfo;

    public MergingServiceSelector(ServiceSelector selector, Announcer announcer, NodeInfo nodeInfo)
    {
        this.selector = checkNotNull(selector, "selector is null");
        this.announcer = checkNotNull(announcer, "announcer is null");
        this.nodeInfo = checkNotNull(nodeInfo, "nodeInfo is null");
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
        Set<ServiceDescriptor> set = new HashSet<>();
        for (ServiceAnnouncement announcement : announcer.getServiceAnnouncements()) {
            ServiceDescriptor descriptor = announcement.toServiceDescriptor(nodeInfo);
            if (descriptor.getType().equals(getType()) && descriptor.getPool().equals(getPool())) {
                set.add(descriptor);
            }
        }
        set.addAll(selector.selectAllServices());
        return ImmutableList.copyOf(set);
    }
}
