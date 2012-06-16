package io.airlift.discovery.client;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.configuration.ConfigurationModule;
import io.airlift.json.JsonModule;
import io.airlift.node.testing.TestingNodeModule;
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

        // should produce a discovery announcement client and a lookup client
        Assert.assertNotNull(injector.getInstance(DiscoveryAnnouncementClient.class));
        Assert.assertNotNull(injector.getInstance(DiscoveryLookupClient.class));
        // should produce an Announcer
        Assert.assertNotNull(injector.getInstance(Announcer.class));
    }

}
