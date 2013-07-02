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
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateBinder;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.http.client.netty.NettyAsyncHttpClient;
import com.proofpoint.http.client.netty.NettyIoPool;
import com.proofpoint.tracetoken.TraceTokenModule;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.weakref.jmx.Managed;

import javax.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Collections;

import static com.proofpoint.http.client.HttpClientBinder.HttpClientBindingBuilder;
import static com.proofpoint.http.client.HttpClientBinder.httpClientBinder;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

public class TestHttpClientBinder
{
    @Test
    public void testBindingMultipleFiltersAndClients()
    {
        Injector injector = Guice.createInjector(
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
                },
                new ConfigurationModule(new ConfigurationFactory(Collections.<String, String>emptyMap())),
                new TraceTokenModule());

        assertFilterCount(injector.getInstance(Key.get(HttpClient.class, FooClient.class)), 3);
        assertFilterCount(injector.getInstance(Key.get(HttpClient.class, BarClient.class)), 2);
    }

    @Test
    public void testBindAsyncClientWithFilter()
    {
        Injector injector = Guice.createInjector(
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        httpClientBinder(binder).bindAsyncHttpClient("foo", FooClient.class)
                                .withFilter(TestingRequestFilter.class)
                                .withFilter(AnotherHttpRequestFilter.class)
                                .withTracing();
                    }
                },
                new ConfigurationModule(new ConfigurationFactory(Collections.<String, String>emptyMap())),
                new TraceTokenModule());

        HttpClient httpClient = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        AsyncHttpClient asyncHttpClient = injector.getInstance(Key.get(AsyncHttpClient.class, FooClient.class));
        assertSame(httpClient, asyncHttpClient);
        assertFilterCount(httpClient, 3);
        assertFilterCount(asyncHttpClient, 3);

        injector.getInstance(NettyIoPool.class).close();
    }

    @Test
    public void testWithoutFilters()
    {
        Injector injector = Guice.createInjector(
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        httpClientBinder(binder).bindHttpClient("foo", FooClient.class);
                    }
                },
                new ConfigurationModule(new ConfigurationFactory(Collections.<String, String>emptyMap())));

        assertNotNull(injector.getInstance(Key.get(HttpClient.class, FooClient.class)));
    }

    @Test
    public void testAliases()
    {
        Injector injector = Guice.createInjector(
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
                                .withAlias(FooAlias1.class)
                                .withAliases(ImmutableList.of(FooAlias2.class, FooAlias3.class));
                    }
                },
                new ConfigurationModule(new ConfigurationFactory(Collections.<String, String>emptyMap())));

        HttpClient client = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        assertSame(injector.getInstance(Key.get(HttpClient.class, FooAlias1.class)), client);
        assertSame(injector.getInstance(Key.get(HttpClient.class, FooAlias2.class)), client);
        assertSame(injector.getInstance(Key.get(HttpClient.class, FooAlias3.class)), client);
    }

    @Test
    public void testBindAsyncClientWithAliases()
    {
        Injector injector = Guice.createInjector(
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        httpClientBinder(binder).bindAsyncHttpClient("foo", FooClient.class)
                                .withAlias(FooAlias1.class)
                                .withAlias(FooAlias2.class);
                    }
                },
                new ConfigurationModule(new ConfigurationFactory(Collections.<String, String>emptyMap())));

        AsyncHttpClient client = injector.getInstance(Key.get(AsyncHttpClient.class, FooClient.class));
        assertSame(injector.getInstance(Key.get(AsyncHttpClient.class, FooAlias1.class)), client);
        assertSame(injector.getInstance(Key.get(AsyncHttpClient.class, FooAlias2.class)), client);

        assertSame(injector.getInstance(Key.get(HttpClient.class, FooClient.class)), client);
        assertSame(injector.getInstance(Key.get(HttpClient.class, FooAlias1.class)), client);
        assertSame(injector.getInstance(Key.get(HttpClient.class, FooAlias2.class)), client);

        injector.getInstance(NettyIoPool.class).close();
    }

    @Test
    public void testMultipleAsyncClients()
    {
        Injector injector = Guice.createInjector(
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        httpClientBinder(binder).bindAsyncHttpClient("foo", FooClient.class);
                        httpClientBinder(binder).bindAsyncHttpClient("bar", BarClient.class);
                    }
                },
                new ConfigurationModule(new ConfigurationFactory(Collections.<String, String>emptyMap())));

        AsyncHttpClient fooClient = injector.getInstance(Key.get(AsyncHttpClient.class, FooClient.class));
        AsyncHttpClient barClient = injector.getInstance(Key.get(AsyncHttpClient.class, BarClient.class));
        Assert.assertNotSame(fooClient, barClient);

        assertNull(injector.getExistingBinding(Key.get(NettyIoPool.class, FooClient.class)));
        assertNull(injector.getExistingBinding(Key.get(NettyIoPool.class, BarClient.class)));

        injector.getInstance(NettyIoPool.class).close();
    }

    @Test
    public void testPrivateThreadPool()
    {
        Injector injector = Guice.createInjector(
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        httpClientBinder(binder).bindAsyncHttpClient("foo", FooClient.class).withPrivateIoThreadPool();
                    }
                },
                new ConfigurationModule(new ConfigurationFactory(Collections.<String, String>emptyMap())));

        AsyncHttpClient fooClient = injector.getInstance(Key.get(AsyncHttpClient.class, FooClient.class));
        assertNotNull(fooClient);

        assertNotNull(injector.getExistingBinding(Key.get(NettyIoPool.class, FooClient.class)));

        injector.getInstance(NettyIoPool.class).close();
        injector.getInstance(Key.get(NettyIoPool.class, FooClient.class)).close();
    }

    @Test
    public void testMultiplePrivateThreadPools()
    {
        Injector injector = Guice.createInjector(
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        httpClientBinder(binder).bindAsyncHttpClient("foo", FooClient.class).withPrivateIoThreadPool();
                        httpClientBinder(binder).bindAsyncHttpClient("bar", BarClient.class).withPrivateIoThreadPool();
                    }
                },
                new ConfigurationModule(new ConfigurationFactory(Collections.<String, String>emptyMap())));

        AsyncHttpClient fooClient = injector.getInstance(Key.get(AsyncHttpClient.class, FooClient.class));
        AsyncHttpClient barClient = injector.getInstance(Key.get(AsyncHttpClient.class, BarClient.class));
        Assert.assertNotSame(fooClient, barClient);

        assertNotNull(injector.getExistingBinding(Key.get(NettyIoPool.class, FooClient.class)));
        assertNotNull(injector.getExistingBinding(Key.get(NettyIoPool.class, BarClient.class)));

        injector.getInstance(NettyIoPool.class).close();
        injector.getInstance(Key.get(NettyIoPool.class, FooClient.class)).close();
        injector.getInstance(Key.get(NettyIoPool.class, BarClient.class)).close();
    }

    @Test
    public void testMultiplePrivateAndSharedThreadPools()
    {
        Injector injector = Guice.createInjector(
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        httpClientBinder(binder).bindAsyncHttpClient("foo", FooClient.class);
                        httpClientBinder(binder).bindAsyncHttpClient("bar", BarClient.class).withPrivateIoThreadPool();
                    }
                },
                new ConfigurationModule(new ConfigurationFactory(Collections.<String, String>emptyMap())));

        AsyncHttpClient fooClient = injector.getInstance(Key.get(AsyncHttpClient.class, FooClient.class));
        AsyncHttpClient barClient = injector.getInstance(Key.get(AsyncHttpClient.class, BarClient.class));
        assertNotSame(fooClient, barClient);

        assertNull(injector.getExistingBinding(Key.get(NettyIoPool.class, FooClient.class)));
        assertNotNull(injector.getExistingBinding(Key.get(NettyIoPool.class, BarClient.class)));

        injector.getInstance(NettyIoPool.class).close();
        injector.getInstance(Key.get(NettyIoPool.class, BarClient.class)).close();
    }

    @Test
    public void testPrivateBindClient()
    {
        Injector injector = Guice.createInjector(
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
                },
                new ConfigurationModule(new ConfigurationFactory(Collections.<String, String>emptyMap())));

        assertNotNull(injector.getInstance(ExposeHttpClient.class).httpClient);
    }

    @Test
    public void testPrivateBindAsyncClient()
    {
        Injector injector = Guice.createInjector(
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        newExporter(binder).export(ManagedClass.class);
                        PrivateBinder privateBinder = binder.newPrivateBinder();
                        HttpClientBinder.httpClientPrivateBinder(privateBinder, binder).bindAsyncHttpClient("foo", FooClient.class);
                        privateBinder.bind(ExposeHttpClient.class);
                        privateBinder.expose(ExposeHttpClient.class);
                    }
                },
                new ConfigurationModule(new ConfigurationFactory(Collections.<String, String>emptyMap())));

        assertNotNull(injector.getInstance(ExposeHttpClient.class).httpClient);
    }

    @Test
    public void testNormalAndPrivateBindAsyncClients()
    {
        Injector injector = Guice.createInjector(
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        newExporter(binder).export(ManagedClass.class);
                        PrivateBinder privateBinder = binder.newPrivateBinder();
                        HttpClientBinder.httpClientPrivateBinder(privateBinder, binder).bindAsyncHttpClient("foo", FooClient.class);
                        privateBinder.bind(ExposeHttpClient.class);
                        privateBinder.expose(ExposeHttpClient.class);
                        HttpClientBinder.httpClientBinder(binder).bindAsyncHttpClient("bar", BarClient.class);
                    }
                },
                new ConfigurationModule(new ConfigurationFactory(Collections.<String, String>emptyMap())));

        assertNotNull(injector.getInstance(ExposeHttpClient.class).httpClient);
        assertNotNull(injector.getInstance(Key.get(AsyncHttpClient.class, BarClient.class)));
    }


    private static void assertFilterCount(HttpClient httpClient, int filterCount)
    {
        assertNotNull(httpClient);
        if (httpClient instanceof ApacheHttpClient) {
            assertEquals(((ApacheHttpClient) httpClient).getRequestFilters().size(), filterCount);
        } else if (httpClient instanceof HttpClient) {
            assertEquals(((NettyAsyncHttpClient) httpClient).getRequestFilters().size(), filterCount);
        } else {
            fail("Unexpected HttpClient implementation " + httpClient.getClass().getName());
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
