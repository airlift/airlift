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
import org.testng.annotations.Test;

import java.io.IOException;

import static com.proofpoint.testing.Closeables.closeQuietly;

public class TestJettyHttpClientSocksProxy
// Intermittently fails due to bug in the Jetty SOCKS code        extends AbstractHttpClientTest
{
    private JettyHttpClient httpClient;
    private JettyIoPool jettyIoPool;
    private TestingSocksProxy testingSocksProxy;

    @BeforeMethod
    public void setUp()
            throws IOException
    {
        testingSocksProxy = new TestingSocksProxy().start();
        jettyIoPool = new JettyIoPool("test-shared", new JettyIoPoolConfig());
        httpClient = new JettyHttpClient(new HttpClientConfig().setSocksProxy(testingSocksProxy.getHostAndPort()), jettyIoPool, ImmutableList.<HttpRequestFilter>of(new TestingRequestFilter()));
    }

    @AfterMethod
    public void tearDown()
    {
        closeQuietly(httpClient);
        closeQuietly(jettyIoPool);
        closeQuietly(testingSocksProxy);
    }

//    @Override
//    public <T, E extends Exception> T executeRequest(Request request, ResponseHandler<T, E> responseHandler)
//            throws Exception
//    {
//        return httpClient.execute(request, responseHandler);
//    }
//
//    @Override
//    public <T, E extends Exception> T executeRequest(HttpClientConfig config, Request request, ResponseHandler<T, E> responseHandler)
//            throws Exception
//    {
//        config.setSocksProxy(testingSocksProxy.getHostAndPort());
//        try (
//                JettyIoPool jettyIoPool = new JettyIoPool("test-private", new JettyIoPoolConfig());
//                JettyHttpClient client = new JettyHttpClient(config, jettyIoPool, ImmutableList.<HttpRequestFilter>of(new TestingRequestFilter()))
//        ) {
//            return client.execute(request, responseHandler);
//        }
//    }
//
//    @Override
//    @Test(expectedExceptions = IOException.class, expectedExceptionsMessageRegExp = ".*SOCKS4 .*")
//    public void testBadPort()
//            throws Exception
//    {
//        // todo this should be handled by jetty client before connecting to the socks proxy
//        super.testBadPort();
//    }
//
//    @Override
//    @Test(enabled = false)
//    public void testConnectTimeout()
//            throws Exception
//    {
//        // todo jetty client does not timeout the socks proxy connect properly
//        super.testConnectTimeout();
//    }
//
//    @Override
//    @Test(expectedExceptions = IOException.class, expectedExceptionsMessageRegExp = ".*SOCKS4 .*")
//    public void testConnectionRefused()
//            throws Exception
//    {
//        super.testConnectionRefused();
//    }
//
//    @Override
//    @Test(expectedExceptions = IOException.class, expectedExceptionsMessageRegExp = ".*SOCKS4 .*")
//    public void testUnresolvableHost()
//            throws Exception
//    {
//        super.testUnresolvableHost();
//    }
//
//    @Override
//    @Test(enabled = false)
//    public void testPostMethod()
//    {
//        // Fails on Unix and we don't care about SOCKS
//    }
}
