package com.proofpoint.rack;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.discovery.client.AnnouncementHttpServerInfo;
import com.proofpoint.discovery.client.Announcer;
import com.proofpoint.discovery.client.DiscoveryLookupClient;
import com.proofpoint.discovery.client.ServiceAnnouncement;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.discovery.client.testing.InMemoryDiscoveryClient;
import com.proofpoint.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.node.testing.TestingNodeModule;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.List;

import static com.proofpoint.discovery.client.ServiceAnnouncement.serviceAnnouncement;
import static org.testng.Assert.assertEquals;

public class TestRackAnnounce
{
    private TestingHttpServer server;

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testAnnouncement()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new TestingHttpServerModule(),
                new RackModule(),
                new TestingNodeModule(),
                new TestingDiscoveryModule(),
                new ConfigurationModule(new ConfigurationFactory(
                        ImmutableMap.<String, String>builder()
                                .put("rackserver.rack-config-path", Resources.getResource("test/raw/config.ru").getFile())
                                .put("rackserver.announcement", "racktest")
                                .build()
                )));

        server = injector.getInstance(TestingHttpServer.class);
        server.start();

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
