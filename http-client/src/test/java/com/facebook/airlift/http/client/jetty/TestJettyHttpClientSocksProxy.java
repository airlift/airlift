package com.facebook.airlift.http.client.jetty;

import com.google.common.collect.ImmutableList;
import com.facebook.airlift.http.client.AbstractHttpClientTest;
import com.facebook.airlift.http.client.HttpClientConfig;
import com.facebook.airlift.http.client.Request;
import com.facebook.airlift.http.client.ResponseHandler;
import com.facebook.airlift.http.client.TestingRequestFilter;
import com.facebook.airlift.http.client.TestingSocksProxy;
import com.facebook.airlift.http.client.spnego.KerberosConfig;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;

import static io.airlift.testing.Closeables.closeQuietly;

public class TestJettyHttpClientSocksProxy
        extends AbstractHttpClientTest
{
    private JettyHttpClient httpClient;
    private TestingSocksProxy testingSocksProxy;

    @BeforeClass
    public void setUpHttpClient()
            throws IOException
    {
        testingSocksProxy = new TestingSocksProxy().start();
        httpClient = new JettyHttpClient("test-shared", createClientConfig(), new KerberosConfig(), ImmutableList.of(new TestingRequestFilter()));
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
        try (JettyHttpClient client = new JettyHttpClient("test-private", config, new KerberosConfig(), ImmutableList.of(new TestingRequestFilter()))) {
            return client.execute(request, responseHandler);
        }
    }

    @Override
    @Test(expectedExceptions = IOException.class, expectedExceptionsMessageRegExp = ".*SOCKS4 .*")
    public void testBadPort()
            throws Exception
    {
        // todo this should be handled by jetty client before connecting to the socks proxy
        super.testBadPort();
    }

    @Override
    @Test(enabled = false)
    public void testConnectTimeout()
            throws Exception
    {
        // todo jetty client does not timeout the socks proxy connect properly
        super.testConnectTimeout();
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
