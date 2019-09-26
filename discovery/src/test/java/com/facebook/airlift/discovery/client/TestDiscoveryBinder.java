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
package com.facebook.airlift.discovery.client;

import com.facebook.airlift.configuration.ConfigurationFactory;
import com.facebook.airlift.configuration.ConfigurationModule;
import com.facebook.airlift.discovery.client.testing.TestingDiscoveryModule;
import com.facebook.airlift.node.testing.TestingNodeModule;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import org.testng.annotations.Test;

import javax.inject.Provider;

import java.util.Map;
import java.util.Set;

import static com.facebook.airlift.discovery.client.DiscoveryBinder.discoveryBinder;
import static com.facebook.airlift.discovery.client.ServiceTypes.serviceType;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestDiscoveryBinder
{
    @Test
    public void testBindSelector()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new TestModule(),
                binder -> discoveryBinder(binder).bindServiceAnnouncement(new ServiceAnnouncementProvider().get()));

        Set<ServiceAnnouncement> announcements = injector.getInstance(Key.get(new TypeLiteral<Set<ServiceAnnouncement>>() {}));
        assertEquals(announcements, ImmutableSet.of(ANNOUNCEMENT));
    }

    @Test
    public void testBindSelectorProviderClass()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new TestModule(),
                binder -> discoveryBinder(binder).bindServiceAnnouncement(ServiceAnnouncementProvider.class));

        Set<ServiceAnnouncement> announcements = injector.getInstance(Key.get(new TypeLiteral<Set<ServiceAnnouncement>>() {}));
        assertEquals(announcements, ImmutableSet.of(ANNOUNCEMENT));
    }

    @Test
    public void testBindSelectorProviderInstance()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new TestModule(),
                binder -> discoveryBinder(binder).bindServiceAnnouncement(new ServiceAnnouncementProvider()));

        Set<ServiceAnnouncement> announcements = injector.getInstance(Key.get(new TypeLiteral<Set<ServiceAnnouncement>>() {}));
        assertEquals(announcements, ImmutableSet.of(ANNOUNCEMENT));
    }

    @Test
    public void testBindSelectorString()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new TestModule(),
                binder -> discoveryBinder(binder).bindSelector("apple"));

        assertCanCreateServiceSelector(injector, "apple", ServiceSelectorConfig.DEFAULT_POOL);
    }

    @Test
    public void testBindSelectorAnnotation()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new TestModule(),
                binder -> discoveryBinder(binder).bindSelector(serviceType("apple")));

        assertCanCreateServiceSelector(injector, "apple", ServiceSelectorConfig.DEFAULT_POOL);
    }

    @Test
    public void testBindSelectorStringWithPool()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new TestModule(ImmutableMap.of("discovery.apple.pool", "apple-pool")),
                binder -> discoveryBinder(binder).bindSelector("apple"));

        assertCanCreateServiceSelector(injector, "apple", "apple-pool");
    }

    @Test
    public void testBindSelectorAnnotationWithPool()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new TestModule(ImmutableMap.of("discovery.apple.pool", "apple-pool")),
                binder -> discoveryBinder(binder).bindSelector(serviceType("apple")));

        assertCanCreateServiceSelector(injector, "apple", "apple-pool");
    }

    private void assertCanCreateServiceSelector(Injector injector, String expectedType, String expectedPool)
    {
        ServiceSelector actualServiceSelector = injector.getInstance(Key.get(ServiceSelector.class, serviceType(expectedType)));
        assertNotNull(actualServiceSelector);
        assertEquals(actualServiceSelector.getType(), expectedType);
        assertEquals(actualServiceSelector.getPool(), expectedPool);
    }

    private static class TestModule
            implements Module
    {
        private Map<String, String> configProperties;

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

    private static class ServiceAnnouncementProvider
            implements Provider<ServiceAnnouncement>
    {
        @Override
        public ServiceAnnouncement get()
        {
            return ANNOUNCEMENT;
        }
    }
}
