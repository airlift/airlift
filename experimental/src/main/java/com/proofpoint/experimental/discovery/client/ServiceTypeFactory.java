package com.proofpoint.experimental.discovery.client;

public final class ServiceTypeFactory
{
    public static ServiceType serviceType(String type)
    {
        return serviceType(type, ServiceType.DEFAULT_POOL);
    }
    public static ServiceType serviceType(String type, String pool)
    {
        return new ServiceTypeImpl(type, pool);
    }
}
