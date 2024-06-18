package io.airlift.http.client.jetty;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.airlift.http.client.AbstractHttpClientTest;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.TestingRequestFilter;
import io.airlift.http.client.TestingSocksProxy;
import io.airlift.http.client.TestingStatusListener;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;

public class TestJettyHttpClientSocksProxy
        extends AbstractHttpClientTest
{
    private JettyHttpClient httpClient;
    private TestingSocksProxy testingSocksProxy;

    @BeforeAll
    public void setUpHttpClient()
            throws IOException
    {
        testingSocksProxy = new TestingSocksProxy().start();
        httpClient = new JettyHttpClient("test-shared", createClientConfig(), ImmutableList.of(new TestingRequestFilter()), ImmutableSet.of(new TestingStatusListener(statusCounts)));
    }

    @AfterAll
    public void tearDownHttpClient()
    {
        closeQuietly(httpClient);
        closeQuietly(testingSocksProxy);
    }

    @Override
    protected HttpClientConfig createClientConfig()
    {
        return new HttpClientConfig()
                .setHttp2Enabled(false)
                .setSocksProxy(testingSocksProxy.getHostAndPort());
    }

    @Override
    public <T, E extends Exception> T executeRequest(Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        return httpClient.execute(request, responseHandler);
    }

    @Override
    public <T, E extends Exception> T executeRequest(HttpClientConfig config, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        config.setSocksProxy(testingSocksProxy.getHostAndPort());
        try (JettyHttpClient client = new JettyHttpClient("test-private", config, ImmutableList.of(new TestingRequestFilter()), ImmutableSet.of(new TestingStatusListener(statusCounts)))) {
            return client.execute(request, responseHandler);
        }
    }

    @Override
    @Test
    @Timeout(5)
    public void testConnectTimeout()
            throws Exception
    {
        doTestConnectTimeout(true);
    }
}
