package io.airlift.http.client.jetty;

import com.google.common.collect.ImmutableList;
import io.airlift.http.client.AbstractHttpClientTest;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.HttpRequestFilter;
import io.airlift.http.client.Request;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.TestingRequestFilter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.Closeable;
import java.io.IOException;

public class TestJettyHttpClientSocksProxy
        extends AbstractHttpClientTest
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
        closeIgnoreException(httpClient);
        closeIgnoreException(jettyIoPool);
        closeIgnoreException(testingSocksProxy);
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
        try (
                JettyIoPool jettyIoPool = new JettyIoPool("test-private", new JettyIoPoolConfig());
                JettyHttpClient client = new JettyHttpClient(config, jettyIoPool, ImmutableList.<HttpRequestFilter>of(new TestingRequestFilter()))
        ) {
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

    private static void closeIgnoreException(Closeable closeable)
    {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        }
        catch (IOException ignored) {
            // nothing we can do about this
        }
    }
}
