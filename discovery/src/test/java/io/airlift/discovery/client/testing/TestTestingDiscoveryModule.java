package io.airlift.discovery.client.testing;

import io.airlift.discovery.client.AbstractTestDiscoveryModule;

public class TestTestingDiscoveryModule
    extends AbstractTestDiscoveryModule
{
    protected TestTestingDiscoveryModule()
    {
        super(new TestingDiscoveryModule());
    }
}
