package com.proofpoint.jaxrs;

import com.beust.jcommander.internal.Sets;
import com.google.common.base.Charsets;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.http.client.ApacheHttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.StaticBodyGenerator;
import com.proofpoint.http.client.StringResponseHandler.StringResponse;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.sun.jersey.spi.container.ResourceFilterFactory;
import org.testng.annotations.Test;

import java.util.Set;

import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestJaxrsBinder
{
    @Test
    public void testInstantiation()
    {
        final ResourceFilterFactory filterFactory = mock(ResourceFilterFactory.class);

        Guice.createInjector(new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                jaxrsBinder(binder).bindResourceFilterFactory(filterFactory.getClass());
                binder.bind(filterFactory.getClass()).in(Scopes.SINGLETON);
            }
        });
    }

    @Test
    public void testMultipleFilterFactories()
    {
        final ResourceFilterFactory filterFactory1 = mock(ResourceFilterFactory.class);
        final ResourceFilterFactory filterFactory2 = mock(ResourceFilterFactory.class);

        Guice.createInjector(new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                JaxrsBinder jaxrsBinder = jaxrsBinder(binder);
                jaxrsBinder.bindResourceFilterFactory(filterFactory1.getClass());
                jaxrsBinder.bindResourceFilterFactory(filterFactory2.getClass());
                binder.bind (filterFactory1.getClass()).in(Scopes.SINGLETON);
                binder.bind (filterFactory2.getClass()).in(Scopes.SINGLETON);
            }
        });
    }

    @Test
    public void testServerInstantiation() throws Exception
    {
        TestResource resource = new TestResource();
        final ResourceFilterFactory filterFactory = new TestFilterFactory();
        Set<ResourceFilterFactory> filterFactories = Sets.newHashSet();
        filterFactories.add(filterFactory);

        TestingHttpServer server = createServerWithFilter(resource, filterFactories);
        ApacheHttpClient client = new ApacheHttpClient();
        server.start();

        Request request = prepareGet()
                .setUri(server.getBaseUrl())
                .setBodyGenerator(StaticBodyGenerator.createStaticBodyGenerator("", Charsets.US_ASCII))
                .build();

        StringResponse response = client.execute(request, createStringResponseHandler());
        assertEquals(response.getStatusCode(), 503);
        assertTrue(resource.getCalled());
    }

    @Test
    public void testMultipleFiltersInServer() throws Exception
    {
        TestResource resource = new TestResource();
        final ResourceFilterFactory statusCodeChangeFilterFactory = new TestFilterFactory();
        final ResourceFilterFactory headerChangeFilterFactory = new SecondTestFilterFactory();
        Set<ResourceFilterFactory> filterFactories = Sets.newHashSet();
        filterFactories.add(statusCodeChangeFilterFactory);
        filterFactories.add(headerChangeFilterFactory);

        TestingHttpServer server = createServerWithFilter(resource, filterFactories);
        ApacheHttpClient client = new ApacheHttpClient();
        server.start();

        Request request = prepareGet()
                .setUri(server.getBaseUrl())
                .setBodyGenerator(StaticBodyGenerator.createStaticBodyGenerator("", Charsets.US_ASCII))
                .build();

        StringResponse response = client.execute(request, createStringResponseHandler());
        assertEquals(response.getStatusCode(), 503);
        assertEquals(response.getHeader("NewHeader"), "NewValue");
        assertTrue(resource.getCalled());
    }

    private static TestingHttpServer createServerWithFilter (final TestResource resource, final Set<ResourceFilterFactory> filterFactories)
    {
        Injector injector = Guice.createInjector(
                new TestingNodeModule(),
                new JaxrsModule(),
                new JsonModule(),
                new TestingHttpServerModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(TestResource.class).toInstance(resource);
                        for (ResourceFilterFactory filterFactory : filterFactories) {
                            jaxrsBinder(binder)
                                .bindResourceFilterFactory(filterFactory.getClass());
                        }
                    }
                });
        return injector.getInstance(TestingHttpServer.class);
    }
}
