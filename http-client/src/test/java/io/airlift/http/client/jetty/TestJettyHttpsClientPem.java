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
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

import static com.google.common.io.Resources.getResource;
import static io.airlift.http.client.Request.Builder.prepareGet;

public class TestJettyHttpsClientPem
        extends AbstractHttpClientTest
{
    private JettyHttpClient httpClient;

    public TestJettyHttpsClientPem()
    {
        super("localhost", getResource("server.keystore").toString());
    }

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
        return new HttpClientConfig()
                .setHttp2Enabled(false)
                .setKeyStorePath(getResource("client.pem").getPath())
                .setKeyStorePassword("changeit")
                .setTrustStorePath(getResource("ca.crt").getPath());
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
        config.setKeyStorePath(getResource("client.pem").getPath())
                .setTrustStorePath(getResource("ca.crt").getPath());

        try (JettyHttpClient client = new JettyHttpClient("test-private", config, ImmutableList.of(new TestingRequestFilter()), ImmutableSet.of(new TestingStatusListener(statusCounts)))) {
            return client.execute(request, responseHandler);
        }
    }

    // TLS connections seem to have some conditions that do not respect timeouts
    @Test(invocationCount = 10, successPercentage = 50, timeOut = 20_000)
    @Override
    public void testConnectTimeout()
            throws Exception
    {
        super.testConnectTimeout();
    }

    @Test(expectedExceptions = IOException.class)
    public void testCertHostnameMismatch()
            throws Exception
    {
        URI uri = new URI("https", null, "127.0.0.1", baseURI.getPort(), "/", null, null);
        Request request = prepareGet()
                .setUri(uri)
                .build();

        executeRequest(request, new ExceptionResponseHandler());
    }

    @Override
    @Test(expectedExceptions = {IOException.class, IllegalStateException.class})
    public void testConnectReadRequestClose()
            throws Exception
    {
        super.testConnectReadRequestClose();
    }

    @Override
    @Test(expectedExceptions = {IOException.class, IllegalStateException.class})
    public void testConnectNoReadClose()
            throws Exception
    {
        super.testConnectNoReadClose();
    }

    @Override
    @Test(expectedExceptions = {IOException.class, TimeoutException.class, IllegalStateException.class})
    public void testConnectReadIncompleteClose()
            throws Exception
    {
        super.testConnectReadIncompleteClose();
    }
}
