package com.proofpoint.experimental.discovery.client;

import java.util.List;

public interface ServiceSelector
{
    String getType();
    String getPool();
    ServiceDescriptor selectService();
    List<ServiceDescriptor> selectAllServices();
}
