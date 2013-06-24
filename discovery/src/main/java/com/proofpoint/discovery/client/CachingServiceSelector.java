/*
 * Copyright 2010 Proofpoint, Inc.
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
package com.proofpoint.discovery.client;

import com.google.common.collect.ImmutableList;
import com.proofpoint.node.NodeInfo;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;

public class CachingServiceSelector
        implements ServiceSelector, ServiceDescriptorsListener
{
    private final String type;
    private final String pool;
    private final AtomicReference<List<ServiceDescriptor>> serviceDescriptors = new AtomicReference<>((List<ServiceDescriptor>) ImmutableList.<ServiceDescriptor>of());

    public CachingServiceSelector(String type, ServiceSelectorConfig selectorConfig, NodeInfo nodeInfo)
    {
        checkNotNull(type, "type is null");
        checkNotNull(selectorConfig, "selectorConfig is null");
        checkNotNull(nodeInfo, "nodeInfo is null");

        this.type = type;
        this.pool = firstNonNull(selectorConfig.getPool(), nodeInfo.getPool());
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
    public List<ServiceDescriptor> selectAllServices()
    {
        return this.serviceDescriptors.get();
    }

    @Override
    public void updateServiceDescriptors(Iterable<ServiceDescriptor> newDescriptors)
    {
        serviceDescriptors.set(ImmutableList.copyOf(checkNotNull(newDescriptors)));
    }

}
