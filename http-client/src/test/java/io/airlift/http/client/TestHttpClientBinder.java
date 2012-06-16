package io.airlift.http.client;

import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.configuration.ConfigurationModule;
import io.airlift.tracetoken.TraceTokenModule;
import org.testng.annotations.Test;

import javax.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Collections;

import static io.airlift.http.client.HttpClientBinder.HttpClientBindingBuilder;
import static io.airlift.http.client.HttpClientBinder.httpClientBinder;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

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
    public void testSeparateFiltersForClientAndAsyncClient()
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

                        httpClientBinder(binder).bindAsyncHttpClient("foo", FooClient.class)
                                .withFilter(AnotherHttpRequestFilter.class);
                    }
                },
                new ConfigurationModule(new ConfigurationFactory(Collections.<String, String>emptyMap())),
                new TraceTokenModule());

        assertFilterCount(injector.getInstance(Key.get(HttpClient.class, FooClient.class)), 3);
        assertFilterCount(injector.getInstance(Key.get(AsyncHttpClient.class, FooClient.class)), 1);
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
    public void testSeparateAliasesForClientAndAsyncClient()
    {
        Injector injector = Guice.createInjector(
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
                                .withAlias(FooAlias1.class)
                                .withAlias(FooAlias2.class);

                        httpClientBinder(binder).bindAsyncHttpClient("foo", FooClient.class)
                                .withAlias(FooAlias3.class);
                    }
                },
                new ConfigurationModule(new ConfigurationFactory(Collections.<String, String>emptyMap())));

        HttpClient client = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        assertSame(injector.getInstance(Key.get(HttpClient.class, FooAlias1.class)), client);
        assertSame(injector.getInstance(Key.get(HttpClient.class, FooAlias2.class)), client);
        assertNull(injector.getExistingBinding(Key.get(HttpClient.class, FooAlias3.class)));

        AsyncHttpClient asyncClient = injector.getInstance(Key.get(AsyncHttpClient.class, FooClient.class));
        assertSame(injector.getInstance(Key.get(AsyncHttpClient.class, FooAlias3.class)), asyncClient);
        assertNull(injector.getExistingBinding(Key.get(AsyncHttpClient.class, FooAlias1.class)));
        assertNull(injector.getExistingBinding(Key.get(AsyncHttpClient.class, FooAlias2.class)));
    }

    private static void assertFilterCount(HttpClient httpClient, int filterCount)
    {
        assertNotNull(httpClient);
        assertEquals(httpClient.getClass(), ApacheHttpClient.class);
        assertEquals(((ApacheHttpClient) httpClient).getRequestFilters().size(), filterCount);
    }

    private static void assertFilterCount(AsyncHttpClient asyncHttpClient, int filterCount)
    {
        assertNotNull(asyncHttpClient);
        assertEquals(asyncHttpClient.getRequestFilters().size(), filterCount);
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
}
