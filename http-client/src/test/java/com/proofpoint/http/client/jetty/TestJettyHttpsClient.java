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

import javax.net.ssl.SSLHandshakeException;
import java.io.EOFException;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

import static com.google.common.io.Resources.getResource;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.testing.Closeables.closeQuietly;

public class TestJettyHttpsClient
        extends AbstractHttpClientTest
{
    private JettyHttpClient httpClient;
    private JettyIoPool jettyIoPool;

    TestJettyHttpsClient()
    {
        super("localhost", getResource("localhost.keystore").toString());
    }

    @BeforeMethod
    public void setUp()
    {
        jettyIoPool = new JettyIoPool("test-shared", new JettyIoPoolConfig());

        HttpClientConfig config = new HttpClientConfig()
                .setKeyStorePath(getResource("localhost.keystore").getPath())
                .setKeyStorePassword("changeit");

        httpClient = new JettyHttpClient(config, jettyIoPool, ImmutableList.<HttpRequestFilter>of(new TestingRequestFilter()));
    }

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        closeQuietly(httpClient);
        closeQuietly(jettyIoPool);
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
        config.setKeyStorePath(getResource("localhost.keystore").getPath())
                .setKeyStorePassword("changeit");

        try (
                JettyIoPool jettyIoPool = new JettyIoPool("test-private", new JettyIoPoolConfig());
                JettyHttpClient client = new JettyHttpClient(config, jettyIoPool, ImmutableList.<HttpRequestFilter>of(new TestingRequestFilter()))
        ) {
            return client.execute(request, responseHandler);
        }
    }

    @Test(expectedExceptions = {SSLHandshakeException.class, EOFException.class})
    public void testCertHostnameMismatch()
            throws Exception
    {
        URI uri = new URI("https", null, "127.0.0.1", baseURI.getPort(), "/", null, null);
        Request request = prepareGet()
                .setUri(uri)
                .build();

        executeRequest(request, new ResponseToStringHandler());
    }

    @Test(expectedExceptions = {IOException.class,  IllegalStateException.class})
    public void testConnectReadRequestClose()
            throws Exception
    {
        super.testConnectReadRequestClose();
    }

    @Test(expectedExceptions = {IOException.class,  IllegalStateException.class})
    public void testConnectNoReadClose()
            throws Exception
    {
        super.testConnectNoReadClose();
    }

    @Test(expectedExceptions = {IOException.class, TimeoutException.class, IllegalStateException.class})
    public void testConnectReadIncompleteClose()
            throws Exception
    {
        super.testConnectReadIncompleteClose();
    }
}
