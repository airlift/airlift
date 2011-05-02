package com.proofpoint.experimental.http.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.proofpoint.experimental.discovery.client.ServiceDescriptor;
import com.proofpoint.experimental.discovery.client.ServiceSelector;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

import static java.lang.String.format;

public class HttpServiceSelector
{
    private final ServiceSelector serviceSelector;

    public HttpServiceSelector(ServiceSelector serviceSelector)
    {
        Preconditions.checkNotNull(serviceSelector, "serviceSelector is null");
        this.serviceSelector = serviceSelector;
    }

    public URI selectHttpService()
    {
        List<ServiceDescriptor> serviceDescriptors = Lists.newArrayList(serviceSelector.selectAllServices());
        Collections.shuffle(serviceDescriptors);

        for (ServiceDescriptor serviceDescriptor : serviceDescriptors) {
            // favor https over http
            String https = serviceDescriptor.getProperties().get("https");
            if (https != null) {
                try {
                    return new URI(https);
                }
                catch (URISyntaxException ignored) {
                }
            }
            String http = serviceDescriptor.getProperties().get("https");
            if (http != null) {
                try {
                    return new URI(http);
                }
                catch (URISyntaxException ignored) {
                }
            }
        }
        throw new IllegalStateException(format("No %s services from pool %s available", serviceSelector.getType(), serviceSelector.getPool()));
    }
}
