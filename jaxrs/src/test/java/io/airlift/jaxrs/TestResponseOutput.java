package io.airlift.jaxrs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.inject.Injector;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.json.JsonModule;
import io.airlift.node.testing.TestingNodeModule;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NameBinding;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.net.HttpHeaders.CONTENT_LENGTH;
import static com.google.common.net.HttpHeaders.TRANSFER_ENCODING;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@TestInstance(PER_CLASS)
@Execution(SAME_THREAD)
public class TestResponseOutput
{
    private final BlockingQueue<Output> outputs = new LinkedBlockingQueue<>();
    private LifeCycleManager lifeCycleManager;
    private JettyHttpClient client;
    private JettyHttpClient http2Client;
    private URI baseUri;

    @BeforeAll
    public void setup()
    {
        Injector injector = new Bootstrap(
                binder -> {
                    jaxrsBinder(binder).bind(TestingResource.class);
                    jaxrsBinder(binder).bind(WrappingInterceptor.class);
                    newSetBinder(binder, Filter.class).addBinding().toInstance(new RecordingFilter(outputs));
                },
                new TestingNodeModule(),
                new TestingHttpServerModule(getClass().getName()),
                new JaxrsModule(),
                new JsonModule())
                .quiet()
                .initialize();
        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        baseUri = injector.getInstance(TestingHttpServer.class).getBaseUrl();
        client = new JettyHttpClient(new HttpClientConfig());
        http2Client = new JettyHttpClient(new HttpClientConfig().setHttp2Enabled(true));
    }

    @AfterAll
    public void teardown()
    {
        client.close();
        http2Client.close();
        lifeCycleManager.stop();
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 8192, 8193, 20_000})
    public void testJsonEntityFitsOutputBuffer(int size)
            throws Exception
    {
        Result result = get("/json/" + size);

        assertThat(result.response().getStatusCode()).isEqualTo(200);
        assertThat(result.response().getBody()).isEqualTo(Entity.json(size));
        assertThat(result.output().flushes()).isZero();
        assertThat(result.response().getHeader(HeaderName.of(CONTENT_LENGTH))).contains(Integer.toString(size));
        assertThat(result.response().getHeader(HeaderName.of(TRANSFER_ENCODING))).isEmpty();
    }

    @Test
    public void testJsonEntityExceedsOutputBuffer()
            throws Exception
    {
        int size = 4_000_000;
        Result result = get("/json/" + size);

        assertThat(result.response().getStatusCode()).isEqualTo(200);
        assertThat(result.response().getBody()).isEqualTo(Entity.json(size));
        assertThat(result.output().flushes()).isZero();
        // the server had to start sending before the entity was complete
        assertThat(result.response().getHeader(HeaderName.of(CONTENT_LENGTH))).isEmpty();
        assertThat(result.response().getHeader(HeaderName.of(TRANSFER_ENCODING))).contains("chunked");
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 8193})
    public void testJsonEntityOverHttp2(int size)
            throws Exception
    {
        Result result = get(http2Client, "/json/" + size);

        assertThat(result.response().getStatusCode()).isEqualTo(200);
        assertThat(result.response().getBody()).isEqualTo(Entity.json(size));
        assertThat(result.output().flushes()).isZero();
        assertThat(result.response().getHeader(HeaderName.of(CONTENT_LENGTH))).contains(Integer.toString(size));
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 8193, 20_000, 4_000_000})
    public void testJsonEntityFailsMidSerialization(int size)
            throws Exception
    {
        Result result = get("/failing/" + size);

        assertThat(result.response().getStatusCode()).isEqualTo(200);
        assertThat(result.response().getBody()).isEqualTo(FailingEntity.jsonBeforeFailure(size));
        // the JSON provider does not flush, but Jersey still does when it closes a response that failed
        assertThat(result.output().flushes()).isEqualTo(1);
    }

    @Test
    public void testFlushKeptForStreamWrappedByAnotherInterceptor()
            throws Exception
    {
        Result result = get("/wrapped");

        assertThat(result.response().getStatusCode()).isEqualTo(200);
        assertThat(result.response().getBody()).isEqualTo(Entity.json(100));
        assertThat(result.output().flushes()).isEqualTo(1);
    }

    @Test
    public void testTextEntity()
            throws Exception
    {
        Result result = get("/text");

        assertThat(result.response().getStatusCode()).isEqualTo(200);
        assertThat(result.response().getBody()).isEqualTo("text");
        assertThat(result.output().flushes()).isZero();
        assertThat(result.response().getHeader(HeaderName.of(CONTENT_LENGTH))).contains("4");
    }

    @Test
    public void testFlushRequestedByResource()
            throws Exception
    {
        Result result = get("/streaming");

        assertThat(result.response().getStatusCode()).isEqualTo(200);
        assertThat(result.response().getBody()).isEqualTo("firstsecond");
        // only the flush the resource asked for
        assertThat(result.output().flushes()).isEqualTo(1);
        assertThat(result.response().getHeader(HeaderName.of(TRANSFER_ENCODING))).contains("chunked");
    }

    private Result get(String path)
            throws InterruptedException
    {
        return get(client, path);
    }

    private Result get(JettyHttpClient client, String path)
            throws InterruptedException
    {
        outputs.clear();
        StringResponse response = client.execute(prepareGet().setUri(baseUri.resolve(path)).build(), createStringResponseHandler());
        Output output = outputs.poll(10, SECONDS);
        assertThat(output).isNotNull();
        return new Result(response, output);
    }

    private record Result(StringResponse response, Output output) {}

    private record Output(int flushes) {}

    @Path("/")
    public static class TestingResource
    {
        @GET
        @Path("json/{size}")
        @Produces(MediaType.APPLICATION_JSON)
        public Entity json(@PathParam("size") int size)
        {
            return new Entity(size);
        }

        @GET
        @Path("wrapped")
        @Produces(MediaType.APPLICATION_JSON)
        @Wrapped
        public Entity wrapped()
        {
            return new Entity(100);
        }

        @GET
        @Path("failing/{size}")
        @Produces(MediaType.APPLICATION_JSON)
        public FailingEntity failing(@PathParam("size") int size)
        {
            return new FailingEntity(size);
        }

        @GET
        @Path("text")
        @Produces(MediaType.TEXT_PLAIN)
        public String text()
        {
            return "text";
        }

        @GET
        @Path("streaming")
        @Produces(MediaType.TEXT_PLAIN)
        public StreamingOutput streaming()
        {
            return output -> {
                output.write("first".getBytes(UTF_8));
                output.flush();
                output.write("second".getBytes(UTF_8));
            };
        }
    }

    @NameBinding
    @Retention(RUNTIME)
    public @interface Wrapped {}

    // wraps the entity stream in a stream that does not drain on close
    @Provider
    @Wrapped
    public static class WrappingInterceptor
            implements WriterInterceptor
    {
        @Override
        public void aroundWriteTo(WriterInterceptorContext context)
                throws IOException
        {
            OutputStream out = context.getOutputStream();
            context.setOutputStream(new OutputStream()
            {
                @Override
                public void write(int b)
                        throws IOException
                {
                    out.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len)
                        throws IOException
                {
                    out.write(b, off, len);
                }

                @Override
                public void flush()
                        throws IOException
                {
                    out.flush();
                }

                @Override
                public void close()
                        throws IOException
                {
                    out.close();
                }
            });
            context.proceed();
        }
    }

    // serializes to the requested number of bytes
    public static class Entity
    {
        private final int size;

        public Entity(int size)
        {
            this.size = size;
        }

        @JsonProperty
        public String getPadding()
        {
            return "x".repeat(size - json(0).length());
        }

        static String json(int size)
        {
            return "{\"padding\":\"" + "x".repeat(Math.max(size - 14, 0)) + "\"}";
        }
    }

    // fails once the requested number of bytes have been serialized
    @JsonPropertyOrder({"padding", "failure"})
    public static class FailingEntity
    {
        private final int size;

        public FailingEntity(int size)
        {
            this.size = size;
        }

        @JsonProperty
        public String getPadding()
        {
            return "x".repeat(size - jsonBeforeFailure(0).length());
        }

        @JsonProperty
        public String getFailure()
        {
            throw new RuntimeException("Intentional failure");
        }

        static String jsonBeforeFailure(int size)
        {
            return "{\"padding\":\"" + "x".repeat(Math.max(size - 13, 0)) + "\"";
        }
    }

    // counts what reaches the servlet output stream, which is Jetty's
    private static class RecordingFilter
            implements Filter
    {
        private final BlockingQueue<Output> outputs;

        private RecordingFilter(BlockingQueue<Output> outputs)
        {
            this.outputs = outputs;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException
        {
            AtomicInteger flushes = new AtomicInteger();
            try {
                chain.doFilter(request, new HttpServletResponseWrapper((HttpServletResponse) response)
                {
                    @Override
                    public ServletOutputStream getOutputStream()
                            throws IOException
                    {
                        return new RecordingOutputStream(super.getOutputStream(), flushes);
                    }
                });
            }
            finally {
                outputs.add(new Output(flushes.get()));
            }
        }
    }

    private static class RecordingOutputStream
            extends ServletOutputStream
    {
        private final ServletOutputStream delegate;
        private final AtomicInteger flushes;

        private RecordingOutputStream(ServletOutputStream delegate, AtomicInteger flushes)
        {
            this.delegate = delegate;
            this.flushes = flushes;
        }

        @Override
        public void write(int b)
                throws IOException
        {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len)
                throws IOException
        {
            delegate.write(b, off, len);
        }

        @Override
        public void flush()
                throws IOException
        {
            flushes.incrementAndGet();
            delegate.flush();
        }

        @Override
        public void close()
                throws IOException
        {
            delegate.close();
        }

        @Override
        public boolean isReady()
        {
            return delegate.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener)
        {
            delegate.setWriteListener(writeListener);
        }
    }
}
