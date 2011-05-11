package com.proofpoint.experimental.http.server;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.experimental.discovery.client.DiscoveryBinder;
import com.proofpoint.experimental.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.experimental.discovery.client.ServiceAnnouncement;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.node.testing.TestingNodeModule;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Set;

import static com.proofpoint.experimental.discovery.client.ServiceAnnouncement.serviceAnnouncement;

public class TestHttpAnnouncementBinder
{
    @Test
    public void testHttpAnnouncement()
    {
        Injector injector = Guice.createInjector(
                new TestModule(new HttpServerConfig().setHttpPort(4444)),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        DiscoveryBinder.discoveryBinder(binder).bindHttpAnnouncement("apple");
                    }
                }
        );

        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);
        ServiceAnnouncement announcement = serviceAnnouncement("apple")
                .addProperty("http", "http://" + nodeInfo.getPublicIp().getHostAddress() + ":4444")
                .build();

        Set<ServiceAnnouncement> announcements = injector.getInstance(Key.get(new TypeLiteral<Set<ServiceAnnouncement>>() { }));

        assertAnnouncement(announcements, announcement);
    }

    @Test
    public void testHttpsAnnouncement()
    {
        Injector injector = Guice.createInjector(
                new TestModule(new HttpServerConfig().setHttpEnabled(false).setHttpsEnabled(true).setHttpsPort(5555)),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        DiscoveryBinder.discoveryBinder(binder).bindHttpAnnouncement("apple");
                    }
                }
        );

        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);
        ServiceAnnouncement announcement = serviceAnnouncement("apple")
                .addProperty("https", "https://" + nodeInfo.getPublicIp().getHostAddress() + ":5555")
                .build();

        Set<ServiceAnnouncement> announcements = injector.getInstance(Key.get(new TypeLiteral<Set<ServiceAnnouncement>>() { }));

        assertAnnouncement(announcements, announcement);
    }

    @Test
    public void testHttpAnnouncementWithPool()
    {
        Injector injector = Guice.createInjector(
                new TestModule(new HttpServerConfig().setHttpPort(4444).setHttpsEnabled(true).setHttpsPort(5555)),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        DiscoveryBinder.discoveryBinder(binder).bindHttpAnnouncement("apple").setPool("apple-pool");
                    }
                }
        );

        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);
        ServiceAnnouncement announcement = serviceAnnouncement("apple")
                .setPool("apple-pool")
                .addProperty("http", "http://" + nodeInfo.getPublicIp().getHostAddress() + ":4444")
                .addProperty("https", "https://" + nodeInfo.getPublicIp().getHostAddress() + ":5555")
                .build();

        Set<ServiceAnnouncement> announcements = injector.getInstance(Key.get(new TypeLiteral<Set<ServiceAnnouncement>>() { }));

        assertAnnouncement(announcements, announcement);
    }

    @Test
    public void testHttpAnnouncementWithCustomProperties()
    {
        Injector injector = Guice.createInjector(
                new TestModule(new HttpServerConfig().setHttpPort(4444).setHttpsEnabled(true).setHttpsPort(5555)),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        DiscoveryBinder.discoveryBinder(binder).bindHttpAnnouncement("apple").addProperty("a", "apple");
                    }
                }
        );

        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);
        ServiceAnnouncement announcement = serviceAnnouncement("apple")
                .addProperty("a", "apple")
                .addProperty("http", "http://" + nodeInfo.getPublicIp().getHostAddress() + ":4444")
                .addProperty("https", "https://" + nodeInfo.getPublicIp().getHostAddress() + ":5555")
                .build();

        Set<ServiceAnnouncement> announcements = injector.getInstance(Key.get(new TypeLiteral<Set<ServiceAnnouncement>>() { }));

        assertAnnouncement(announcements, announcement);
    }

    private void assertAnnouncement(Set<ServiceAnnouncement> actualAnnouncements, ServiceAnnouncement expected)
    {
        Assert.assertNotNull(actualAnnouncements);
        Assert.assertEquals(actualAnnouncements.size(), 1);
        ServiceAnnouncement announcement = Iterables.getOnlyElement(actualAnnouncements);
        Assert.assertEquals(announcement.getType(), expected.getType());
        Assert.assertEquals(announcement.getPool(), expected.getPool());
        Assert.assertEquals(announcement.getProperties(), expected.getProperties());
    }

    private static class TestModule implements Module
    {
        private final HttpServerConfig httpServerConfig;

        public TestModule(HttpServerConfig httpServerConfig)
        {
            this.httpServerConfig = httpServerConfig;
        }

        @Override
        public void configure(Binder binder)
        {
            binder.install(new ConfigurationModule(new ConfigurationFactory(ImmutableMap.<String, String>of())));
            binder.install(new TestingNodeModule());
            binder.install(new TestingDiscoveryModule());
            binder.bind(HttpServerConfig.class).toInstance(httpServerConfig);
            binder.bind(HttpServerInfo.class).in(Scopes.SINGLETON);
        }
    }
}
