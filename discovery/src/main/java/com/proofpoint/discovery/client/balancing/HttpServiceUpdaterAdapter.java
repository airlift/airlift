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
package com.proofpoint.discovery.client.balancing;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.discovery.client.ServiceDescriptorsListener;
import com.proofpoint.http.client.balancing.HttpServiceBalancerImpl;

import java.net.URI;
import java.net.URISyntaxException;

public class HttpServiceUpdaterAdapter
        implements ServiceDescriptorsListener
{

    private final HttpServiceBalancerImpl balancer;

    public HttpServiceUpdaterAdapter(HttpServiceBalancerImpl balancer)
    {
        this.balancer = balancer;
    }

    @Override
    public void updateServiceDescriptors(Iterable<ServiceDescriptor> newDescriptors)
    {
        Builder<URI> builder = ImmutableSet.builder();

        for (ServiceDescriptor serviceDescriptor : newDescriptors) {
            String https = serviceDescriptor.getProperties().get("https");
            if (https != null) {
                try {
                    builder.add(new URI(https));
                    continue;
                }
                catch (URISyntaxException ignored) {
                }
            }

            String http = serviceDescriptor.getProperties().get("http");
            if (http != null) {
                try {
                    builder.add(new URI(http));
                }
                catch (URISyntaxException ignored) {
                }
            }
        }

        balancer.updateHttpUris(builder.build());
    }
}
