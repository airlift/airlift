package io.airlift.http.client.jetty;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.airlift.http.client.AbstractHttpClientTest;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.TestingRequestFilter;
import io.airlift.http.client.TestingStatusListener;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

public class TestAsyncJettyHttp1ClientHttp1Server
        extends AbstractHttpClientTest
{
    private JettyHttpClient httpClient;

    @BeforeClass
    public void setUpHttpClient()
    {
        httpClient = new JettyHttpClient("test-shared", createClientConfig(), ImmutableList.of(new TestingRequestFilter()), ImmutableSet.of(new TestingStatusListener(statusCounts)));
    }

    @AfterClass(alwaysRun = true)
    public void tearDownHttpClient()
    {
        closeQuietly(httpClient);
    }

    @Override
    protected HttpClientConfig createClientConfig()
    {
        return new HttpClientConfig();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, E extends Exception> T executeRequest(Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        return executeAsync(httpClient, request, responseHandler);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, E extends Exception> T executeRequest(HttpClientConfig config, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        try (JettyHttpClient client = new JettyHttpClient("test-private", config, ImmutableList.of(new TestingRequestFilter()), ImmutableSet.of(new TestingStatusListener(statusCounts)))) {
            return executeAsync(client, request, responseHandler);
        }
    }
}
