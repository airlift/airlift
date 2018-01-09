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
package io.airlift.http.client;

import com.google.common.collect.ImmutableList;
import com.google.inject.Injector;
import com.google.inject.Key;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.tracetoken.TraceTokenModule;
import io.airlift.units.Duration;
import org.testng.annotations.Test;

import javax.inject.Qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static io.airlift.http.client.HttpClientBinder.httpClientBinder;
import static io.airlift.testing.Assertions.assertInstanceOf;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class TestHttpClientBinder
{
    @Test
    public void testConfigDefaults()
            throws Exception
    {
        Injector injector = new Bootstrap(
                binder -> httpClientBinder(binder)
                        .bindHttpClient("foo", FooClient.class)
                        .withConfigDefaults(config -> config.setRequestTimeout(new Duration(33, MINUTES))),
                new TraceTokenModule())
                .quiet()
                .strictConfig()
                .initialize();

        JettyHttpClient httpClient = (JettyHttpClient) injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        assertEquals(httpClient.getRequestTimeoutMillis(), MINUTES.toMillis(33));
    }

    @Test
    public void testGlobalFilterBinding()
            throws Exception
    {
        HttpRequestFilter globalFilter1 = (r) -> r;
        HttpRequestFilter globalFilter2 = (r) -> r;
        HttpRequestFilter filter1 = (r) -> r;
        HttpRequestFilter filter2 = (r) -> r;
        Injector injector = new Bootstrap(
                binder -> {
                    httpClientBinder(binder)
                            .addGlobalFilterBinding().toInstance(globalFilter1);
                    httpClientBinder(binder)
                            .bindGlobalFilter(globalFilter2);
                    httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
                            .addFilterBinding().toInstance(filter1);
                    httpClientBinder(binder).bindHttpClient("bar", BarClient.class)
                            .addFilterBinding().toInstance(filter2);
                },
                new TraceTokenModule())
                .quiet()
                .strictConfig()
                .initialize();

        JettyHttpClient fooClient = (JettyHttpClient) injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        assertFilterCount(fooClient, 3);
        assertEquals(fooClient.getRequestFilters().get(0), globalFilter1);
        assertEquals(fooClient.getRequestFilters().get(1), globalFilter2);
        assertEquals(fooClient.getRequestFilters().get(2), filter1);

        JettyHttpClient barClient = (JettyHttpClient) injector.getInstance(Key.get(HttpClient.class, BarClient.class));
        assertFilterCount(barClient, 3);
        assertEquals(barClient.getRequestFilters().get(0), globalFilter1);
        assertEquals(barClient.getRequestFilters().get(1), globalFilter2);
        assertEquals(barClient.getRequestFilters().get(2), filter2);
    }

    @Test
    public void testBindingMultipleFiltersAndClients()
            throws Exception
    {
        Injector injector = new Bootstrap(
                binder -> {
                    httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
                            .withFilter(TestingRequestFilter.class)
                            .withFilter(AnotherHttpRequestFilter.class)
                            .withTracing();

                    httpClientBinder(binder).bindHttpClient("bar", BarClient.class)
                            .withFilter(TestingRequestFilter.class)
                            .addFilterBinding().to(AnotherHttpRequestFilter.class);
                },
                new TraceTokenModule())
                .quiet()
                .strictConfig()
                .initialize();

        assertFilterCount(injector.getInstance(Key.get(HttpClient.class, FooClient.class)), 3);
        assertFilterCount(injector.getInstance(Key.get(HttpClient.class, BarClient.class)), 2);
    }

    @Test
    public void testBindClientWithFilter()
            throws Exception
    {
        Injector injector = new Bootstrap(
                binder -> httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
                        .withFilter(TestingRequestFilter.class)
                        .withFilter(AnotherHttpRequestFilter.class)
                        .withTracing(),
                new TraceTokenModule())
                .quiet()
                .strictConfig()
                .initialize();

        HttpClient httpClient = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        assertFilterCount(httpClient, 3);
    }

    @Test
    public void testWithoutFilters()
            throws Exception
    {
        Injector injector = new Bootstrap(
                binder -> httpClientBinder(binder).bindHttpClient("foo", FooClient.class))
                .quiet()
                .strictConfig()
                .initialize();

        assertNotNull(injector.getInstance(Key.get(HttpClient.class, FooClient.class)));
    }

    @Test
    public void testAliases()
            throws Exception
    {
        Injector injector = new Bootstrap(
                binder -> httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
                        .withAlias(FooAlias1.class)
                        .withAliases(ImmutableList.of(FooAlias2.class, FooAlias3.class)))
                .quiet()
                .strictConfig()
                .initialize();

        HttpClient client = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        assertSame(injector.getInstance(Key.get(HttpClient.class, FooAlias1.class)), client);
        assertSame(injector.getInstance(Key.get(HttpClient.class, FooAlias2.class)), client);
        assertSame(injector.getInstance(Key.get(HttpClient.class, FooAlias3.class)), client);
    }

    @Test
    public void testBindClientWithAliases()
            throws Exception
    {
        Injector injector = new Bootstrap(
                binder -> httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
                        .withAlias(FooAlias1.class)
                        .withAlias(FooAlias2.class))
                .quiet()
                .strictConfig()
                .initialize();

        HttpClient client = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        assertSame(injector.getInstance(Key.get(HttpClient.class, FooAlias1.class)), client);
        assertSame(injector.getInstance(Key.get(HttpClient.class, FooAlias2.class)), client);
    }

    @Test
    public void testMultipleClients()
            throws Exception
    {
        Injector injector = new Bootstrap(
                binder -> {
                    httpClientBinder(binder).bindHttpClient("foo", FooClient.class);
                    httpClientBinder(binder).bindHttpClient("bar", BarClient.class);
                })
                .quiet()
                .strictConfig()
                .initialize();

        HttpClient fooClient = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        HttpClient barClient = injector.getInstance(Key.get(HttpClient.class, BarClient.class));
        assertNotSame(fooClient, barClient);
    }

    @Test
    public void testClientShutdown()
            throws Exception
    {
        Injector injector = new Bootstrap(
                binder -> {
                    httpClientBinder(binder).bindHttpClient("foo", FooClient.class);
                    httpClientBinder(binder).bindHttpClient("bar", BarClient.class);
                })
                .quiet()
                .strictConfig()
                .initialize();

        HttpClient fooClient = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        HttpClient barClient = injector.getInstance(Key.get(HttpClient.class, BarClient.class));

        assertFalse(fooClient.isClosed());
        assertFalse(barClient.isClosed());

        injector.getInstance(LifeCycleManager.class).stop();

        assertTrue(fooClient.isClosed());
        assertTrue(barClient.isClosed());
    }

    private static void assertFilterCount(HttpClient httpClient, int filterCount)
    {
        assertNotNull(httpClient);
        assertInstanceOf(httpClient, JettyHttpClient.class);
        assertEquals(((JettyHttpClient) httpClient).getRequestFilters().size(), filterCount);
    }

    @Retention(RUNTIME)
    @Target(ElementType.PARAMETER)
    @Qualifier
    public @interface FooClient
    {
    }

    @Retention(RUNTIME)
    @Target(ElementType.PARAMETER)
    @Qualifier
    public @interface FooAlias1
    {
    }

    @Retention(RUNTIME)
    @Target(ElementType.PARAMETER)
    @Qualifier
    public @interface FooAlias2
    {
    }

    @Retention(RUNTIME)
    @Target(ElementType.PARAMETER)
    @Qualifier
    public @interface FooAlias3
    {
    }

    @Retention(RUNTIME)
    @Target(ElementType.PARAMETER)
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
}
