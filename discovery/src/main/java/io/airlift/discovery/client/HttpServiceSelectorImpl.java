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
package io.airlift.discovery.client;

import com.google.common.collect.ImmutableList;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNull;

public class HttpServiceSelectorImpl implements HttpServiceSelector
{
    private final ServiceSelector serviceSelector;

    public HttpServiceSelectorImpl(ServiceSelector serviceSelector)
    {
        requireNonNull(serviceSelector, "serviceSelector is null");
        this.serviceSelector = serviceSelector;
    }

    @Override
    public String getType()
    {
        return serviceSelector.getType();
    }

    @Override
    public String getPool()
    {
        return serviceSelector.getPool();
    }

    @Override
    public List<URI> selectHttpService()
    {
        List<ServiceDescriptor> serviceDescriptors = new ArrayList<>(serviceSelector.selectAllServices());
        if (serviceDescriptors.isEmpty()) {
            return ImmutableList.of();
        }

        // favor https over http
        List<URI> httpsUri = new ArrayList<>();
        for (ServiceDescriptor serviceDescriptor : serviceDescriptors) {
            String https = serviceDescriptor.getProperties().get("https");
            if (https != null) {
                try {
                    httpsUri.add(new URI(https));
                }
                catch (URISyntaxException ignored) {
                }
            }
        }
        List<URI> httpUri = new ArrayList<>();
        for (ServiceDescriptor serviceDescriptor : serviceDescriptors) {
            String http = serviceDescriptor.getProperties().get("http");
            if (http != null) {
                try {
                    httpUri.add(new URI(http));
                }
                catch (URISyntaxException ignored) {
                }
            }
        }

        // return random(https) + random(http)
        Collections.shuffle(httpsUri);
        Collections.shuffle(httpUri);
        return ImmutableList.<URI>builder().addAll(httpsUri).addAll(httpUri).build();
    }
}
