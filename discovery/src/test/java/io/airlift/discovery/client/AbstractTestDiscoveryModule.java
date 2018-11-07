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
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.configuration.ConfigurationModule;
import io.airlift.json.JsonModule;
import io.airlift.node.testing.TestingNodeModule;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Map;

import static com.google.common.collect.Iterables.getOnlyElement;
import static io.airlift.discovery.client.DiscoveryBinder.discoveryBinder;
import static io.airlift.discovery.client.ServiceTypes.serviceType;
import static java.util.Objects.requireNonNull;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public abstract class AbstractTestDiscoveryModule
{
    private final Module discoveryModule;

    protected AbstractTestDiscoveryModule(Module discoveryModule)
    {
        this.discoveryModule = requireNonNull(discoveryModule, "discoveryModule is null");
    }

    @Test
    public void testBinding()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.of("discovery.uri", "fake://server"))),
                new JsonModule(),
                new TestingNodeModule(),
                discoveryModule);

        // should produce a discovery announcement client and a lookup client
        assertNotNull(injector.getInstance(DiscoveryAnnouncementClient.class));
        assertNotNull(injector.getInstance(DiscoveryLookupClient.class));
        // should produce an Announcer
        assertNotNull(injector.getInstance(Announcer.class));
        // should produce a ServiceSelectorManager
        assertNotNull(injector.getInstance(ServiceSelectorManager.class));
    }

    @Test
    public void testMerging()
            throws Exception
    {
        final StaticAnnouncementHttpServerInfoImpl httpServerInfo = new StaticAnnouncementHttpServerInfoImpl(
                URI.create("http://127.0.0.1:4444"),
                URI.create("http://example.com:4444"),
                null,
                null);

        Map<String, String> config = ImmutableMap.<String, String>builder()
                .put("discovery.uri", "fake://server")
                .put("discovery.carrot.pool", "test")
                .build();

        Injector injector = Guice.createInjector(
                new ConfigurationModule(new ConfigurationFactory(config)),
                new JsonModule(),
                new TestingNodeModule(),
                new DiscoveryModule(),
                binder -> {
                    binder.bind(AnnouncementHttpServerInfo.class).toInstance(httpServerInfo);
                    discoveryBinder(binder).bindHttpAnnouncement("apple");
                    discoveryBinder(binder).bindHttpAnnouncement("banana");
                    discoveryBinder(binder).bindHttpAnnouncement("carrot");
                    discoveryBinder(binder).bindHttpSelector("apple");
                    discoveryBinder(binder).bindHttpSelector("banana");
                    discoveryBinder(binder).bindHttpSelector("carrot");
                    discoveryBinder(binder).bindHttpSelector("grape");
                });

        HttpServiceSelector selector = injector.getInstance(Key.get(HttpServiceSelector.class, serviceType("apple")));
        assertEquals(getOnlyElement(selector.selectHttpService()), URI.create("http://127.0.0.1:4444"));

        selector = injector.getInstance(Key.get(HttpServiceSelector.class, serviceType("banana")));
        assertEquals(getOnlyElement(selector.selectHttpService()), URI.create("http://127.0.0.1:4444"));

        selector = injector.getInstance(Key.get(HttpServiceSelector.class, serviceType("carrot")));
        assertTrue(selector.selectHttpService().isEmpty());

        selector = injector.getInstance(Key.get(HttpServiceSelector.class, serviceType("grape")));
        assertTrue(selector.selectHttpService().isEmpty());
    }
}
