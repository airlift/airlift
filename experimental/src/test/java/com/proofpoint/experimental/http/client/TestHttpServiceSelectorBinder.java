package com.proofpoint.experimental.http.client;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.experimental.discovery.client.DiscoveryClient;
import com.proofpoint.experimental.discovery.client.InMemoryDiscoveryClient;
import com.proofpoint.experimental.discovery.client.InMemoryDiscoveryModule;
import com.proofpoint.node.testing.TestingNodeModule;
import org.testng.annotations.Test;

import java.net.URI;

import static com.proofpoint.experimental.discovery.client.ServiceAnnouncement.serviceAnnouncement;
import static com.proofpoint.experimental.discovery.client.ServiceTypes.serviceType;
import static com.proofpoint.experimental.http.client.HttpServiceSelectorBinder.httpServiceSelectorBinder;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestHttpServiceSelectorBinder
{
    @Test
    public void testHttpSelectorString()
    {
        Injector injector = Guice.createInjector(
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.<String, String>of())),
                new TestingNodeModule(),
                new InMemoryDiscoveryModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        httpServiceSelectorBinder(binder).bindSelector("apple");
                    }
                }
        );

        InMemoryDiscoveryClient discoveryClient = (InMemoryDiscoveryClient) injector.getInstance(DiscoveryClient.class);
        discoveryClient.announce(ImmutableSet.of(serviceAnnouncement("apple").addProperty("http", "fake://server-http").build()));

        HttpServiceSelector selector = injector.getInstance(Key.get(HttpServiceSelector.class, serviceType("apple")));
        assertEquals(selector.selectHttpService(), URI.create("fake://server-http"));
    }

    @Test
    public void testHttpSelectorAnnotation()
    {
        Injector injector = Guice.createInjector(
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.<String, String>of())),
                new TestingNodeModule(),
                new InMemoryDiscoveryModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        httpServiceSelectorBinder(binder).bindSelector(serviceType("apple"));
                    }
                }
        );

        InMemoryDiscoveryClient discoveryClient = (InMemoryDiscoveryClient) injector.getInstance(DiscoveryClient.class);
        discoveryClient.announce(ImmutableSet.of(serviceAnnouncement("apple").addProperty("http", "fake://server-http").build()));

        HttpServiceSelector selector = injector.getInstance(Key.get(HttpServiceSelector.class, serviceType("apple")));
        assertEquals(selector.selectHttpService(), URI.create("fake://server-http"));
    }

    @Test
    public void testHttpsSelector()
    {
        Injector injector = Guice.createInjector(
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.<String, String>of())),
                new TestingNodeModule(),
                new InMemoryDiscoveryModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        httpServiceSelectorBinder(binder).bindSelector("apple");
                    }
                }
        );

        InMemoryDiscoveryClient discoveryClient = (InMemoryDiscoveryClient) injector.getInstance(DiscoveryClient.class);
        discoveryClient.announce(ImmutableSet.of(serviceAnnouncement("apple").addProperty("https", "fake://server-https").build()));

        HttpServiceSelector selector = injector.getInstance(Key.get(HttpServiceSelector.class, serviceType("apple")));
        assertEquals(selector.selectHttpService(), URI.create("fake://server-https"));
    }

    @Test
    public void testFavorHttpsOverHttpSelector()
    {
        Injector injector = Guice.createInjector(
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.<String, String>of())),
                new TestingNodeModule(),
                new InMemoryDiscoveryModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        httpServiceSelectorBinder(binder).bindSelector("apple");
                    }
                }
        );

        InMemoryDiscoveryClient discoveryClient = (InMemoryDiscoveryClient) injector.getInstance(DiscoveryClient.class);
        discoveryClient.announce(ImmutableSet.of(serviceAnnouncement("apple").addProperty("http", "fake://server-http").build()));
        discoveryClient.announce(ImmutableSet.of(serviceAnnouncement("apple").addProperty("https", "fake://server-https").build()));

        HttpServiceSelector selector = injector.getInstance(Key.get(HttpServiceSelector.class, serviceType("apple")));
        assertEquals(selector.selectHttpService(), URI.create("fake://server-https"));
    }

    @Test
    public void testNoHttpServices()
    {
        Injector injector = Guice.createInjector(
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.<String, String>of())),
                new TestingNodeModule(),
                new InMemoryDiscoveryModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        httpServiceSelectorBinder(binder).bindSelector("apple");
                    }
                }
        );

        InMemoryDiscoveryClient discoveryClient = (InMemoryDiscoveryClient) injector.getInstance(DiscoveryClient.class);
        discoveryClient.announce(ImmutableSet.of(serviceAnnouncement("apple").addProperty("foo", "fake://server-https").build()));

        HttpServiceSelector selector = injector.getInstance(Key.get(HttpServiceSelector.class, serviceType("apple")));
        try {
            selector.selectHttpService();
            fail("Expected IllegalStateException");
        }
        catch (IllegalStateException expected) {
        }
    }


    @Test
    public void testInvalidUris()
    {
        Injector injector = Guice.createInjector(
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.<String, String>of())),
                new TestingNodeModule(),
                new InMemoryDiscoveryModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        httpServiceSelectorBinder(binder).bindSelector("apple");
                    }
                }
        );

        InMemoryDiscoveryClient discoveryClient = (InMemoryDiscoveryClient) injector.getInstance(DiscoveryClient.class);
        discoveryClient.announce(ImmutableSet.of(serviceAnnouncement("apple").addProperty("http", ":::INVALID:::").build()));
        discoveryClient.announce(ImmutableSet.of(serviceAnnouncement("apple").addProperty("https", ":::INVALID:::").build()));

        HttpServiceSelector selector = injector.getInstance(Key.get(HttpServiceSelector.class, serviceType("apple")));
        try {
            selector.selectHttpService();
            fail("Expected IllegalStateException");
        }
        catch (IllegalStateException expected) {
        }
    }
}
