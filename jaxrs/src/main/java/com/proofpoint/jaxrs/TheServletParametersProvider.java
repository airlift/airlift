package com.proofpoint.jaxrs;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.sun.jersey.spi.container.ResourceFilterFactory;

import java.util.Map;
import java.util.Set;

public class TheServletParametersProvider implements Provider<Map<String, String>>
{
    public static final String JERSEY_CONTAINER_REQUEST_FILTERS = "com.sun.jersey.spi.container.ContainerRequestFilters";
    public static final String JERSEY_RESOURCE_FILTERS = "com.sun.jersey.spi.container.ResourceFilters";
    private Set<ResourceFilterFactory> resourceFilterFactorySet = null;

    @Inject(optional = true)
    public void setResourceFilterFactories(Set<ResourceFilterFactory> resourceFilterFactorySet)
    {
        this.resourceFilterFactorySet = ImmutableSet.copyOf(resourceFilterFactorySet);
    }

    @Override
    public Map<String, String> get()
    {
        Builder<String, String> builder = ImmutableMap.builder();
        builder.put(JERSEY_CONTAINER_REQUEST_FILTERS, OverrideMethodFilter.class.getName());
        if ((resourceFilterFactorySet != null) && !resourceFilterFactorySet.isEmpty()) {
            builder.put(JERSEY_RESOURCE_FILTERS, join(resourceFilterFactorySet));
        }
        return builder.build();
    }

    private String join(Set<ResourceFilterFactory> resourceFilterFactorySet)
    {
        StringBuilder stringBuilder = new StringBuilder();
        boolean first = true;
        if ((resourceFilterFactorySet != null) && (!resourceFilterFactorySet.isEmpty())) {
            for (ResourceFilterFactory factory : resourceFilterFactorySet) {
                if (first) {
                    first = false;
                }
                else {
                    stringBuilder.append(",");
                }
                stringBuilder.append(factory.getClass().getName());
            }
        }
        return stringBuilder.toString();
    }
}
