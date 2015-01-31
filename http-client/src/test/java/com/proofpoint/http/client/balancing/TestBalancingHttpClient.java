package com.proofpoint.http.client.balancing;

import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.http.client.LimitedRetryable;
import org.testng.annotations.Test;

import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.proofpoint.http.client.testing.BodySourceTester.writeBodySourceTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestBalancingHttpClient
    extends AbstractTestBalancingHttpClient<HttpClient>
{
    @Override
    protected TestingHttpClient createTestingClient()
    {
        return new TestingHttpClient("PUT");
    }

    @Override
    protected BalancingHttpClient createBalancingHttpClient()
    {
        return new BalancingHttpClient(serviceBalancer, httpClient,
                new BalancingHttpClientConfig().setMaxAttempts(3));
    }

    @Override
    protected void assertHandlerExceptionThrown(ResponseHandler responseHandler, RuntimeException handlerException)
            throws Exception
    {
        try {
            balancingHttpClient.execute(request, responseHandler);
            fail("Exception not thrown");
        }
        catch (Exception e) {
            assertSame(e, handlerException, "Exception thrown by BalancingHttpClient");
        }
    }

    @Override
    protected void issueRequest()
            throws Exception
    {
        balancingHttpClient.executeAsync(request, mock(ResponseHandler.class));
    }

    @Test
    public void testGetStats()
    {
        RequestStats requestStats = new RequestStats();
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.getStats()).thenReturn(requestStats);

        balancingHttpClient = new BalancingHttpClient(serviceBalancer, mockClient, new BalancingHttpClientConfig());
        assertSame(balancingHttpClient.getStats(), requestStats);

        verify(mockClient).getStats();
        verifyNoMoreInteractions(mockClient, serviceBalancer);
    }

    @Test
    public void testClose()
    {
        HttpClient mockClient = mock(HttpClient.class);

        balancingHttpClient = new BalancingHttpClient(serviceBalancer, mockClient, new BalancingHttpClientConfig());
        balancingHttpClient.close();

        verify(mockClient).close();
        verifyNoMoreInteractions(mockClient, serviceBalancer);
    }

    class TestingHttpClient
            implements HttpClient, TestingClient
    {
        private String method;
        private List<URI> uris = new ArrayList<>();
        private List<Object> responses = new ArrayList<>();
        private boolean skipBodyGenerator = false;

        TestingHttpClient(String method)
        {
            this.method = method;
            checkArgument(uris.size() == responses.size(), "uris same size as responses");
        }

        public TestingHttpClient expectCall(String uri, Response response)
        {
            return expectCall(URI.create(uri), response);
        }

        public TestingHttpClient expectCall(String uri, Exception exception)
        {
            return expectCall(URI.create(uri), exception);
        }

        private TestingHttpClient expectCall(URI uri, Object response)
        {
            uris.add(uri);
            responses.add(response);
            return this;
        }

        public  TestingClient firstCallNoBodyGenerator()
        {
            skipBodyGenerator = true;
            return this;
        }

        public void assertDone()
        {
            assertEquals(uris.size(), 0, "all expected calls made");
        }

        @Override
        public <T, E extends Exception> HttpResponseFuture<T> executeAsync(Request request, ResponseHandler<T, E> responseHandler)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
                throws E
        {
            assertTrue(!uris.isEmpty(), "call was expected");
            assertEquals(request.getMethod(), method, "request method");
            assertEquals(request.getUri(), uris.remove(0), "request uri");
            assertEquals(request.getBodySource(), bodySource, "request body generator");

            if (skipBodyGenerator) {
                skipBodyGenerator = false;
            }
            else if (bodySource instanceof LimitedRetryable) {
                try {
                    writeBodySourceTo(bodySource, new OutputStream()
                    {
                        @Override
                        public void write(int b)
                        {
                        }
                    });
                }
                catch (Exception e) {
                    fail("BodySource exception", e);
                }
            }

            Object response = responses.remove(0);
            if (response instanceof Exception) {
                return responseHandler.handleException(request, (Exception) response);
            }
            return responseHandler.handle(request, (Response) response);
        }

        @Override
        public RequestStats getStats()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close()
        {
            throw new UnsupportedOperationException();
        }
    }
}
