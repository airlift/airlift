package com.facebook.airlift.discovery.client.testing;

import com.facebook.airlift.discovery.client.AbstractTestDiscoveryModule;

public class TestTestingDiscoveryModule
        extends AbstractTestDiscoveryModule
{
    protected TestTestingDiscoveryModule()
    {
        super(new TestingDiscoveryModule());
    }
}
