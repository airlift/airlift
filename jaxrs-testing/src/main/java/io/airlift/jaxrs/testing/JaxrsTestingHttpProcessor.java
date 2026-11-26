/*
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

package io.airlift.jaxrs.testing;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import io.airlift.http.client.AsyncBodyGenerator;
import io.airlift.http.client.ByteBufferBodyGenerator;
import io.airlift.http.client.FileBodyGenerator;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
import io.airlift.http.client.StaticBodyGenerator;
import io.airlift.http.client.StreamingBodyGenerator;
import io.airlift.http.client.testing.TestingHttpClient;
import io.airlift.http.client.testing.TestingResponse;
import io.airlift.log.Logger;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.glassfish.jersey.test.DeploymentContext;
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Throwables.throwIfInstanceOf;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.common.util.concurrent.Futures.immediateVoidFuture;
import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;

public class JaxrsTestingHttpProcessor
        implements TestingHttpClient.Processor
{
    private static final Logger log = Logger.get(JaxrsTestingHttpProcessor.class);

    private final Client client;

    private boolean trace;

    public JaxrsTestingHttpProcessor(URI baseUri, Object... jaxRsSingletons)
    {
        Set<Object> jaxRsSingletonsSet = ImmutableSet.copyOf(jaxRsSingletons);
        Application application = new Application()
        {
            @Override
            @SuppressWarnings("deprecation")
            public Set<Object> getSingletons()
            {
                return jaxRsSingletonsSet;
            }
        };
        TestContainer testContainer = new InMemoryTestContainerFactory()
                .create(baseUri, DeploymentContext.newInstance(application));
        ClientConfig clientConfig = testContainer.getClientConfig();
        this.client = JerseyClientBuilder.createClient(clientConfig);
    }

    public JaxrsTestingHttpProcessor setTrace(boolean enabled)
    {
        this.trace = enabled;
        return this;
    }

    @Override
    public Response handle(Request request)
            throws Exception
    {
        // prepare request to jax-rs resource
        MultivaluedMap<HeaderName, Object> requestHeaders = new MultivaluedHashMap<>();
        for (Map.Entry<HeaderName, String> entry : request.getHeaders().entries()) {
            requestHeaders.add(entry.getKey(), entry.getValue());
        }
        Invocation.Builder invocationBuilder = client.target(request.getUri()).request();
        requestHeaders
                .forEach((headerName, values) -> values
                        .forEach(value -> invocationBuilder.header(headerName.toString(), value)));

        ClosedBody asyncBody = null;
        Invocation invocation;
        if (request.getBodyGenerator() == null) {
            invocation = invocationBuilder.build(request.getMethod());
        }
        else {
            byte[] bytes = switch (request.getBodyGenerator()) {
                case StaticBodyGenerator generator -> generator.getBody();
                case ByteBufferBodyGenerator generator -> getBytes(generator.getByteBuffers());
                case FileBodyGenerator generator -> Files.readAllBytes(generator.getPath());
                case StreamingBodyGenerator generator -> {
                    try (InputStream stream = generator.source()) {
                        yield stream.readAllBytes();
                    }
                }
                case AsyncBodyGenerator generator -> {
                    ClosedBody body = readClosedBody(generator);
                    asyncBody = body;
                    yield body.bytes();
                }
            };
            Entity<byte[]> entity = Entity.entity(bytes, contentType(request, requestHeaders));
            invocation = invocationBuilder.build(request.getMethod(), entity);
        }

        // issue request, and handle exceptions
        jakarta.ws.rs.core.Response result;
        try {
            result = invocation.invoke(jakarta.ws.rs.core.Response.class);
        }
        catch (ProcessingException exception) {
            if (trace) {
                log.warn(exception.getCause(), "%-8s %s -> Exception", request.getMethod(), request.getUri());
            }
            // Peel out the exception we threw in jax-rs resource implementation
            // to facilitate testing exceptional paths
            if (exception.getCause() instanceof Exception) {
                throw (Exception) exception.getCause();
            }
            throw exception;
        }
        catch (Throwable throwable) {
            if (trace) {
                log.warn(throwable, "%-8s %s -> Fail", request.getMethod(), request.getUri());
            }
            throw throwable;
        }
        // a body that fails while the resource runs fails the request, although the resource got all of it
        if (asyncBody != null) {
            asyncBody.throwFailure();
        }

        // process response from jax-rs resource
        ImmutableListMultimap.Builder<HeaderName, String> responseHeaders = ImmutableListMultimap.builder();
        for (Map.Entry<String, List<String>> headerEntry : result.getStringHeaders().entrySet()) {
            for (String value : headerEntry.getValue()) {
                responseHeaders.put(HeaderName.of(headerEntry.getKey()), value);
            }
        }

        if (trace) {
            log.warn("%-8s %s -> OK", request.getMethod(), request.getUri());
        }
        return new TestingResponse(HttpStatus.fromStatusCode(result.getStatus()), responseHeaders.build(), result.readEntity(byte[].class));
    }

    /**
     * The request header wins. Without it, the content type is the one the body was created with,
     * which is what the HTTP client sends for such a body.
     */
    private static String contentType(Request request, MultivaluedMap<HeaderName, Object> requestHeaders)
    {
        List<Object> contentTypes = requestHeaders.get(CONTENT_TYPE);
        if (contentTypes != null) {
            return (String) contentTypes.stream().collect(onlyElement());
        }
        return switch (request.getBodyGenerator()) {
            case AsyncBodyGenerator generator -> generator.contentType();
            case StreamingBodyGenerator generator -> generator.contentType();
            case FileBodyGenerator generator -> generator.getContentType().toString();
            case StaticBodyGenerator _, ByteBufferBodyGenerator _ -> throw new IllegalArgumentException("Request with a body has no Content-Type header");
        };
    }

    /**
     * The resource is invoked with the complete body, so the body must be closed already. Waiting for it
     * instead would block forever when the same thread is the one that is going to close it.
     */
    private static ClosedBody readClosedBody(AsyncBodyGenerator generator)
            throws Exception
    {
        ClosedBody body = new ClosedBody();
        // content written so far and the end of the body are delivered before this returns
        generator.attach(body);
        body.throwFailure();
        if (!body.isClosed()) {
            // the request ends here, so later writes must not be accepted by the abandoned sink
            IllegalStateException rejection = new IllegalStateException("AsyncBodyGenerator must be closed before the request is executed");
            generator.fail(rejection);
            throw rejection;
        }
        return body;
    }

    private static class ClosedBody
            implements AsyncBodyGenerator.Sink
    {
        private final ByteArrayOutputStream content = new ByteArrayOutputStream();
        private volatile boolean closed;
        private volatile Throwable failure;

        @Override
        public ListenableFuture<Void> write(ByteBuffer content)
        {
            // the caller's buffer is left as it is
            ByteBuffer copy = content.duplicate();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            this.content.writeBytes(bytes);
            return immediateVoidFuture();
        }

        @Override
        public void close()
        {
            closed = true;
        }

        @Override
        public void fail(Throwable failure)
        {
            this.failure = failure;
        }

        public boolean isClosed()
        {
            return closed;
        }

        public byte[] bytes()
        {
            return content.toByteArray();
        }

        public void throwFailure()
                throws Exception
        {
            Throwable failure = this.failure;
            if (failure != null) {
                throwIfInstanceOf(failure, Exception.class);
                throw new RuntimeException(failure);
            }
        }
    }

    private static byte[] getBytes(ByteBuffer[] byteBuffers)
            throws Exception
    {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (ByteBuffer byteBuffer : byteBuffers) {
                int savedPosition = byteBuffer.position();
                while (byteBuffer.hasRemaining()) {
                    out.write(byteBuffer.get());
                }
                byteBuffer.position(savedPosition);
            }
            return out.toByteArray();
        }
    }
}
