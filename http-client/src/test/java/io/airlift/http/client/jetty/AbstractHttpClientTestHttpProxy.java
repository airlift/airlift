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

import com.google.common.collect.ImmutableList;
import io.airlift.http.client.AbstractHttpClientTest;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.TestingHttpProxy;
import io.airlift.http.client.TestingRequestFilter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Optional;

import static io.airlift.http.client.HttpStatus.BAD_GATEWAY;
import static io.airlift.http.client.HttpStatus.BAD_REQUEST;
import static io.airlift.testing.Closeables.closeAll;
import static java.util.Objects.requireNonNull;

public abstract class AbstractHttpClientTestHttpProxy
        extends AbstractHttpClientTest
{
    protected JettyHttpClient httpClient;
    protected TestingHttpProxy testingHttpProxy;

    protected AbstractHttpClientTestHttpProxy() {}

    protected AbstractHttpClientTestHttpProxy(String host, String keystore)
    {
        super(host, keystore);
    }

    @BeforeClass
    public void setUpHttpClient()
            throws Exception
    {
        testingHttpProxy = new TestingHttpProxy(Optional.ofNullable(keystore));
        httpClient = new JettyHttpClient("test-shared", createClientConfig(), ImmutableList.of(new TestingRequestFilter()));
    }

    @AfterClass(alwaysRun = true)
    public void tearDownHttpClient()
            throws Exception
    {
        closeAll(httpClient);
        closeAll(testingHttpProxy);
    }

    @Override
    public HttpClientConfig createClientConfig()
    {
        return new HttpClientConfig()
                .setHttpProxy(testingHttpProxy.getHostAndPort());
    }

    @Override
    @Test(enabled = false)
    public void testConnectTimeout()
            throws Exception
    {
        // todo jetty client does not timeout the http proxy connect properly
        super.testConnectTimeout();
    }

    @Test(expectedExceptions = IOException.class)
    public void testConnectionRefused()
            throws Exception
    {
        super.testConnectionRefused();
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testBadPort()
            throws Exception
    {
        super.testBadPort();
    }

    @Test(expectedExceptions = IOException.class)
    public void testUnresolvableHost()
            throws Exception
    {
        super.testUnresolvableHost();
    }

    @Override
    public <T, E extends Exception> T executeRequest(Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        return httpClient.execute(request, new ProxyResponseHandler<>(responseHandler));
    }

    @Override
    public <T, E extends Exception> T executeRequest(HttpClientConfig config, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        try (JettyHttpClient client = new JettyHttpClient("test-private", config, ImmutableList.of(new TestingRequestFilter()))) {
            return client.execute(request, new ProxyResponseHandler<>(responseHandler));
        }
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
            if (response.getStatusCode() == BAD_GATEWAY.code()) {
                return delegate.handleException(request, new IOException());
            }

            if (response.getStatusCode() == BAD_REQUEST.code()) {
                return delegate.handleException(request, new RuntimeException());
            }

            return delegate.handle(request, response);
        }
    }
}
