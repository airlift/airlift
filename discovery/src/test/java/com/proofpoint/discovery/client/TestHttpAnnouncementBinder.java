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
package com.proofpoint.discovery.client;

import com.google.common.collect.Iterables;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.proofpoint.discovery.client.announce.AnnouncementHttpServerInfo;
import com.proofpoint.discovery.client.announce.ServiceAnnouncement;
import com.proofpoint.discovery.client.announce.StaticAnnouncementHttpServerInfoImpl;
import com.proofpoint.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.reporting.ReportingModule;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.weakref.jmx.guice.MBeanModule;

import javax.management.MBeanServer;
import java.net.URI;
import java.util.Set;

import static com.proofpoint.discovery.client.announce.ServiceAnnouncement.serviceAnnouncement;
import static org.mockito.Mockito.mock;

public class TestHttpAnnouncementBinder
{
    @Test
    public void testHttpAnnouncement()
    {
        final StaticAnnouncementHttpServerInfoImpl httpServerInfo = new StaticAnnouncementHttpServerInfoImpl(
                URI.create("http://127.0.0.1:4444"),
                URI.create("http://example.com:4444"),
                null
        );

        Injector injector = createInjector(httpServerInfo);

        ServiceAnnouncement announcement = serviceAnnouncement("apple")
                .addProperty("http", httpServerInfo.getHttpUri().toASCIIString())
                .addProperty("http-external", httpServerInfo.getHttpExternalUri().toASCIIString())
                .build();

        Set<ServiceAnnouncement> announcements = injector.getInstance(Key.get(new TypeLiteral<Set<ServiceAnnouncement>>()
        {
        }));

        assertAnnouncement(announcements, announcement);
    }

    @Test
    public void testHttpsAnnouncement()
    {
        final StaticAnnouncementHttpServerInfoImpl httpServerInfo = new StaticAnnouncementHttpServerInfoImpl(
                null,
                null,
                URI.create("https://example.com:4444")
        );

        Injector injector = createInjector(httpServerInfo);

        ServiceAnnouncement announcement = serviceAnnouncement("apple")
                .addProperty("https", httpServerInfo.getHttpsUri().toASCIIString())
                .build();

        Set<ServiceAnnouncement> announcements = injector.getInstance(Key.get(new TypeLiteral<Set<ServiceAnnouncement>>()
        {
        }));

        assertAnnouncement(announcements, announcement);
    }

    @Test
    public void testHttpAnnouncementWithPool()
    {
        final StaticAnnouncementHttpServerInfoImpl httpServerInfo = new StaticAnnouncementHttpServerInfoImpl(
                URI.create("http://127.0.0.1:4444"),
                URI.create("http://example.com:4444"),
                URI.create("https://example.com:4444")
        );

        Injector injector = createInjector(httpServerInfo);

        ServiceAnnouncement announcement = serviceAnnouncement("apple")
                .addProperty("http", httpServerInfo.getHttpUri().toASCIIString())
                .addProperty("http-external", httpServerInfo.getHttpExternalUri().toASCIIString())
                .addProperty("https", httpServerInfo.getHttpsUri().toASCIIString())
                .build();

        Set<ServiceAnnouncement> announcements = injector.getInstance(Key.get(new TypeLiteral<Set<ServiceAnnouncement>>()
        {
        }));

        assertAnnouncement(announcements, announcement);
    }

    @Test
    public void testHttpAnnouncementWithCustomProperties()
    {
        final StaticAnnouncementHttpServerInfoImpl httpServerInfo = new StaticAnnouncementHttpServerInfoImpl(
                URI.create("http://127.0.0.1:4444"),
                URI.create("http://example.com:4444"),
                URI.create("https://example.com:4444")
        );

        Injector injector = Guice.createInjector(
                new TestingDiscoveryModule(),
                new MBeanModule(),
                new ReportingModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(AnnouncementHttpServerInfo.class).toInstance(httpServerInfo);
                        binder.bind(MBeanServer.class).toInstance(mock(MBeanServer.class));
                        DiscoveryBinder.discoveryBinder(binder).bindHttpAnnouncement("apple").addProperty("a", "apple");
                    }
                }
        );

        ServiceAnnouncement announcement = serviceAnnouncement("apple")
                .addProperty("a", "apple")
                .addProperty("http", httpServerInfo.getHttpUri().toASCIIString())
                .addProperty("http-external", httpServerInfo.getHttpExternalUri().toASCIIString())
                .addProperty("https", httpServerInfo.getHttpsUri().toASCIIString())
                .build();

        Set<ServiceAnnouncement> announcements = injector.getInstance(Key.get(new TypeLiteral<Set<ServiceAnnouncement>>()
        {
        }));

        assertAnnouncement(announcements, announcement);
    }

    private Injector createInjector(final StaticAnnouncementHttpServerInfoImpl httpServerInfo)
    {
        return Guice.createInjector(
                new TestingDiscoveryModule(),
                new MBeanModule(),
                new ReportingModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(AnnouncementHttpServerInfo.class).toInstance(httpServerInfo);
                        binder.bind(MBeanServer.class).toInstance(mock(MBeanServer.class));
                        DiscoveryBinder.discoveryBinder(binder).bindHttpAnnouncement("apple");
                    }
                }
        );
    }

    private void assertAnnouncement(Set<ServiceAnnouncement> actualAnnouncements, ServiceAnnouncement expected)
    {
        Assert.assertNotNull(actualAnnouncements);
        Assert.assertEquals(actualAnnouncements.size(), 1);
        ServiceAnnouncement announcement = Iterables.getOnlyElement(actualAnnouncements);
        Assert.assertEquals(announcement.getType(), expected.getType());
        Assert.assertEquals(announcement.getProperties(), expected.getProperties());
    }
}
