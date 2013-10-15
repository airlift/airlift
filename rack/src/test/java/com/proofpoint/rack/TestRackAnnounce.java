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
package com.proofpoint.rack;

import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import com.google.inject.Injector;
import com.proofpoint.bootstrap.Bootstrap;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.discovery.client.DiscoveryLookupClient;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.discovery.client.announce.AnnouncementHttpServerInfo;
import com.proofpoint.discovery.client.announce.Announcer;
import com.proofpoint.discovery.client.announce.ServiceAnnouncement;
import com.proofpoint.discovery.client.testing.InMemoryDiscoveryClient;
import com.proofpoint.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.jmx.testing.TestingJmxModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.guice.MBeanModule;

import java.util.List;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapApplication;
import static com.proofpoint.discovery.client.announce.ServiceAnnouncement.serviceAnnouncement;
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
        Bootstrap app = bootstrapApplication("test-application")
                .withModules(
                        new TestingHttpServerModule(),
                        new RackModule(),
                        new TestingNodeModule(),
                        new TestingDiscoveryModule(),
                        new ReportingModule(),
                        new MBeanModule(),
                        new TestingJmxModule()
                );

        Injector injector = app
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
