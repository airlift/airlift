/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.discovery.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.configuration.ConfigurationModule;
import io.airlift.discovery.client.testing.InMemoryDiscoveryClient;
import io.airlift.discovery.client.testing.TestingDiscoveryModule;
import io.airlift.node.testing.TestingNodeModule;
import org.testng.annotations.Test;

import java.net.URI;

import static com.google.common.collect.Iterables.getOnlyElement;
import static io.airlift.discovery.client.DiscoveryBinder.discoveryBinder;
import static io.airlift.discovery.client.ServiceAnnouncement.serviceAnnouncement;
import static io.airlift.discovery.client.ServiceTypes.serviceType;
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
                binder -> discoveryBinder(binder).bindHttpSelector("apple"));

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
                binder -> discoveryBinder(binder).bindHttpSelector(serviceType("apple")));

        InMemoryDiscoveryClient discoveryClient = injector.getInstance(InMemoryDiscoveryClient.class);
        discoveryClient.announce(ImmutableSet.of(serviceAnnouncement("apple").addProperty("http", "fake://server-http").build()));

        HttpServiceSelector selector = injector.getInstance(Key.get(HttpServiceSelector.class, serviceType("apple")));
        assertEquals(getOnlyElement(selector.selectHttpService()), URI.create("fake://server-http"));

        ServiceSelectorManager manager = injector.getInstance(ServiceSelectorManager.class);
        assertEquals(manager.getServiceSelectors().size(), 1);
        manager.attemptRefresh();
        manager.forceRefresh();
    }

    @Test
    public void testHttpsSelector()
    {
        Injector injector = Guice.createInjector(
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.<String, String>of())),
                new TestingNodeModule(),
                new TestingDiscoveryModule(),
                binder -> discoveryBinder(binder).bindHttpSelector("apple"));

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
                binder -> discoveryBinder(binder).bindHttpSelector("apple"));

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
                binder -> discoveryBinder(binder).bindHttpSelector("apple"));

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
                binder -> discoveryBinder(binder).bindHttpSelector("apple"));

        InMemoryDiscoveryClient discoveryClient = injector.getInstance(InMemoryDiscoveryClient.class);
        discoveryClient.announce(ImmutableSet.of(serviceAnnouncement("apple").addProperty("http", ":::INVALID:::").build()));
        discoveryClient.announce(ImmutableSet.of(serviceAnnouncement("apple").addProperty("https", ":::INVALID:::").build()));

        HttpServiceSelector selector = injector.getInstance(Key.get(HttpServiceSelector.class, serviceType("apple")));
        assertEquals(selector.selectHttpService(), ImmutableList.of());
    }
}
