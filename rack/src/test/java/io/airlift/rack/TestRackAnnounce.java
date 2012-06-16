package io.airlift.rack;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.configuration.ConfigurationModule;
import io.airlift.discovery.client.AnnouncementHttpServerInfo;
import io.airlift.discovery.client.Announcer;
import io.airlift.discovery.client.DiscoveryLookupClient;
import io.airlift.discovery.client.ServiceAnnouncement;
import io.airlift.discovery.client.ServiceDescriptor;
import io.airlift.discovery.client.testing.InMemoryDiscoveryClient;
import io.airlift.discovery.client.testing.TestingDiscoveryModule;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.node.testing.TestingNodeModule;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.List;

import static io.airlift.discovery.client.ServiceAnnouncement.serviceAnnouncement;
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
