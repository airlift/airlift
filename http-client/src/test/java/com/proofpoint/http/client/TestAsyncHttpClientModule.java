package com.proofpoint.http.client;

import com.google.common.collect.ImmutableMap;
import com.google.inject.BindingAnnotation;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.inject.Qualifier;
import java.lang.annotation.Retention;
import java.util.Set;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

public class TestAsyncHttpClientModule
{
    @Test
    public void test()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.<String, String>of())),
                new AsyncHttpClientModule("foo", HttpTest.class, HttpAliasTest.class));
        AsyncHttpClient client = injector.getInstance(Key.get(AsyncHttpClient.class, HttpTest.class));
        Assert.assertNotNull(client);
        AsyncHttpClient aliasedClient = injector.getInstance(Key.get(AsyncHttpClient.class, HttpAliasTest.class));
        Assert.assertNotNull(aliasedClient);
        Assert.assertSame(client, aliasedClient);
        Set<HttpRequestFilter> filters = injector.getInstance(AsyncHttpClientModule.filterKey(HttpTest.class));
        Assert.assertNotNull(filters);
        Assert.assertTrue(filters.isEmpty());
    }

    @Retention(RUNTIME)
    @Qualifier
    @BindingAnnotation
    public static @interface HttpTest {
    }

    @Retention(RUNTIME)
    @Qualifier
    @BindingAnnotation
    public static @interface HttpAliasTest {
    }
}
