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

import static java.lang.annotation.RetentionPolicy.RUNTIME;

public class TestHttpClientModule
{
    @Test
    public void test()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.<String, String>of())),
                new HttpClientModule("foo", HttpTest.class, HttpAliasTest.class));
        HttpClient client = injector.getInstance(Key.get(HttpClient.class, HttpTest.class));
        Assert.assertNotNull(client);
        HttpClient aliasedClient = injector.getInstance(Key.get(HttpClient.class, HttpAliasTest.class));
        Assert.assertNotNull(client);
        Assert.assertSame(client, aliasedClient);
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
