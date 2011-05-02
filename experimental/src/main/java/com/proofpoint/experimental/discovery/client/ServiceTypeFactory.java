package com.proofpoint.experimental.discovery.client;

public final class ServiceTypeFactory
{
    public static ServiceType serviceType(String type)
    {
        return new ServiceTypeImpl(type);
    }
}
