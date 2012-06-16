package io.airlift.discovery.client;

import java.util.List;

public interface ServiceSelector
{
    String getType();
    String getPool();
    List<ServiceDescriptor> selectAllServices();
}
