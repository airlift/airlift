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

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import com.google.inject.BindingAnnotation;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.tracetoken.TraceTokenModule;
import io.airlift.units.Duration;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static io.airlift.http.client.HttpClientBinder.httpClientBinder;
import static io.airlift.testing.Assertions.assertInstanceOf;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

public class TestHttpClientBinder
{
    @Test
    public void testConfigDefaults()
    {
        Injector injector = new Bootstrap(
                binder -> httpClientBinder(binder)
                        .bindHttpClient("foo", FooClient.class)
                        .withConfigDefaults(config -> config.setRequestTimeout(new Duration(33, MINUTES))),
                new TraceTokenModule())
                .quiet()
                .initialize();

        JettyHttpClient httpClient = (JettyHttpClient) injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        assertThat(httpClient.getRequestTimeoutMillis()).isEqualTo(MINUTES.toMillis(33));
    }

    @Test
    public void testGlobalStatusListenerBinding()
    {
        HttpStatusListener globalListener1 = ignore -> {};
        HttpStatusListener globalListener2 = ignore -> {};
        HttpStatusListener listener1 = ignore -> {};
        HttpStatusListener listener2 = ignore -> {};

        Injector injector = new Bootstrap(
                binder -> {
                    httpClientBinder(binder)
                            .addGlobalStatusListenerBinding().toInstance(globalListener1);
                    httpClientBinder(binder)
                            .addGlobalStatusListenerBinding().toInstance(globalListener2);
                    httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
                            .addStatusListenerBinding().toInstance(listener1);
                    httpClientBinder(binder).bindHttpClient("bar", BarClient.class)
                            .addStatusListenerBinding().toInstance(listener2);
                })
                .quiet()
                .initialize();

        JettyHttpClient fooClient = (JettyHttpClient) injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        assertStatusListenerCount(fooClient, 3);
        assertThat(fooClient.getStatusListeners()).containsExactly(globalListener1, globalListener2, listener1);

        JettyHttpClient barClient = (JettyHttpClient) injector.getInstance(Key.get(HttpClient.class, BarClient.class));
        assertStatusListenerCount(barClient, 3);
        assertThat(barClient.getStatusListeners()).containsExactly(globalListener1, globalListener2, listener2);
    }

    @Test
    public void testGlobalFilterBinding()
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
                .initialize();

        JettyHttpClient fooClient = (JettyHttpClient) injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        assertFilterCount(fooClient, 3);
        assertThat(fooClient.getRequestFilters().get(0)).isEqualTo(globalFilter1);
        assertThat(fooClient.getRequestFilters().get(1)).isEqualTo(globalFilter2);
        assertThat(fooClient.getRequestFilters().get(2)).isEqualTo(filter1);

        JettyHttpClient barClient = (JettyHttpClient) injector.getInstance(Key.get(HttpClient.class, BarClient.class));
        assertFilterCount(barClient, 3);
        assertThat(barClient.getRequestFilters().get(0)).isEqualTo(globalFilter1);
        assertThat(barClient.getRequestFilters().get(1)).isEqualTo(globalFilter2);
        assertThat(barClient.getRequestFilters().get(2)).isEqualTo(filter2);
    }

    @Test
    public void testBindClientWithStatusListener()
    {
        Injector injector = new Bootstrap(
                binder -> {
                    httpClientBinder(binder).bindHttpClient("foo", FooClient.class).withStatusListener(TestingStatusListener.class);
                    binder.bind(new TypeLiteral<Multiset<Integer>>() {}).toInstance(HashMultiset.create());
                })
                .quiet()
                .initialize();

        HttpClient httpClient = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        assertStatusListenerCount(httpClient, 1);
    }

    @Test
    public void testBindingMultipleFiltersAndClients()
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
                .initialize();

        assertFilterCount(injector.getInstance(Key.get(HttpClient.class, FooClient.class)), 3);
        assertFilterCount(injector.getInstance(Key.get(HttpClient.class, BarClient.class)), 2);
    }

    @Test
    public void testBindClientWithFilter()
    {
        Injector injector = new Bootstrap(
                binder -> httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
                        .withFilter(TestingRequestFilter.class)
                        .withFilter(AnotherHttpRequestFilter.class)
                        .withTracing(),
                new TraceTokenModule())
                .quiet()
                .initialize();

        HttpClient httpClient = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        assertFilterCount(httpClient, 3);
    }

    @Test
    public void testWithoutFilters()
    {
        Injector injector = new Bootstrap(
                binder -> httpClientBinder(binder).bindHttpClient("foo", FooClient.class))
                .quiet()
                .initialize();

        assertThat(injector.getInstance(Key.get(HttpClient.class, FooClient.class))).isNotNull();
    }

    @Test
    public void testAliases()
    {
        Injector injector = new Bootstrap(
                binder -> httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
                        .withAlias(FooAlias1.class)
                        .withAliases(ImmutableList.of(FooAlias2.class, FooAlias3.class)))
                .quiet()
                .initialize();

        HttpClient client = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        assertThat(injector.getInstance(Key.get(HttpClient.class, FooAlias1.class))).isSameAs(client);
        assertThat(injector.getInstance(Key.get(HttpClient.class, FooAlias2.class))).isSameAs(client);
        assertThat(injector.getInstance(Key.get(HttpClient.class, FooAlias3.class))).isSameAs(client);
    }

    @Test
    public void testBindClientWithAliases()
    {
        Injector injector = new Bootstrap(
                binder -> httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
                        .withAlias(FooAlias1.class)
                        .withAlias(FooAlias2.class))
                .quiet()
                .initialize();

        HttpClient client = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        assertThat(injector.getInstance(Key.get(HttpClient.class, FooAlias1.class))).isSameAs(client);
        assertThat(injector.getInstance(Key.get(HttpClient.class, FooAlias2.class))).isSameAs(client);
    }

    @Test
    public void testMultipleClients()
    {
        Injector injector = new Bootstrap(
                binder -> {
                    httpClientBinder(binder).bindHttpClient("foo", FooClient.class);
                    httpClientBinder(binder).bindHttpClient("bar", BarClient.class);
                })
                .quiet()
                .initialize();

        HttpClient fooClient = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        HttpClient barClient = injector.getInstance(Key.get(HttpClient.class, BarClient.class));
        assertThat(fooClient).isNotSameAs(barClient);
    }

    @Test
    public void testClientShutdown()
    {
        Injector injector = new Bootstrap(
                binder -> {
                    httpClientBinder(binder).bindHttpClient("foo", FooClient.class);
                    httpClientBinder(binder).bindHttpClient("bar", BarClient.class);
                })
                .quiet()
                .initialize();

        HttpClient fooClient = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        HttpClient barClient = injector.getInstance(Key.get(HttpClient.class, BarClient.class));

        assertThat(fooClient.isClosed()).isFalse();
        assertThat(barClient.isClosed()).isFalse();

        injector.getInstance(LifeCycleManager.class).stop();

        assertThat(fooClient.isClosed()).isTrue();
        assertThat(barClient.isClosed()).isTrue();
    }

    private static void assertFilterCount(HttpClient httpClient, int filterCount)
    {
        assertThat(httpClient).isNotNull();
        assertInstanceOf(httpClient, JettyHttpClient.class);
        assertThat(((JettyHttpClient) httpClient).getRequestFilters().size()).isEqualTo(filterCount);
    }

    private static void assertStatusListenerCount(HttpClient httpClient, int statusListenerCount)
    {
        assertThat(httpClient).isInstanceOfSatisfying(JettyHttpClient.class, jettyClient ->
                assertThat(jettyClient.getStatusListeners().size()).isEqualTo(statusListenerCount));
    }

    @Retention(RUNTIME)
    @Target(ElementType.PARAMETER)
    @BindingAnnotation
    public @interface FooClient
    {
    }

    @Retention(RUNTIME)
    @Target(ElementType.PARAMETER)
    @BindingAnnotation
    public @interface FooAlias1
    {
    }

    @Retention(RUNTIME)
    @Target(ElementType.PARAMETER)
    @BindingAnnotation
    public @interface FooAlias2
    {
    }

    @Retention(RUNTIME)
    @Target(ElementType.PARAMETER)
    @BindingAnnotation
    public @interface FooAlias3
    {
    }

    @Retention(RUNTIME)
    @Target(ElementType.PARAMETER)
    @BindingAnnotation
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
