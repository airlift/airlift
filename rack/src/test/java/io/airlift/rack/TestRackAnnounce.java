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
package io.airlift.rack;

import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import com.google.inject.Injector;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.discovery.client.AnnouncementHttpServerInfo;
import io.airlift.discovery.client.Announcer;
import io.airlift.discovery.client.DiscoveryLookupClient;
import io.airlift.discovery.client.ServiceAnnouncement;
import io.airlift.discovery.client.ServiceDescriptor;
import io.airlift.discovery.client.testing.InMemoryDiscoveryClient;
import io.airlift.discovery.client.testing.TestingDiscoveryModule;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.node.testing.TestingNodeModule;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.List;

import static io.airlift.discovery.client.ServiceAnnouncement.serviceAnnouncement;
import static org.testng.Assert.assertEquals;

public class TestRackAnnounce
{
    private LifeCycleManager lifeCycleManager;

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        if (lifeCycleManager != null) {
            lifeCycleManager.stop();
        }
    }

    @Test
    public void testAnnouncement()
            throws Exception
    {
        Bootstrap app = new Bootstrap(
                new TestingHttpServerModule(),
                new RackModule(),
                new TestingNodeModule(),
                new TestingDiscoveryModule());

        Injector injector = app
                .strictConfig()
                .doNotInitializeLogging()
                .setRequiredConfigurationProperty("rackserver.rack-config-path", Resources.getResource("test/raw/config.ru").getFile())
                .setRequiredConfigurationProperty("rackserver.announcement", "racktest")
                .initialize();

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);

        injector.getInstance(Announcer.class).start();

        AnnouncementHttpServerInfo httpServerInfo = injector.getInstance(AnnouncementHttpServerInfo.class);
        ServiceAnnouncement announcement = serviceAnnouncement("racktest")
                .addProperty("http", httpServerInfo.getHttpUri().toASCIIString())
                .addProperty("http-external", httpServerInfo.getHttpExternalUri().toASCIIString())
                .build();

        DiscoveryLookupClient lookupClient = injector.getInstance(InMemoryDiscoveryClient.class);
        List<ServiceDescriptor> descriptors = lookupClient.getServices("racktest").get().getServiceDescriptors();

        assertEquals(descriptors.size(), 1);
        ServiceDescriptor descriptor = Iterables.getOnlyElement(descriptors);

        assertEquals(descriptor.getProperties(), announcement.getProperties());
    }
}
