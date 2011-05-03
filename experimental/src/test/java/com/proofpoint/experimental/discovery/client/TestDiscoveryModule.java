package com.proofpoint.experimental.discovery.client;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.experimental.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestDiscoveryModule
{
    @Test
    public void testBinding()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.of("discovery.uri", "fake://server"))),
                new JsonModule(),
                new TestingNodeModule(),
                new DiscoveryModule()
        );

        // should produce a DiscoveryClient
        Assert.assertNotNull(injector.getInstance(DiscoveryClient.class));
        // should produce an Announcer
        Assert.assertNotNull(injector.getInstance(Announcer.class));
    }

}
