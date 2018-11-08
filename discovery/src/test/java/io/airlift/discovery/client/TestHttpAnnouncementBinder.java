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

import com.google.common.collect.Iterables;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import io.airlift.discovery.client.testing.TestingDiscoveryModule;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Set;

import static io.airlift.discovery.client.DiscoveryBinder.discoveryBinder;
import static io.airlift.discovery.client.ServiceAnnouncement.serviceAnnouncement;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestHttpAnnouncementBinder
{
    @Test
    public void testHttpAnnouncement()
    {
        final StaticAnnouncementHttpServerInfoImpl httpServerInfo = new StaticAnnouncementHttpServerInfoImpl(
                URI.create("http://127.0.0.1:4444"),
                URI.create("http://example.com:4444"),
                null,
                null);

        Injector injector = Guice.createInjector(
                new TestingDiscoveryModule(),
                binder -> {
                    binder.bind(AnnouncementHttpServerInfo.class).toInstance(httpServerInfo);
                    discoveryBinder(binder).bindHttpAnnouncement("apple");
                });

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
                URI.create("https://127.0.0.1:4444"),
                URI.create("https://example.com:4444"));

        Injector injector = Guice.createInjector(
                new TestingDiscoveryModule(),
                binder -> {
                    binder.bind(AnnouncementHttpServerInfo.class).toInstance(httpServerInfo);
                    discoveryBinder(binder).bindHttpAnnouncement("apple");
                });

        ServiceAnnouncement announcement = serviceAnnouncement("apple")
                .addProperty("https", httpServerInfo.getHttpsUri().toASCIIString())
                .addProperty("https-external", httpServerInfo.getHttpsExternalUri().toASCIIString())
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
                URI.create("https://127.0.0.1:4444"),
                URI.create("https://example.com:4444"));

        Injector injector = Guice.createInjector(
                new TestingDiscoveryModule(),
                binder -> {
                    binder.bind(AnnouncementHttpServerInfo.class).toInstance(httpServerInfo);
                    discoveryBinder(binder).bindHttpAnnouncement("apple");
                });

        ServiceAnnouncement announcement = serviceAnnouncement("apple")
                .addProperty("http", httpServerInfo.getHttpUri().toASCIIString())
                .addProperty("http-external", httpServerInfo.getHttpExternalUri().toASCIIString())
                .addProperty("https", httpServerInfo.getHttpsUri().toASCIIString())
                .addProperty("https-external", httpServerInfo.getHttpsExternalUri().toASCIIString())
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
                URI.create("https://127.0.0.1:4444"),
                URI.create("https://example.com:4444"));

        Injector injector = Guice.createInjector(
                new TestingDiscoveryModule(),
                binder -> {
                    binder.bind(AnnouncementHttpServerInfo.class).toInstance(httpServerInfo);
                    discoveryBinder(binder).bindHttpAnnouncement("apple").addProperty("a", "apple");
                });

        ServiceAnnouncement announcement = serviceAnnouncement("apple")
                .addProperty("a", "apple")
                .addProperty("http", httpServerInfo.getHttpUri().toASCIIString())
                .addProperty("http-external", httpServerInfo.getHttpExternalUri().toASCIIString())
                .addProperty("https", httpServerInfo.getHttpsUri().toASCIIString())
                .addProperty("https-external", httpServerInfo.getHttpsExternalUri().toASCIIString())
                .build();

        Set<ServiceAnnouncement> announcements = injector.getInstance(Key.get(new TypeLiteral<Set<ServiceAnnouncement>>()
        {
        }));

        assertAnnouncement(announcements, announcement);
    }

    private void assertAnnouncement(Set<ServiceAnnouncement> actualAnnouncements, ServiceAnnouncement expected)
    {
        assertNotNull(actualAnnouncements);
        assertEquals(actualAnnouncements.size(), 1);
        ServiceAnnouncement announcement = Iterables.getOnlyElement(actualAnnouncements);
        assertEquals(announcement.getType(), expected.getType());
        assertEquals(announcement.getProperties(), expected.getProperties());
    }
}
