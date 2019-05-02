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
import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
import io.airlift.http.client.testing.TestingHttpClient;
import io.airlift.http.client.testing.TestingResponse;
import io.airlift.log.Logger;
import org.jboss.resteasy.mock.MockDispatcherFactory;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.plugins.server.resourcefactory.SingletonResource;
import org.jboss.resteasy.spi.Dispatcher;
import org.jboss.resteasy.spi.UnhandledException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.List;
import java.util.Map.Entry;

public class JaxrsTestingHttpProcessor
        implements TestingHttpClient.Processor
{
    private static final Logger log = Logger.get(JaxrsTestingHttpProcessor.class);

    private final Dispatcher dispatcher;

    private boolean trace;

    public JaxrsTestingHttpProcessor(URI baseUri, Object... jaxRsSingletons)
    {
        dispatcher = MockDispatcherFactory.createDispatcher();
        for (Object singleton : jaxRsSingletons) {
            dispatcher.getRegistry().addResourceFactory(new SingletonResource(singleton));
        }
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
        // create request
        MockHttpRequest httpRequest = MockHttpRequest.create(request.getMethod(), request.getUri().toString());
        for (Entry<String, String> entry : request.getHeaders().entries()) {
            httpRequest.header(entry.getKey(), entry.getValue());
        }
        if (request.getBodyGenerator() != null) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            request.getBodyGenerator().write(out);
            byte[] bytes = out.toByteArray();
            httpRequest.setInputStream(new ByteArrayInputStream(bytes));
        }

        MockHttpResponse response = new MockHttpResponse();

        // issue request, and handle exceptions
        try {
            dispatcher.invoke(httpRequest, response);
        }
        catch (UnhandledException exception) {
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
        for (Entry<String, List<Object>> headerEntry : response.getOutputHeaders().entrySet()) {
            for (Object value : headerEntry.getValue()) {
                responseHeaders.put(headerEntry.getKey(), String.valueOf(value));
            }
        }

        if (trace) {
            log.warn("%-8s %s -> OK", request.getMethod(), request.getUri());
        }
        return new TestingResponse(HttpStatus.fromStatusCode(response.getStatus()), responseHeaders.build(), response.getOutput());
    }
}
