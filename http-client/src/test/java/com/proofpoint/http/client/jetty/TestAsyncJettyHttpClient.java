package com.proofpoint.http.client.jetty;

import com.google.common.collect.ImmutableList;
import com.proofpoint.http.client.AbstractHttpClientTest;
import com.proofpoint.http.client.HttpClientConfig;
import com.proofpoint.http.client.HttpRequestFilter;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.http.client.TestingRequestFilter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.util.concurrent.ExecutionException;

import static com.google.common.base.Throwables.propagate;
import static com.google.common.base.Throwables.propagateIfPossible;
import static com.proofpoint.http.client.HttpClient.HttpResponseFuture;
import static com.proofpoint.testing.Closeables.closeQuietly;
import static java.lang.Thread.currentThread;
import static org.testng.Assert.fail;

public class TestAsyncJettyHttpClient
        extends AbstractHttpClientTest
{
    private JettyHttpClient httpClient;
    private JettyIoPool jettyIoPool;

    @BeforeMethod
    public void setUp()
    {
        jettyIoPool = new JettyIoPool("test-shared", new JettyIoPoolConfig());
        httpClient = new JettyHttpClient(new HttpClientConfig(), jettyIoPool, ImmutableList.<HttpRequestFilter>of(new TestingRequestFilter()));
    }

    @Override
    @AfterMethod
    public void tearDown()
            throws Exception
    {
        closeQuietly(httpClient);
        closeQuietly(jettyIoPool);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, E extends Exception> T executeRequest(Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        HttpResponseFuture<T> future = null;
        try {
            future = httpClient.executeAsync(request, responseHandler);
        }
        catch (Exception e) {
            fail("Unexpected exception", e);
        }

        try {
            return future.get();
        }
        catch (InterruptedException e) {
            currentThread().interrupt();
            throw propagate(e);
        }
        catch (ExecutionException e) {
            propagateIfPossible(e.getCause());

            if (e.getCause() instanceof Exception) {
                // the HTTP client and ResponseHandler interface enforces this
                throw (E) e.getCause();
            }

            // e.getCause() is some direct subclass of throwable
            throw propagate(e.getCause());
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, E extends Exception> T executeRequest(HttpClientConfig config, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        try (
                JettyIoPool jettyIoPool = new JettyIoPool("test-private", new JettyIoPoolConfig());
                JettyHttpClient client = new JettyHttpClient(config, jettyIoPool, ImmutableList.<HttpRequestFilter>of(new TestingRequestFilter()))
        ) {
            HttpResponseFuture<T> future = null;
            try {
                future = client.executeAsync(request, responseHandler);
            }
            catch (Exception e) {
                fail("Unexpected exception", e);
            }

            try {
                return future.get();
            }
            catch (InterruptedException e) {
                currentThread().interrupt();
                throw propagate(e);
            }
            catch (ExecutionException e) {
                propagateIfPossible(e.getCause());

                if (e.getCause() instanceof Exception) {
                    // the HTTP client and ResponseHandler interface enforces this
                    throw (E) e.getCause();
                }

                // e.getCause() is some direct subclass of throwable
                throw propagate(e.getCause());
            }
        }
    }
}
