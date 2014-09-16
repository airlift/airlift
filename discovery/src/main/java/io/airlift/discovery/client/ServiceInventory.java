package io.airlift.discovery.client;

public interface ServiceInventory
{
    Iterable<ServiceDescriptor> getServiceDescriptors();

    Iterable<ServiceDescriptor> getServiceDescriptors(String type);

    Iterable<ServiceDescriptor> getServiceDescriptors(String type, String pool);
}
