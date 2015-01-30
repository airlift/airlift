package com.proofpoint.jaxrs;

import com.google.common.base.Supplier;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.StringResponseHandler.StringResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.ApplicationNameModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import com.proofpoint.testing.Closeables;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.inject.Inject;
import javax.management.MBeanServer;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static com.proofpoint.testing.Assertions.assertContains;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;

public class TestInjectedContext
{
    TestingHttpServer server;
    JettyHttpClient client;
    private static final String INJECTED_MESSSAGE = "Hello, World!";
    private static final String SECOND_INJECTED_MESSSAGE = "Goodbye, World!";

    @BeforeMethod
    public void setup()
    {
        client = new JettyHttpClient();
    }

    @AfterMethod
    public void teardown()
            throws Exception
    {
        try {
            if (server != null) {
                server.stop();
            }
        }
        catch (Throwable ignored) {
        }
        Closeables.closeQuietly(client);
    }

    @Test
    public void testInjectableProvider()
            throws Exception
    {
        server = createServer(new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                jaxrsBinder(binder).bindInstance(new InjectedResource());
                jaxrsBinder(binder).bindInjectionProvider(InjectedContextObject.class).to(InjectedContextObjectSupplier.class);
            }
        });
        server.start();

        Request request = Request.builder()
                            .setUri(server.getBaseUrl().resolve("/injectedresource"))
                            .setMethod("GET")
                            .build();
        StringResponse response = client.execute(request, createStringResponseHandler());
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode(), "Status code");
        assertContains(response.getBody(), INJECTED_MESSSAGE);
    }

    @Test
    public void testTwoInjectableProviders()
            throws Exception
    {
        server = createServer(new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                jaxrsBinder(binder).bindInstance(new InjectedResource2());
                jaxrsBinder(binder).bindInjectionProvider(InjectedContextObject.class).to(InjectedContextObjectSupplier.class);
                jaxrsBinder(binder).bindInjectionProvider(SecondInjectedContextObject.class).to(SecondInjectedContextObjectSupplier.class);
            }
        });
        server.start();

        Request request = Request.builder()
                            .setUri(server.getBaseUrl().resolve("/injectedresource2"))
                            .setMethod("GET")
                            .build();
        StringResponse response = client.execute(request, createStringResponseHandler());
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode(), "Status code");
        assertContains(response.getBody(), INJECTED_MESSSAGE);
        assertContains(response.getBody(), SECOND_INJECTED_MESSSAGE);
    }

    private static TestingHttpServer createServer(Module module)
    {
        return Guice.createInjector(
                new ApplicationNameModule("test-application"),
                new TestingNodeModule(),
                explicitJaxrsModule(),
                new JsonModule(),
                new ReportingModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(MBeanServer.class).toInstance(mock(MBeanServer.class));
                    }
                },
                new TestingHttpServerModule(),
                module).getInstance(TestingHttpServer.class);
    }

    @Path("/injectedresource")
    public static class InjectedResource
    {
        @GET
        public Response getContextInjectable(@Context InjectedContextObject injectedContextObject)
        {
            return Response.ok(injectedContextObject.getMessage()).build();
        }
    }

    public static class InjectedContextObjectSupplier
        implements Supplier<InjectedContextObject>
    {
        @Override
        public InjectedContextObject get()
        {
            return new InjectedContextObject();
        }
    }

    public static class InjectedContextObject
    {
        @Inject
        private HttpServletRequest request;

        public String getMessage()
        {
            return String.format("%s %s", request.getServletPath(), INJECTED_MESSSAGE);
        }
    }

    @Path("/injectedresource2")
    public static class InjectedResource2
    {
        @GET
        public Response getContextInjectable(@Context InjectedContextObject injectedContextObject, @Context SecondInjectedContextObject secondInjectedContextObject)
        {
            return Response.ok(injectedContextObject.getMessage() + ":" + secondInjectedContextObject.getMessage()).build();
        }
    }

    public static class SecondInjectedContextObjectSupplier
        implements Supplier<SecondInjectedContextObject>
    {
        @Override
        public SecondInjectedContextObject get()
        {
            return new SecondInjectedContextObject();
        }
    }

    public static class SecondInjectedContextObject
    {
        @Inject
        private HttpServletRequest request;

        public String getMessage()
        {
            return String.format("%s %s", request.getServletPath(), SECOND_INJECTED_MESSSAGE);
        }
    }
}
