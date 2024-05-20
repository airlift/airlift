package io.airlift.jaxrs;

import com.google.inject.BindingAnnotation;
import com.google.inject.Injector;
import com.google.inject.Key;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.json.JsonModule;
import io.airlift.node.testing.TestingNodeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import org.testng.annotations.Test;

import java.lang.annotation.Retention;
import java.net.URI;
import java.util.function.Supplier;

import static io.airlift.http.client.HttpClientBinder.httpClientBinder;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.Objects.requireNonNull;
import static org.testng.Assert.assertEquals;

public class TestRequestScopedBinding
{
    @Test
    public void testRequestScopedInjection()
    {
        Injector injector = startServer();
        try {
            HttpClient httpClient = injector.getInstance(Key.get(HttpClient.class, ForTest.class));
            URI serverUri = injector.getInstance(HttpServerInfo.class).getHttpUri();

            assertEquals(executeRequest(httpClient, serverUri, "Bob"), "Bob@127.0.0.1");
            assertEquals(executeRequest(httpClient, serverUri, "Alice"), "Alice@127.0.0.1");
        }
        finally {
            injector.getInstance(LifeCycleManager.class).stop();
        }
    }

    private String executeRequest(HttpClient httpClient, URI baseUri, String name)
    {
        Request request = prepareGet()
                .setUri(baseUri.resolve("/hello"))
                .setHeader("X-Name", name)
                .build();
        return httpClient.execute(request, createStringResponseHandler()).getBody();
    }

    @Path("/hello")
    public static class MyResource
    {
        @GET
        public String returnRequestScopedInfo(@Context RemoteAddr remoteAddr, @Context HeaderValue headerValue)
        {
            return headerValue.getValue() + "@" + remoteAddr.value();
        }
    }

    @Retention(RUNTIME)
    @BindingAnnotation
    public @interface ForTest {}

    public record RemoteAddr(String value) {}

    // this is not a record in order to test that normal classes work
    public static class HeaderValue
    {
        private final String value;

        public HeaderValue(String value)
        {
            this.value = requireNonNull(value, "value is null");
        }

        public String getValue()
        {
            return value;
        }
    }

    private Injector startServer()
    {
        return new Bootstrap(
                binder -> {
                    jaxrsBinder(binder).bind(MyResource.class);
                    httpClientBinder(binder).bindHttpClient("test", ForTest.class);
                    jaxrsBinder(binder).bindRequestScoped(RemoteAddr.class, RemoteAddrFactory.class);
                    jaxrsBinder(binder).bindRequestScoped(HeaderValue.class, HeaderValueFactory.class);
                },
                new TestingNodeModule(),
                new TestingHttpServerModule(),
                new JaxrsModule(),
                new JsonModule())
                .quiet()
                .initialize();
    }

    private record RemoteAddrFactory(HttpServletRequest request)
            implements Supplier<RemoteAddr>
    {
        private RemoteAddrFactory(@Context HttpServletRequest request)
        {
            this.request = requireNonNull(request, "request is null");
        }

        @Override
        public RemoteAddr get()
        {
            return new RemoteAddr(request.getRemoteAddr());
        }
    }

    private static class HeaderValueFactory
            implements Supplier<HeaderValue>
    {
        private final HttpHeaders headers;

        private HeaderValueFactory(@Context HttpHeaders headers)
        {
            this.headers = requireNonNull(headers, "headers is null");
        }

        @Override
        public HeaderValue get()
        {
            return new HeaderValue(headers.getHeaderString("X-Name"));
        }
    }
}
