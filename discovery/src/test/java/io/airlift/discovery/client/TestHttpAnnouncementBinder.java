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

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import io.airlift.discovery.client.testing.TestingDiscoveryModule;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static io.airlift.discovery.client.DiscoveryBinder.discoveryBinder;
import static io.airlift.discovery.client.ServiceAnnouncement.serviceAnnouncement;
import static org.assertj.core.api.Assertions.assertThat;

public class TestHttpAnnouncementBinder
{
    @Test
    public void testHttpAnnouncement()
    {
        StaticAnnouncementHttpServerInfoImpl httpServerInfo = new StaticAnnouncementHttpServerInfoImpl(
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

        Set<ServiceAnnouncement> announcements = injector.getInstance(Key.get(new TypeLiteral<Set<ServiceAnnouncement>>() {}));

        assertAnnouncement(announcements, announcement);
    }

    @Test
    public void testHttpsAnnouncement()
    {
        StaticAnnouncementHttpServerInfoImpl httpServerInfo = new StaticAnnouncementHttpServerInfoImpl(
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

        Set<ServiceAnnouncement> announcements = injector.getInstance(Key.get(new TypeLiteral<Set<ServiceAnnouncement>>() {}));

        assertAnnouncement(announcements, announcement);
    }

    @Test
    public void testHttpAnnouncementWithPool()
    {
        StaticAnnouncementHttpServerInfoImpl httpServerInfo = new StaticAnnouncementHttpServerInfoImpl(
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

        Set<ServiceAnnouncement> announcements = injector.getInstance(Key.get(new TypeLiteral<Set<ServiceAnnouncement>>() {}));

        assertAnnouncement(announcements, announcement);
    }

    @Test
    public void testHttpAnnouncementWithCustomProperties()
    {
        StaticAnnouncementHttpServerInfoImpl httpServerInfo = new StaticAnnouncementHttpServerInfoImpl(
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

        Set<ServiceAnnouncement> announcements = injector.getInstance(Key.get(new TypeLiteral<Set<ServiceAnnouncement>>() {}));

        assertAnnouncement(announcements, announcement);
    }

    @Test
    public void testHttpAnnouncementWithCustomProvidedProperties()
    {
        StaticAnnouncementHttpServerInfoImpl httpServerInfo = new StaticAnnouncementHttpServerInfoImpl(
                URI.create("http://127.0.0.1:4444"),
                URI.create("http://example.com:4444"),
                URI.create("https://127.0.0.1:4444"),
                URI.create("https://example.com:4444"));
        String randomValue = UUID.randomUUID().toString();

        Injector injector = Guice.createInjector(
                new TestingDiscoveryModule(),
                binder -> {
                    binder.bind(AnnouncementHttpServerInfo.class).toInstance(httpServerInfo);
                    discoveryBinder(binder).bindHttpAnnouncement("apple")
                            .addProperty("instance-property", "my-instance")
                            .bindPropertyProvider("provided-by-instance", () -> "provided-constant: " + randomValue)
                            .bindPropertyProvider("provided-by-injected", StringPropertyProvider.class);
                });

        Set<ServiceAnnouncement> announcements = injector.getInstance(new Key<>() {});
        assertAnnouncement(announcements, serviceAnnouncement("apple")
                .addProperty("instance-property", "my-instance")
                .addProperty("provided-by-instance", "provided-constant: " + randomValue)
                .addProperty("provided-by-injected", "concatenated: http://127.0.0.1:4444 https://127.0.0.1:4444")
                .addProperty("http", "http://127.0.0.1:4444")
                .addProperty("http-external", "http://example.com:4444")
                .addProperty("https", "https://127.0.0.1:4444")
                .addProperty("https-external", "https://example.com:4444")
                .build());
    }

    private void assertAnnouncement(Set<ServiceAnnouncement> actualAnnouncements, ServiceAnnouncement expected)
    {
        assertThat(actualAnnouncements).isNotNull();
        assertThat(actualAnnouncements.size()).isEqualTo(1);
        ServiceAnnouncement announcement = actualAnnouncements.stream().collect(onlyElement());
        assertThat(announcement.getType()).isEqualTo(expected.getType());
        assertThat(announcement.getProperties()).isEqualTo(expected.getProperties());
    }

    public static class StringPropertyProvider
            implements Provider<String>
    {
        private final AnnouncementHttpServerInfo httpServerInfo;

        @Inject
        public StringPropertyProvider(AnnouncementHttpServerInfo httpServerInfo)
        {
            this.httpServerInfo = httpServerInfo;
        }

        @Override
        public String get()
        {
            return "concatenated: %s %s".formatted(httpServerInfo.getHttpUri(), httpServerInfo.getHttpsUri());
        }
    }
}
