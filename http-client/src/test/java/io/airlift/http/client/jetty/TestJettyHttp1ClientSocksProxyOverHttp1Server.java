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
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;

public class TestJettyHttp1ClientSocksProxyOverHttp1Server
        extends AbstractHttpClientTest
{
    private JettyHttpClient httpClient;
    private TestingSocksProxy testingSocksProxy;

    @BeforeClass
    public void setUpHttpClient()
            throws IOException
    {
        testingSocksProxy = new TestingSocksProxy().start();
        httpClient = new JettyHttpClient("test-shared", createClientConfig(), ImmutableList.of(new TestingRequestFilter()), ImmutableSet.of(new TestingStatusListener(statusCounts)));
    }

    @AfterClass(alwaysRun = true)
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
    @Test(timeOut = 5000)
    public void testConnectTimeout()
            throws Exception
    {
        doTestConnectTimeout(true);
    }

    @Override
    @Test(expectedExceptions = IOException.class, expectedExceptionsMessageRegExp = ".*SOCKS4 .*")
    public void testConnectionRefused()
            throws Exception
    {
        super.testConnectionRefused();
    }

    @Override
    @Test(expectedExceptions = IOException.class, expectedExceptionsMessageRegExp = ".*SOCKS4 .*")
    public void testUnresolvableHost()
            throws Exception
    {
        super.testUnresolvableHost();
    }
}
