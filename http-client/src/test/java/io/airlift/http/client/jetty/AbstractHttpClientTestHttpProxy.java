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
package io.airlift.http.client.jetty;

import io.airlift.http.client.AbstractHttpClientTest;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.StreamingResponse;
import io.airlift.http.client.TestingHttpProxy;

import java.io.IOException;
import java.util.Optional;

import static io.airlift.http.client.HttpStatus.BAD_GATEWAY;
import static io.airlift.http.client.HttpStatus.BAD_REQUEST;
import static java.util.Objects.requireNonNull;

public abstract class AbstractHttpClientTestHttpProxy
        extends AbstractHttpClientTest
{
    protected AbstractHttpClientTestHttpProxy() {}

    protected AbstractHttpClientTestHttpProxy(String keystore)
    {
        super(keystore);
    }

    @Override
    public HttpClientConfig createClientConfig()
    {
        return new HttpClientConfig();
    }

    @Override
    public Optional<StreamingResponse> executeRequest(CloseableTestHttpServer server, Request request)
            throws Exception
    {
        HttpClientConfig config = createClientConfig();
        TestingHttpProxy testingHttpProxy = new TestingHttpProxy(keystore);
        JettyHttpClient client = server.createClient(config.setHttpProxy(testingHttpProxy.getHostAndPort()));

        TestingStreamingResponse streamingResponse = new TestingStreamingResponse(() -> client.executeStreaming(request), testingHttpProxy, client);
        if ((streamingResponse.getStatusCode() == BAD_GATEWAY.code()) || (streamingResponse.getStatusCode() == BAD_REQUEST.code())) {
            throw new IOException();
        }
        return Optional.of(streamingResponse);
    }

    @Override
    public <T, E extends Exception> T executeRequest(CloseableTestHttpServer server, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        return executeRequest(server, createClientConfig(), request, responseHandler);
    }

    @Override
    public <T, E extends Exception> T executeRequest(CloseableTestHttpServer server, HttpClientConfig config, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        try (TestingHttpProxy testingHttpProxy = createTestingHttpProxy(); JettyHttpClient client = server.createClient(config.setHttpProxy(testingHttpProxy.getHostAndPort()))) {
            return client.execute(request, new ProxyResponseHandler<>(responseHandler));
        }
    }

    protected TestingHttpProxy createTestingHttpProxy()
            throws Exception
    {
        return new TestingHttpProxy(keystore);
    }

    public static class ProxyResponseHandler<T, E extends Exception>
            implements ResponseHandler<T, E>
    {
        private final ResponseHandler<T, E> delegate;

        public ProxyResponseHandler(ResponseHandler<T, E> delegate)
        {
            this.delegate = requireNonNull(delegate, "delegate is null");
        }

        @Override
        public T handleException(Request request, Exception exception)
                throws E
        {
            return delegate.handleException(request, exception);
        }

        @Override
        public T handle(Request request, Response response)
                throws E
        {
            if ((response.getStatusCode() == BAD_GATEWAY.code()) ||
                    (response.getStatusCode() == BAD_REQUEST.code())) {
                return delegate.handleException(request, new IOException());
            }

            return delegate.handle(request, response);
        }
    }
}
