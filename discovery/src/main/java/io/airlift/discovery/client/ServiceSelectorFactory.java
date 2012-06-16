package io.airlift.discovery.client;

public interface ServiceSelectorFactory
{
    ServiceSelector createServiceSelector(String type, ServiceSelectorConfig selectorConfig);
}
