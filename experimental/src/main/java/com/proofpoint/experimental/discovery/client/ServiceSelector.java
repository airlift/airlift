package com.proofpoint.experimental.discovery.client;

import java.util.List;

public interface ServiceSelector
{
    ServiceDescriptor selectService();
    List<ServiceDescriptor> selectAllServices();
}
