package io.airlift.jaxrs;

import com.google.common.collect.ImmutableList;
import com.google.inject.BindingAnnotation;
import com.google.inject.Injector;
import com.google.inject.Key;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.http.client.FullJsonResponseHandler.JsonResponse;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.Request;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.json.JsonModule;
import io.airlift.node.testing.TestingNodeModule;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.testng.annotations.Test;

import java.lang.annotation.Retention;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static io.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static io.airlift.http.client.HttpClientBinder.httpClientBinder;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.server.HttpServerBinder.httpServerBinder;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static io.airlift.json.JsonCodec.listJsonCodec;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.testng.Assert.assertEquals;

public class TestLegacyUriMode
{
    @Path("/legacy")
    public static class MyResource
    {
        @GET
        @Path("test1/{a:.*}/{b:.*}/{c:.*}")
        public List<String> test1(@PathParam("a") String a, @PathParam("b") String b, @PathParam("c") String c)
        {
            return ImmutableList.of("test1", a, b, c);
        }

        @GET
        @Path("test2/{a}/{b}/{c}")
        public List<String> test(@PathParam("a") String a, @PathParam("b") String b, @PathParam("c") String c)
        {
            return ImmutableList.of("test2", a, b, c);
        }
    }

    @Retention(RUNTIME)
    @BindingAnnotation
    public @interface ForTest {}

    @Test
    public void testLegacyUriModeDisabled()
    {
        doTest(false,
                new Tester("/legacy/test1/one%2ftwo/%2f/three", response -> assertEquals(response.getStatusCode(), HttpStatus.BAD_REQUEST.code())),
                new Tester("/legacy/test2/one%2ftwo/%2f/three", response -> assertEquals(response.getStatusCode(), HttpStatus.BAD_REQUEST.code())));
    }

    @Test
    public void testLegacyUriMode()
    {
        doTest(true,
                new Tester("/legacy/test1/one%2ftwo/%2f/three", response -> assertEquals(response.getValue(), ImmutableList.of("test1", "one/two", "/", "three"))),
                new Tester("/legacy/test2/one%2ftwo/%2f/three", response -> assertEquals(response.getValue(), ImmutableList.of("test2", "one/two", "/", "three"))));
    }

    private record Tester(String path, Consumer<JsonResponse<List<String>>> responseConsumer) {}

    private void doTest(boolean legacyUriComplianceEnabled, Tester... testers)
    {
        Injector injector = startServer(legacyUriComplianceEnabled);
        try {
            HttpClient httpClient = injector.getInstance(Key.get(HttpClient.class, ForTest.class));
            URI baseuri = injector.getInstance(HttpServerInfo.class).getHttpUri();

            Stream.of(testers).forEach(tester -> {
                Request request = prepareGet().setUri(baseuri.resolve(tester.path)).build();
                tester.responseConsumer.accept(httpClient.execute(request, createFullJsonResponseHandler(listJsonCodec(String.class))));
            });
        }
        finally {
            injector.getInstance(LifeCycleManager.class).stop();
        }
    }

    @SuppressWarnings("removal")
    private Injector startServer(boolean legacyUriComplianceEnabled)
    {
        return new Bootstrap(
                binder -> {
                    jaxrsBinder(binder).bind(MyResource.class);
                    httpClientBinder(binder).bindHttpClient("test", ForTest.class);
                    if (legacyUriComplianceEnabled) {
                        httpServerBinder(binder).enableLegacyUriCompliance();
                    }
                },
                new TestingNodeModule(),
                new TestingHttpServerModule(),
                new JaxrsModule(),
                new JsonModule())
                .quiet()
                .initialize();
    }
}
