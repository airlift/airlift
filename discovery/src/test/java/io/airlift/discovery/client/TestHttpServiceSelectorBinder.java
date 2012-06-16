package com.proofpoint.discovery.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.discovery.client.testing.InMemoryDiscoveryClient;
import com.proofpoint.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.node.testing.TestingNodeModule;
import org.testng.annotations.Test;

import java.net.URI;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.proofpoint.discovery.client.DiscoveryBinder.discoveryBinder;
import static com.proofpoint.discovery.client.ServiceAnnouncement.serviceAnnouncement;
import static com.proofpoint.discovery.client.ServiceTypes.serviceType;
import static org.testng.Assert.assertEquals;

public class TestHttpServiceSelectorBinder
{
    @Test
    public void testHttpSelectorString()
    {
        Injector injector = Guice.createInjector(
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.<String, String>of())),
                new TestingNodeModule(),
                new TestingDiscoveryModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindHttpSelector("apple");
                    }
                }
        );

        InMemoryDiscoveryClient discoveryClient = injector.getInstance(InMemoryDiscoveryClient.class);
        discoveryClient.announce(ImmutableSet.of(serviceAnnouncement("apple").addProperty("http", "fake://server-http").build()));

        HttpServiceSelector selector = injector.getInstance(Key.get(HttpServiceSelector.class, serviceType("apple")));
        assertEquals(getOnlyElement(selector.selectHttpService()), URI.create("fake://server-http"));
    }

    @Test
    public void testHttpSelectorAnnotation()
    {
        Injector injector = Guice.createInjector(
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.<String, String>of())),
                new TestingNodeModule(),
                new TestingDiscoveryModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindHttpSelector(serviceType("apple"));
                    }
                }
        );

        InMemoryDiscoveryClient discoveryClient = injector.getInstance(InMemoryDiscoveryClient.class);
        discoveryClient.announce(ImmutableSet.of(serviceAnnouncement("apple").addProperty("http", "fake://server-http").build()));

        HttpServiceSelector selector = injector.getInstance(Key.get(HttpServiceSelector.class, serviceType("apple")));
        assertEquals(getOnlyElement(selector.selectHttpService()), URI.create("fake://server-http"));
    }

    @Test
    public void testHttpsSelector()
    {
        Injector injector = Guice.createInjector(
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.<String, String>of())),
                new TestingNodeModule(),
                new TestingDiscoveryModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindHttpSelector("apple");
                    }
                }
        );

        InMemoryDiscoveryClient discoveryClient = injector.getInstance(InMemoryDiscoveryClient.class);
        discoveryClient.announce(ImmutableSet.of(serviceAnnouncement("apple").addProperty("https", "fake://server-https").build()));

        HttpServiceSelector selector = injector.getInstance(Key.get(HttpServiceSelector.class, serviceType("apple")));
        assertEquals(getOnlyElement(selector.selectHttpService()), URI.create("fake://server-https"));
    }

    @Test
    public void testFavorHttpsOverHttpSelector()
    {
        Injector injector = Guice.createInjector(
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.<String, String>of())),
                new TestingNodeModule(),
                new TestingDiscoveryModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindHttpSelector("apple");
                    }
                }
        );

        InMemoryDiscoveryClient discoveryClient = injector.getInstance(InMemoryDiscoveryClient.class);
        discoveryClient.announce(ImmutableSet.of(serviceAnnouncement("apple").addProperty("http", "fake://server-http").build(),
                serviceAnnouncement("apple").addProperty("https", "fake://server-https").build()));

        HttpServiceSelector selector = injector.getInstance(Key.get(HttpServiceSelector.class, serviceType("apple")));
        assertEquals(selector.selectHttpService(), ImmutableList.of(URI.create("fake://server-https"), URI.create("fake://server-http")));
    }

    @Test
    public void testNoHttpServices()
    {
        Injector injector = Guice.createInjector(
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.<String, String>of())),
                new TestingNodeModule(),
                new TestingDiscoveryModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindHttpSelector("apple");
                    }
                }
        );

        InMemoryDiscoveryClient discoveryClient = injector.getInstance(InMemoryDiscoveryClient.class);
        discoveryClient.announce(ImmutableSet.of(serviceAnnouncement("apple").addProperty("foo", "fake://server-https").build()));

        HttpServiceSelector selector = injector.getInstance(Key.get(HttpServiceSelector.class, serviceType("apple")));
        assertEquals(selector.selectHttpService(), ImmutableList.of());
    }


    @Test
    public void testInvalidUris()
    {
        Injector injector = Guice.createInjector(
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.<String, String>of())),
                new TestingNodeModule(),
                new TestingDiscoveryModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindHttpSelector("apple");
                    }
                }
        );

        InMemoryDiscoveryClient discoveryClient = injector.getInstance(InMemoryDiscoveryClient.class);
        discoveryClient.announce(ImmutableSet.of(serviceAnnouncement("apple").addProperty("http", ":::INVALID:::").build()));
        discoveryClient.announce(ImmutableSet.of(serviceAnnouncement("apple").addProperty("https", ":::INVALID:::").build()));

        HttpServiceSelector selector = injector.getInstance(Key.get(HttpServiceSelector.class, serviceType("apple")));
        assertEquals(selector.selectHttpService(), ImmutableList.of());
    }
}
