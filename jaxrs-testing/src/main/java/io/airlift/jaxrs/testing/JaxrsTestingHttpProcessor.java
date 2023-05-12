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
import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
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
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.MoreCollectors.onlyElement;

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
        MultivaluedMap<String, Object> requestHeaders = new MultivaluedHashMap<>();
        for (Map.Entry<String, String> entry : request.getHeaders().entries()) {
            requestHeaders.add(entry.getKey(), entry.getValue());
        }
        Invocation.Builder invocationBuilder = client.target(request.getUri()).request().headers(requestHeaders);
        Invocation invocation;
        if (request.getBodyGenerator() == null) {
            invocation = invocationBuilder.build(request.getMethod());
        }
        else {
            byte[] bytes = getRequestBody(request);
            Entity<byte[]> entity = Entity.entity(bytes, (String) requestHeaders.get("Content-Type").stream().collect(onlyElement()));
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

        // process response from jax-rs resource
        ImmutableListMultimap.Builder<String, String> responseHeaders = ImmutableListMultimap.builder();
        for (Map.Entry<String, List<String>> headerEntry : result.getStringHeaders().entrySet()) {
            for (String value : headerEntry.getValue()) {
                responseHeaders.put(headerEntry.getKey(), value);
            }
        }

        if (trace) {
            log.warn("%-8s %s -> OK", request.getMethod(), request.getUri());
        }
        return new TestingResponse(HttpStatus.fromStatusCode(result.getStatus()), responseHeaders.build(), result.readEntity(byte[].class));
    }

    @SuppressWarnings("deprecation")
    private static byte[] getRequestBody(Request request)
            throws Exception
    {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            request.getBodyGenerator().write(out);
            return out.toByteArray();
        }
    }
}
