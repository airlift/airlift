package io.airlift.discovery.client;

public class TestDiscoveryModule
        extends AbstractTestDiscoveryModule
{
    protected TestDiscoveryModule()
    {
        super(new DiscoveryModule());
    }
}
