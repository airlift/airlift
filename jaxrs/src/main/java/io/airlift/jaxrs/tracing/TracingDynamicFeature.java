package io.airlift.jaxrs.tracing;

import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.FeatureContext;

public class TracingDynamicFeature
        implements DynamicFeature
{
    @Override
    public void configure(ResourceInfo resourceInfo, FeatureContext context)
    {
        context.register(new TracingFilter(
                resourceInfo.getResourceClass().getName(),
                resourceInfo.getResourceMethod().getName()));
    }
}
