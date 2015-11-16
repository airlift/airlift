package com.proofpoint.jaxrs;

import com.google.common.base.Supplier;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.http.client.StringResponseHandler.StringResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.ApplicationNameModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import com.proofpoint.testing.Closeables;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.testing.TestingMBeanModule;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static com.proofpoint.testing.Assertions.assertContains;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestJaxrsModule
{
    private static final String INJECTED_MESSSAGE = "Hello, World!";
    private static final String SECOND_INJECTED_MESSSAGE = "Goodbye, World!";

    private final JettyHttpClient client = new JettyHttpClient();

    private TestingHttpServer server;

    @AfterMethod(alwaysRun = true)
    public void teardown()
            throws Exception
    {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @AfterClass(alwaysRun = true)
    public void teardownClass()
    {
        Closeables.closeQuietly(client);
    }

    @Test
    public void testWadlDisabled()
            throws Exception
    {
        createServer(binder -> jaxrsBinder(binder).bind(TestResource.class));

        Request request = Request.builder()
                            .setUri(server.getBaseUrl().resolve("/application.wadl"))
                            .setMethod("GET")
                            .build();
        StatusResponse response = client.execute(request, createStatusResponseHandler());
        assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode(), "Status code");
    }

    @Test
    public void testOptionsDisabled()
            throws Exception
    {
        createServer(binder -> jaxrsBinder(binder).bind(TestResource.class));

        Request request = Request.builder()
                            .setUri(server.getBaseUrl().resolve("/"))
                            .setMethod("OPTIONS")
                            .build();
        StatusResponse response = client.execute(request, createStatusResponseHandler());
        assertEquals(response.getStatusCode(), Status.METHOD_NOT_ALLOWED.getStatusCode(), "Status code");
        assertNull(response.getHeader("Host")); // Pentest "finding"
    }

    @Test
    public void testInjectableProvider()
            throws Exception
    {
        createServer(binder -> {
            jaxrsBinder(binder).bindInstance(new InjectedResource());
            jaxrsBinder(binder).bindInjectionProvider(InjectedContextObject.class).to(InjectedContextObjectSupplier.class);
        });

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
        createServer(binder -> {
            jaxrsBinder(binder).bindInstance(new InjectedResource2());
            jaxrsBinder(binder).bindInjectionProvider(InjectedContextObject.class).to(InjectedContextObjectSupplier.class);
            jaxrsBinder(binder).bindInjectionProvider(SecondInjectedContextObject.class).to(SecondInjectedContextObjectSupplier.class);
        });

        Request request = Request.builder()
                            .setUri(server.getBaseUrl().resolve("/injectedresource2"))
                            .setMethod("GET")
                            .build();
        StringResponse response = client.execute(request, createStringResponseHandler());
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode(), "Status code");
        assertContains(response.getBody(), INJECTED_MESSSAGE);
        assertContains(response.getBody(), SECOND_INJECTED_MESSSAGE);
    }

    private void createServer(Module module)
            throws Exception
    {
        server = Guice.createInjector(
                new ApplicationNameModule("test-application"),
                new TestingNodeModule(),
                explicitJaxrsModule(),
                new JsonModule(),
                new ReportingModule(),
                new TestingMBeanModule(),
                new TestingHttpServerModule(),
                module)
                .getInstance(TestingHttpServer.class);
        server.start();
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
