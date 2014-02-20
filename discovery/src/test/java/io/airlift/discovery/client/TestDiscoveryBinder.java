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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.configuration.ConfigurationModule;
import io.airlift.discovery.client.testing.TestingDiscoveryModule;
import io.airlift.node.testing.TestingNodeModule;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.airlift.discovery.client.DiscoveryBinder.discoveryBinder;
import static io.airlift.discovery.client.ServiceTypes.serviceType;

public class TestDiscoveryBinder
{
    private static final ServiceDescriptor SERVICE1 = new ServiceDescriptor(UUID.randomUUID(), "nodeId1", "type1", "pool1", "localhost", ServiceState.RUNNING, ImmutableMap.<String, String>of());
    private static final ServiceDescriptor SERVICE2 = new ServiceDescriptor(UUID.randomUUID(), "nodeId2", "type2", "pool1", "localhost", ServiceState.RUNNING, ImmutableMap.<String, String>of());
    private static final ServiceDescriptor SERVICE3 = new ServiceDescriptor(UUID.randomUUID(), "nodeId3", "type2", "pool2", "localhost", ServiceState.RUNNING, ImmutableMap.<String, String>of());

    @Test
    public void testBindSelector()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new TestModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindServiceAnnouncement(new ServiceAnnouncementProvider().get());
                    }
                }
        );

        Set<ServiceAnnouncement> announcements = injector.getInstance(Key.get(new TypeLiteral<Set<ServiceAnnouncement>>() { }));
        Assert.assertEquals(announcements, ImmutableSet.of(ANNOUNCEMENT));
    }

    @Test
    public void testBindSelectorProviderClass()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new TestModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindServiceAnnouncement(ServiceAnnouncementProvider.class);
                    }
                }
        );

        Set<ServiceAnnouncement> announcements = injector.getInstance(Key.get(new TypeLiteral<Set<ServiceAnnouncement>>() { }));
        Assert.assertEquals(announcements, ImmutableSet.of(ANNOUNCEMENT));
    }

    @Test
    public void testBindSelectorProviderInstance()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new TestModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindServiceAnnouncement(new ServiceAnnouncementProvider());
                    }
                }
        );

        Set<ServiceAnnouncement> announcements = injector.getInstance(Key.get(new TypeLiteral<Set<ServiceAnnouncement>>() { }));
        Assert.assertEquals(announcements, ImmutableSet.of(ANNOUNCEMENT));
    }

    @Test
    public void testBindSelectorString()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new TestModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindSelector("apple");
                    }
                }
        );

        assertCanCreateServiceSelector(injector, "apple", ServiceSelectorConfig.DEFAULT_POOL);
    }

    @Test
    public void testBindSelectorAnnotation()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new TestModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindSelector(serviceType("apple"));
                    }
                }
        );

        assertCanCreateServiceSelector(injector, "apple", ServiceSelectorConfig.DEFAULT_POOL);
    }

    @Test
    public void testBindSelectorStringWithPool()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new TestModule(ImmutableMap.of("discovery.apple.pool", "apple-pool")),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindSelector("apple");
                    }
                }
        );

        assertCanCreateServiceSelector(injector, "apple", "apple-pool");
    }

    @Test
    public void testBindSelectorAnnotationWithPool()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new TestModule(ImmutableMap.of("discovery.apple.pool", "apple-pool")),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindSelector(serviceType("apple"));
                    }
                }
        );

        assertCanCreateServiceSelector(injector, "apple", "apple-pool");
    }

    @Test
    public void testServiceInventoryManagerMerge()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new TestModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        TestingServiceInventory testingServiceInventory1 = new TestingServiceInventory();
                        testingServiceInventory1.addServiceDescriptor(SERVICE1)
                                .addServiceDescriptor(SERVICE2);
                        Multibinder.newSetBinder(binder, ServiceInventory.class).addBinding().toInstance(testingServiceInventory1);
                    }
                },
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        TestingServiceInventory testingServiceInventory2 = new TestingServiceInventory();
                        testingServiceInventory2.addServiceDescriptor(SERVICE3);
                        Multibinder.newSetBinder(binder, ServiceInventory.class).addBinding().toInstance(testingServiceInventory2);
                    }
                }
        );

        ServiceInventory serviceInventory = injector.getInstance(ServiceInventory.class);
        Assert.assertNotNull(serviceInventory);

        Iterable<ServiceDescriptor> descriptors = serviceInventory.getServiceDescriptors();
        Assert.assertEquals(Iterables.size(descriptors), 3);
        Set<ServiceDescriptor> descriptorSet = ImmutableSet.copyOf(descriptors);
        Assert.assertEquals(descriptorSet, ImmutableSet.of(SERVICE1, SERVICE2, SERVICE3));

        descriptors = serviceInventory.getServiceDescriptors("type1");
        Assert.assertEquals(Iterables.size(descriptors), 1);
        Assert.assertEquals(Iterables.getOnlyElement(descriptors), SERVICE1);
        descriptors = serviceInventory.getServiceDescriptors("type2");
        Assert.assertEquals(Iterables.size(descriptors), 2);
        descriptorSet = ImmutableSet.copyOf(descriptors);
        Assert.assertEquals(descriptorSet, ImmutableSet.of(SERVICE2, SERVICE3));
        Assert.assertTrue(Iterables.isEmpty(serviceInventory.getServiceDescriptors("type3")));

        descriptors = serviceInventory.getServiceDescriptors("type1", "pool1");
        Assert.assertEquals(Iterables.size(descriptors), 1);
        Assert.assertEquals(Iterables.getOnlyElement(descriptors), SERVICE1);
        Assert.assertTrue(Iterables.isEmpty(serviceInventory.getServiceDescriptors("type1", "pool2")));
    }

    private void assertCanCreateServiceSelector(Injector injector, String expectedType, String expectedPool)
    {
        ServiceSelector actualServiceSelector = injector.getInstance(Key.get(ServiceSelector.class, serviceType(expectedType)));
        Assert.assertNotNull(actualServiceSelector);
        Assert.assertEquals(actualServiceSelector.getType(), expectedType);
        Assert.assertEquals(actualServiceSelector.getPool(), expectedPool);
    }

    private static class TestModule implements Module
    {
        private Map<String,String> configProperties;

        private TestModule()
        {
            configProperties = ImmutableMap.of();
        }

        private TestModule(Map<String, String> configProperties)
        {
            this.configProperties = ImmutableMap.copyOf(configProperties);
        }

        @Override
        public void configure(Binder binder)
        {
            binder.install(new ConfigurationModule(new ConfigurationFactory(configProperties)));
            binder.install(new TestingNodeModule());
            binder.install(new TestingDiscoveryModule());
        }
    }

    private static final ServiceAnnouncement ANNOUNCEMENT = ServiceAnnouncement.serviceAnnouncement("apple").addProperty("a", "apple").build();

    private static class ServiceAnnouncementProvider implements Provider<ServiceAnnouncement>
    {
        @Override
        public ServiceAnnouncement get()
        {
            return ANNOUNCEMENT;
        }
    }
}
