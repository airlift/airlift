package io.airlift.discovery.client;

import io.airlift.node.NodeInfo;

import static java.util.Objects.requireNonNull;

public class MergingServiceSelectorFactory
        implements ServiceSelectorFactory
{
    private final ServiceSelectorFactory selectorFactory;
    private final Announcer announcer;
    private final NodeInfo nodeInfo;

    public MergingServiceSelectorFactory(ServiceSelectorFactory selectorFactory, Announcer announcer, NodeInfo nodeInfo)
    {
        this.selectorFactory = requireNonNull(selectorFactory, "selectorFactory is null");
        this.announcer = requireNonNull(announcer, "announcer is null");
        this.nodeInfo = requireNonNull(nodeInfo, "nodeInfo is null");
    }

    @Override
    public ServiceSelector createServiceSelector(String type, ServiceSelectorConfig selectorConfig)
    {
        ServiceSelector selector = selectorFactory.createServiceSelector(type, selectorConfig);
        return new MergingServiceSelector(selector, announcer, nodeInfo);
    }
}
