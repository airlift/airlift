package com.proofpoint.experimental.discovery.client;

public interface ServiceSelectorFactory
{
    ServiceSelector createServiceSelector(String type, ServiceSelectorConfig selectorConfig);
}
