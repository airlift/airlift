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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.proofpoint.bootstrap.Bootstrap;
import com.proofpoint.discovery.client.DiscoveryBinder.BalancingHttpClientBindingBuilder;
import com.proofpoint.discovery.client.announce.ServiceAnnouncement;
import com.proofpoint.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.HttpRequestFilter;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.balancing.HttpServiceBalancer;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.weakref.jmx.testing.TestingMBeanModule;

import javax.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Set;

import static com.proofpoint.discovery.client.DiscoveryBinder.discoveryBinder;
import static com.proofpoint.discovery.client.ServiceTypes.serviceType;
import static com.proofpoint.http.client.Request.Builder.fromRequest;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;

public class TestDiscoveryBinder
{

    @Test
    public void testBindAnnouncements()
            throws Exception
    {
        Injector injector = createInjector(
                ImmutableMap.<String, String>of(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindServiceAnnouncement(new ServiceAnnouncementProvider().get());
                    }
                }
        );

        Set<ServiceAnnouncement> announcements = injector.getInstance(Key.get(new TypeLiteral<Set<ServiceAnnouncement>>()
        {
        }));
        Assert.assertEquals(announcements, ImmutableSet.of(ANNOUNCEMENT));
    }

    @Test
    public void testBindAnnouncementProviderClass()
            throws Exception
    {
        Injector injector = createInjector(
                ImmutableMap.<String, String>of(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindServiceAnnouncement(ServiceAnnouncementProvider.class);
                    }
                }
        );

        Set<ServiceAnnouncement> announcements = injector.getInstance(Key.get(new TypeLiteral<Set<ServiceAnnouncement>>()
        {
        }));
        Assert.assertEquals(announcements, ImmutableSet.of(ANNOUNCEMENT));
    }

    @Test
    public void testBindAnnouncementProviderInstance()
            throws Exception
    {
        Injector injector = createInjector(
                ImmutableMap.<String, String>of(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindServiceAnnouncement(new ServiceAnnouncementProvider());
                    }
                }
        );

        Set<ServiceAnnouncement> announcements = injector.getInstance(Key.get(new TypeLiteral<Set<ServiceAnnouncement>>()
        {
        }));
        Assert.assertEquals(announcements, ImmutableSet.of(ANNOUNCEMENT));
    }

    @Test
    public void testBindSelectorString()
            throws Exception
    {
        Injector injector = createInjector(
                ImmutableMap.<String, String>of(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindSelector("apple");
                    }
                }
        );

        assertCanCreateServiceSelector(injector, "apple", ServiceSelectorConfig.DEFAULT_POOL);
    }

    @Test
    public void testBindSelectorAnnotation()
            throws Exception
    {
        Injector injector = createInjector(
                ImmutableMap.<String, String>of(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindSelector(serviceType("apple"));
                    }
                }
        );

        assertCanCreateServiceSelector(injector, "apple", ServiceSelectorConfig.DEFAULT_POOL);
    }

    @Test
    public void testBindSelectorStringWithPool()
            throws Exception
    {
        Injector injector = createInjector(
                ImmutableMap.of("discovery.apple.pool", "apple-pool"),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindSelector("apple");
                    }
                }
        );

        assertCanCreateServiceSelector(injector, "apple", "apple-pool");
    }

    @Test
    public void testBindSelectorAnnotationWithPool()
            throws Exception
    {
        Injector injector = createInjector(
                ImmutableMap.of("discovery.apple.pool", "apple-pool"),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindSelector(serviceType("apple"));
                    }
                }
        );

        assertCanCreateServiceSelector(injector, "apple", "apple-pool");
    }

    @Test
    public void testBindHttpServiceBalancerString()
            throws Exception
    {
        Injector injector = createInjector(
                ImmutableMap.<String, String>of(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindHttpBalancer("apple");
                    }
                }
        );

        Assert.assertNotNull(injector.getInstance(Key.get(HttpServiceBalancer.class, serviceType("apple"))));
    }

    @Test
    public void testBindHttpServiceBalancerAnnotation()
            throws Exception
    {
        Injector injector = createInjector(
                ImmutableMap.<String, String>of(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindHttpBalancer(serviceType("apple"));
                    }
                }
        );

        Assert.assertNotNull(injector.getInstance(Key.get(HttpServiceBalancer.class, serviceType("apple"))));
    }

    @Test
    public void testBindHttpServiceBalancerStringWithPool()
            throws Exception
    {
        Injector injector = createInjector(
                ImmutableMap.of("discovery.apple.pool", "apple-pool"),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindHttpBalancer("apple");
                    }
                }
        );

        Assert.assertNotNull(injector.getInstance(Key.get(HttpServiceBalancer.class, serviceType("apple"))));
    }

    @Test
    public void testBindHttpServiceBalancerAnnotationWithPool()
            throws Exception
    {
        Injector injector = createInjector(
                ImmutableMap.of("discovery.apple.pool", "apple-pool"),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindHttpBalancer(serviceType("apple"));
                    }
                }
        );

        Assert.assertNotNull(injector.getInstance(Key.get(HttpServiceBalancer.class, serviceType("apple"))));
    }

    @Test
    public void testBindHttpClientWithDefaultAnnotation()
            throws Exception
    {
        Injector injector = createInjector(
                ImmutableMap.of(
                        "discovery.foo.pool", "foo-pool",
                        "foo.http-client.read-timeout", "1s",
                        "foo.http-client.max-attempts", "2"
                ),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindDiscoveredHttpClient("foo");
                    }
                }
        );

        assertNotNull(injector.getInstance(Key.get(HttpClient.class, serviceType("foo"))));
    }

    @Test
    public void testBindHttpClientWithNameAndDefaultAnnotation()
            throws Exception
    {
        Injector injector = createInjector(
                ImmutableMap.of(
                        "discovery.foo.pool", "foo-pool",
                        "bar.http-client.read-timeout", "1s",
                        "bar.http-client.max-attempts", "2"
                ),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindDiscoveredHttpClient("bar", serviceType("foo"));
                    }
                });

        assertNotNull(injector.getInstance(Key.get(HttpClient.class, serviceType("foo"))));
    }

    @Test
    public void testBindHttpClientWithoutFilters()
            throws Exception
    {
        Injector injector = createInjector(
                ImmutableMap.of("discovery.foo.pool", "foo-pool"),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindDiscoveredHttpClient("foo", FooClient.class);
                    }
                }
        );

        assertNotNull(injector.getInstance(Key.get(HttpClient.class, FooClient.class)));
    }

    @Test
    public void testBindHttpClientWithAliases()
            throws Exception
    {
        Injector injector = createInjector(
                ImmutableMap.of("discovery.foo.pool", "foo-pool"),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindDiscoveredHttpClient("foo", FooClient.class)
                                .withAlias(FooAlias1.class)
                                .withAliases(ImmutableList.of(FooAlias2.class, FooAlias3.class));
                    }
                }
        );

        HttpClient client = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        assertSame(injector.getInstance(Key.get(HttpClient.class, FooAlias1.class)), client);
        assertSame(injector.getInstance(Key.get(HttpClient.class, FooAlias2.class)), client);
        assertSame(injector.getInstance(Key.get(HttpClient.class, FooAlias3.class)), client);

    }

    @Test
    public void testBindingMultipleFiltersAndClients()
            throws Exception
    {
        Injector injector = createInjector(
                ImmutableMap.of(
                        "discovery.foo.pool", "foo-pool",
                        "discovery.bar.pool", "bar-pool"),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindDiscoveredHttpClient("foo", FooClient.class)
                                .withFilter(TestingRequestFilter.class)
                                .withFilter(AnotherHttpRequestFilter.class)
                                .withTracing();

                        BalancingHttpClientBindingBuilder builder = discoveryBinder(binder).bindDiscoveredHttpClient("bar", BarClient.class);
                        builder.withFilter(TestingRequestFilter.class);
                        builder.addFilterBinding().to(AnotherHttpRequestFilter.class);
                    }
                });

        assertNotNull(injector.getInstance(Key.get(HttpClient.class, FooClient.class)));
        assertNotNull(injector.getInstance(Key.get(HttpClient.class, BarClient.class)));
    }

    @Test
    public void testPrivateThreadPool()
            throws Exception
    {
        Injector injector = createInjector(
                ImmutableMap.of("discovery.foo.pool", "foo-pool"),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        discoveryBinder(binder).bindDiscoveredHttpClient("foo", FooClient.class)
                                .withPrivateIoThreadPool();
                    }
                }
        );

        HttpClient fooClient = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        assertNotNull(fooClient);
    }

    private static void assertCanCreateServiceSelector(Injector injector, String expectedType, String expectedPool)
    {
        ServiceSelector actualServiceSelector = injector.getInstance(Key.get(ServiceSelector.class, serviceType(expectedType)));
        Assert.assertNotNull(actualServiceSelector);
        Assert.assertEquals(actualServiceSelector.getType(), expectedType);
        Assert.assertEquals(actualServiceSelector.getPool(), expectedPool);
    }

    private Injector createInjector(ImmutableMap<String, String> configurationProperties, Module module)
            throws Exception
    {
        return Bootstrap.bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(
                        new TestingNodeModule(),
                        new TestingDiscoveryModule(),
                        new TestingMBeanModule(),
                        new ReportingModule(),
                        module
                )
                .quiet()
                .setRequiredConfigurationProperties(configurationProperties)
                .initialize();
    }

    private static final ServiceAnnouncement ANNOUNCEMENT = ServiceAnnouncement.serviceAnnouncement("apple").addProperty("a", "apple").build();

    private static class ServiceAnnouncementProvider implements Provider<ServiceAnnouncement>
    {
        @Override
        public ServiceAnnouncement get()
        {
            return ANNOUNCEMENT;
        }
    }

    @Retention(RUNTIME)
    @Target({ElementType.PARAMETER})
    @Qualifier
    public @interface FooClient
    {
    }

    @Retention(RUNTIME)
    @Target({ElementType.PARAMETER})
    @Qualifier
    public @interface FooAlias1
    {
    }

    @Retention(RUNTIME)
    @Target({ElementType.PARAMETER})
    @Qualifier
    public @interface FooAlias2
    {
    }

    @Retention(RUNTIME)
    @Target({ElementType.PARAMETER})
    @Qualifier
    public @interface FooAlias3
    {
    }

    @Retention(RUNTIME)
    @Target({ElementType.PARAMETER})
    @Qualifier
    public @interface BarClient
    {
    }

    public static class TestingRequestFilter
            implements HttpRequestFilter
    {
        @Override
        public Request filterRequest(Request request)
        {
            return fromRequest(request)
                    .addHeader("x-custom-filter", "customvalue")
                    .build();
        }
    }

    public static class AnotherHttpRequestFilter
            implements HttpRequestFilter
    {
        @Override
        public Request filterRequest(Request request)
        {
            return request;
        }
    }
}
