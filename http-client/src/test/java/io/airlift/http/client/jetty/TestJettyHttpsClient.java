package io.airlift.http.client.jetty;

import com.google.common.collect.ImmutableList;
import io.airlift.http.client.AbstractHttpClientTest;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.HttpRequestFilter;
import io.airlift.http.client.Request;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.TestingRequestFilter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.net.ssl.SSLHandshakeException;

import java.io.EOFException;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

import static com.google.common.io.Resources.getResource;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.testing.Closeables.closeQuietly;

public class TestJettyHttpsClient
        extends AbstractHttpClientTest
{
    private JettyHttpClient httpClient;
    private JettyIoPool jettyIoPool;

    TestJettyHttpsClient()
    {
        super("localhost", getResource("localhost.keystore").toString());
    }

    @BeforeClass
    public void setUpHttpClient()
    {
        jettyIoPool = new JettyIoPool("test-shared", new JettyIoPoolConfig());

        HttpClientConfig config = new HttpClientConfig()
                .setKeyStorePath(getResource("localhost.keystore").getPath())
                .setKeyStorePassword("changeit");

        httpClient = new JettyHttpClient(config, jettyIoPool, ImmutableList.<HttpRequestFilter>of(new TestingRequestFilter()));
    }

    @AfterClass
    public void tearDownHttpClient()
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
