package com.proofpoint.http.client.balancing;

import com.google.common.util.concurrent.AbstractFuture;
import com.proofpoint.http.client.BodyGenerator;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.HttpClient.HttpResponseFuture;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.http.client.SyncToAsyncWrapperClient;
import org.testng.annotations.Test;

import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.google.common.base.Preconditions.checkArgument;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestAsyncBalancingHttpClient
    extends AbstractTestBalancingHttpClient<SyncToAsyncWrapperClient>
{
    @Override
    protected TestingHttpClient createTestingClient()
    {
        return new TestingHttpClient("PUT");
    }

    @Override
    protected SyncToAsyncWrapperClient createBalancingHttpClient()
    {
        return new SyncToAsyncWrapperClient(
                new BalancingHttpClient(serviceBalancer, httpClient,
                        new BalancingHttpClientConfig().setMaxAttempts(3)));
    }

    @Override
    protected void assertHandlerExceptionThrown(ResponseHandler responseHandler, RuntimeException handlerException)
            throws Exception
    {
        HttpResponseFuture future = balancingHttpClient.executeAsync(request, responseHandler);
        try {
            future.get();
            fail("Exception not thrown");
        }
        catch (ExecutionException e) {
            assertSame(e.getCause(), handlerException, "Exception thrown by BalancingAsyncHttpClient");
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

        balancingHttpClient = new SyncToAsyncWrapperClient(
                new BalancingHttpClient(serviceBalancer, mockClient, new BalancingHttpClientConfig()));
        assertSame(balancingHttpClient.getStats(), requestStats);

        verify(mockClient).getStats();
        verifyNoMoreInteractions(mockClient, serviceBalancer);
    }

    @Test
    public void testClose()
    {
        HttpClient mockClient = mock(HttpClient.class);

        balancingHttpClient = new SyncToAsyncWrapperClient(
                new BalancingHttpClient(serviceBalancer, mockClient, new BalancingHttpClientConfig()));
        balancingHttpClient.close();

        verify(mockClient).close();
        verifyNoMoreInteractions(mockClient, serviceBalancer);
    }

    // TODO tests for interruption and cancellation

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
            assertTrue(!uris.isEmpty(), "call was expected");
            assertEquals(request.getMethod(), method, "request method");
            assertEquals(request.getUri(), uris.remove(0), "request uri");
            assertEquals(request.getBodySource(), bodySource, "request body generator");

            if (skipBodyGenerator) {
                skipBodyGenerator = false;
            }
            else {
                try {
                    if (bodySource instanceof BodyGenerator) {
                        ((BodyGenerator) bodySource).write(new OutputStream()
                        {
                            @Override
                            public void write(int b)
                            {
                            }
                        });
                    }
                }
                catch (Exception e) {
                    fail("BodyGenerator exception", e);
                }
            }

            Object response = responses.remove(0);
            // TODO: defer availability of return values ?
            if (response instanceof Exception) {
                try {
                    return new ImmediateAsyncHttpFuture<>(responseHandler.handleException(request, (Exception) response));
                }
                catch (Exception e) {
                    return new ImmediateFailedAsyncHttpFuture<>((E) e);
                }
            }

            try {
                return new ImmediateAsyncHttpFuture<>(responseHandler.handle(request, (Response) response));
            }
            catch (Exception e) {
                return new ImmediateFailedAsyncHttpFuture<>((E) e);
            }
        }

        @Override
        public <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
                throws E
        {
            throw new UnsupportedOperationException();
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

        private class ImmediateAsyncHttpFuture<T, E extends Exception>
                extends AbstractFuture<T>
                implements HttpResponseFuture<T>
        {
            public ImmediateAsyncHttpFuture(T value)
            {
                set(value);
            }

            @Override
            public String getState()
            {
                throw new UnsupportedOperationException();
            }
        }

        private class ImmediateFailedAsyncHttpFuture<T, E extends Exception>
                extends AbstractFuture<T>
                implements HttpResponseFuture<T>
        {
            private final E exception;

            public ImmediateFailedAsyncHttpFuture(E exception)
            {
                this.exception = exception;
                setException(exception);
            }

            @Override
            public String getState()
            {
                throw new UnsupportedOperationException();
            }
        }
    }
}
