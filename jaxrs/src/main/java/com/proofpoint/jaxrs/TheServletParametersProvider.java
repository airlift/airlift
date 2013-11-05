/*
 * Copyright 2013 Proofpoint, Inc.
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
package com.proofpoint.jaxrs;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.sun.jersey.spi.container.ResourceFilterFactory;

import java.util.Map;
import java.util.Set;

class TheServletParametersProvider
        implements Provider<Map<String, String>>
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
