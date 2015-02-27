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
package com.proofpoint.http.client;

import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateBinder;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.http.client.HttpClientModule.JettyIoPoolManager;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import org.testng.annotations.Test;
import org.weakref.jmx.Managed;

import javax.inject.Qualifier;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapApplication;
import static com.proofpoint.http.client.HttpClientBinder.HttpClientBindingBuilder;
import static com.proofpoint.http.client.HttpClientBinder.httpClientBinder;
import static com.proofpoint.testing.Assertions.assertInstanceOf;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

public class TestHttpClientBinder
{
    @Test
    public void testBindingMultipleFiltersAndClients()
            throws Exception
    {
        Injector injector = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(
                        new Module()
                        {
                            @Override
                            public void configure(Binder binder)
                            {
                                httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
                                        .withFilter(TestingRequestFilter.class)
                                        .withFilter(AnotherHttpRequestFilter.class)
                                        .withTracing();

                                HttpClientBindingBuilder builder = httpClientBinder(binder).bindHttpClient("bar", BarClient.class);
                                builder.withFilter(TestingRequestFilter.class);
                                builder.addFilterBinding().to(AnotherHttpRequestFilter.class);
                            }
                        })
                .quiet()
                .initialize();

        assertFilterCount(injector.getInstance(Key.get(HttpClient.class, FooClient.class)), 3);
        assertFilterCount(injector.getInstance(Key.get(HttpClient.class, BarClient.class)), 2);

        // a pool should not be registered for this Foo
        assertNull(injector.getExistingBinding(Key.get(JettyIoPoolManager.class, FooClient.class)));
        assertNull(injector.getExistingBinding(Key.get(JettyIoPoolManager.class, BarClient.class)));

        assertPoolsDestroyProperly(injector);
    }

    @Test
    public void testBindClientWithFilter()
            throws Exception
    {
        Injector injector = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(
                        new Module()
                        {
                            @Override
                            public void configure(Binder binder)
                            {
                                httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
                                        .withFilter(TestingRequestFilter.class)
                                        .withFilter(AnotherHttpRequestFilter.class)
                                        .withTracing();
                            }
                        })
                .quiet()
                .initialize();


        HttpClient httpClient = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        assertFilterCount(httpClient, 3);

        // a pool should not be registered for this Foo
        assertNull(injector.getExistingBinding(Key.get(JettyIoPoolManager.class, FooClient.class)));
        assertNull(injector.getExistingBinding(Key.get(JettyIoPoolManager.class, BarClient.class)));

        assertPoolsDestroyProperly(injector);
    }

    @Test
    public void testWithoutFilters()
            throws Exception
    {
        Injector injector = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(
                        new Module()
                        {
                            @Override
                            public void configure(Binder binder)
                            {
                                httpClientBinder(binder).bindHttpClient("foo", FooClient.class);
                            }
                        })
                .quiet()
                .initialize();

        assertNotNull(injector.getInstance(Key.get(HttpClient.class, FooClient.class)));

        // a pool should not be registered for this Foo
        assertNull(injector.getExistingBinding(Key.get(JettyIoPoolManager.class, FooClient.class)));

        assertPoolsDestroyProperly(injector);
    }

    @Test
    public void testAliases()
            throws Exception
    {
        Injector injector = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(
                        new Module()
                        {
                            @Override
                            public void configure(Binder binder)
                            {
                                httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
                                        .withAlias(FooAlias1.class)
                                        .withAliases(ImmutableList.of(FooAlias2.class, FooAlias3.class));
                            }
                        })
                .quiet()
                .initialize();

        HttpClient client = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        assertSame(injector.getInstance(Key.get(HttpClient.class, FooAlias1.class)), client);
        assertSame(injector.getInstance(Key.get(HttpClient.class, FooAlias2.class)), client);
        assertSame(injector.getInstance(Key.get(HttpClient.class, FooAlias3.class)), client);

        // a private pool should not be registered for these clients
        assertNull(injector.getExistingBinding(Key.get(JettyIoPoolManager.class, FooClient.class)));
        assertNull(injector.getExistingBinding(Key.get(JettyIoPoolManager.class, FooAlias1.class)));
        assertNull(injector.getExistingBinding(Key.get(JettyIoPoolManager.class, FooAlias2.class)));
        assertNull(injector.getExistingBinding(Key.get(JettyIoPoolManager.class, FooAlias3.class)));

        assertPoolsDestroyProperly(injector);
    }

    @Test
    public void testMultipleClients()
            throws Exception
    {
        Injector injector = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(
                        new Module()
                        {
                            @Override
                            public void configure(Binder binder)
                            {
                                httpClientBinder(binder).bindHttpClient("foo", FooClient.class);
                                httpClientBinder(binder).bindHttpClient("bar", BarClient.class);
                            }
                        })
                .quiet()
                .initialize();

        HttpClient fooClient = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        HttpClient barClient = injector.getInstance(Key.get(HttpClient.class, BarClient.class));
        assertNotSame(fooClient, barClient);

        assertNull(injector.getExistingBinding(Key.get(JettyIoPoolManager.class, FooClient.class)));
        assertNull(injector.getExistingBinding(Key.get(JettyIoPoolManager.class, BarClient.class)));


        // a private pool should not be registered for these clients
        assertNull(injector.getExistingBinding(Key.get(JettyIoPoolManager.class, FooClient.class)));
        assertNull(injector.getExistingBinding(Key.get(JettyIoPoolManager.class, BarClient.class)));

        assertPoolsDestroyProperly(injector);
    }

    @Test
    public void testPrivateThreadPool()
            throws Exception
    {
        Injector injector = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(
                        new Module()
                        {
                            @Override
                            public void configure(Binder binder)
                            {
                                binder.requireExplicitBindings();
                                binder.disableCircularProxies();
                                httpClientBinder(binder).bindHttpClient("foo", FooClient.class).withPrivateIoThreadPool();
                            }
                        })
                .quiet()
                .initialize();

        HttpClient fooClient = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        assertNotNull(fooClient);

        assertPrivatePools(injector, FooClient.class);

        assertPoolsDestroyProperly(injector, FooClient.class);
    }

    @Test
    public void testMultiplePrivateThreadPools()
            throws Exception
    {
        Injector injector = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(
                        new Module()
                        {
                            @Override
                            public void configure(Binder binder)
                            {
                                httpClientBinder(binder).bindHttpClient("foo", FooClient.class).withPrivateIoThreadPool();
                                httpClientBinder(binder).bindHttpClient("bar", BarClient.class).withPrivateIoThreadPool();
                            }
                        })
                .quiet()
                .initialize();

        HttpClient fooClient = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        HttpClient barClient = injector.getInstance(Key.get(HttpClient.class, BarClient.class));
        assertNotSame(fooClient, barClient);

        assertPrivatePools(injector, FooClient.class, BarClient.class);

        assertPoolsDestroyProperly(injector, FooClient.class, BarClient.class);
    }

    @Test
    public void testMultiplePrivateAndSharedThreadPools()
            throws Exception
    {
        Injector injector = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(
                        new Module()
                        {
                            @Override
                            public void configure(Binder binder)
                            {
                                httpClientBinder(binder).bindHttpClient("foo", FooClient.class);
                                httpClientBinder(binder).bindHttpClient("bar", BarClient.class).withPrivateIoThreadPool();
                            }
                        })
                .quiet()
                .initialize();

        HttpClient fooClient = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        HttpClient barClient = injector.getInstance(Key.get(HttpClient.class, BarClient.class));
        assertNotSame(fooClient, barClient);

        // a pool should not be registered for this Foo
        assertNull(injector.getExistingBinding(Key.get(JettyIoPoolManager.class, FooClient.class)));

        assertPrivatePools(injector, BarClient.class);

        assertPoolsDestroyProperly(injector, BarClient.class);
    }

    @SafeVarargs
    private final void assertPrivatePools(Injector injector, Class<? extends Annotation>... privateClientAnnotations)
            throws Exception
    {
        JettyIoPoolManager sharedPool = injector.getInstance(Key.get(JettyIoPoolManager.class));
        // pool should not be destroyed yet
        assertFalse(sharedPool.isDestroyed());

        Set<JettyIoPoolManager> privatePools = Collections.newSetFromMap(new IdentityHashMap<JettyIoPoolManager, Boolean>());
        for (Class<? extends Annotation> privateClientAnnotation : privateClientAnnotations) {
            assertNotNull(injector.getExistingBinding(Key.get(JettyIoPoolManager.class, privateClientAnnotation)));
            JettyIoPoolManager privatePool = injector.getInstance(Key.get(JettyIoPoolManager.class, privateClientAnnotation));

            // pool should not be the same as any other pool
            assertNotSame(privatePool, sharedPool);
            assertFalse(privatePools.contains(privatePool));
            privatePools.add(privatePool);
        }
    }

    @Test
    public void testPrivateBindClient()
            throws Exception
    {
        Injector injector = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(
                        new Module()
                        {
                            @Override
                            public void configure(Binder binder)
                            {
                                newExporter(binder).export(ManagedClass.class);
                                PrivateBinder privateBinder = binder.newPrivateBinder();
                                HttpClientBinder.httpClientPrivateBinder(privateBinder, binder).bindHttpClient("foo", FooClient.class);
                                privateBinder.bind(ExposeHttpClient.class);
                                privateBinder.expose(ExposeHttpClient.class);
                            }
                        })
                .quiet()
                .initialize();

        assertNotNull(injector.getInstance(ExposeHttpClient.class).httpClient);

        assertPoolsDestroyProperly(injector);
    }

    @Test
    public void testNormalAndPrivateBindClients()
            throws Exception
    {
        Injector injector = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(
                        new Module()
                        {
                            @Override
                            public void configure(Binder binder)
                            {
                                newExporter(binder).export(ManagedClass.class);
                                PrivateBinder privateBinder = binder.newPrivateBinder();
                                HttpClientBinder.httpClientPrivateBinder(privateBinder, binder).bindHttpClient("foo", FooClient.class);
                                privateBinder.bind(ExposeHttpClient.class);
                                privateBinder.expose(ExposeHttpClient.class);
                                HttpClientBinder.httpClientBinder(binder).bindHttpClient("bar", BarClient.class);
                            }
                        })
                .quiet()
                .initialize();

        assertNotNull(injector.getInstance(ExposeHttpClient.class).httpClient);
        assertNotNull(injector.getInstance(Key.get(HttpClient.class, BarClient.class)));

        assertPoolsDestroyProperly(injector);
    }

    @SafeVarargs
    private final void assertPoolsDestroyProperly(Injector injector, Class<? extends Annotation>... privateClientAnnotations)
            throws Exception
    {
        JettyIoPoolManager sharedPool = injector.getInstance(Key.get(JettyIoPoolManager.class));
        assertFalse(sharedPool.isDestroyed());

        Set<JettyIoPoolManager> privatePools = Collections.newSetFromMap(new IdentityHashMap<JettyIoPoolManager, Boolean>());
        for (Class<? extends Annotation> privateClientAnnotation : privateClientAnnotations) {
            JettyIoPoolManager privatePool = injector.getInstance(Key.get(JettyIoPoolManager.class, privateClientAnnotation));
            assertFalse(privatePool.isDestroyed());
        }

        injector.getInstance(LifeCycleManager.class).stop();

        assertTrue(sharedPool.isDestroyed());
        for (JettyIoPoolManager privatePool : privatePools) {
            assertTrue(privatePool.isDestroyed());
        }
    }

    private static void assertFilterCount(HttpClient httpClient, int filterCount)
    {
        assertNotNull(httpClient);
        assertInstanceOf(httpClient, JettyHttpClient.class);
        assertEquals(((JettyHttpClient) httpClient).getRequestFilters().size(), filterCount);
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

    public static class AnotherHttpRequestFilter
            implements HttpRequestFilter
    {
        @Override
        public Request filterRequest(Request request)
        {
            return request;
        }
    }

    private static class ExposeHttpClient
    {
        public final HttpClient httpClient;

        @Inject
        private ExposeHttpClient(@FooClient HttpClient httpClient)
        {
            this.httpClient = httpClient;
        }
    }

    private static class ManagedClass
    {
        @Managed
        public int getInt()
        {
            return 0;
        }
    }
}
