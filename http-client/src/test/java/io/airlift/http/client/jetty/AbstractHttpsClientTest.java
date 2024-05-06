package io.airlift.http.client.jetty;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.airlift.http.client.AbstractHttpClientTest;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.HttpVersion;
import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
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
import static org.testng.Assert.assertEquals;

public abstract class AbstractHttpsClientTest
        extends AbstractHttpClientTest
{
    protected JettyHttpClient httpClient;

    public AbstractHttpsClientTest()
    {
        super("localhost", getResource("localhost.keystore").toString());
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

    protected abstract HttpClientConfig createClientConfig();

    abstract HttpVersion testedHttpVersion();

    @Override
    public <T, E extends Exception> T executeRequest(Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        return httpClient.execute(upgradeRequest(request, testedHttpVersion()), responseHandler);
    }

    @Override
    public <T, E extends Exception> T executeRequest(HttpClientConfig config, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        config.setKeyStorePath(getResource("localhost.keystore").getPath())
                .setKeyStorePassword("changeit")
                .setTrustStorePath(getResource("localhost.truststore").getPath())
                .setTrustStorePassword("changeit");

        try (JettyHttpClient client = new JettyHttpClient("test-private", config, ImmutableList.of(new TestingRequestFilter()), ImmutableSet.of(new TestingStatusListener(statusCounts)))) {
            return client.execute(upgradeRequest(request, testedHttpVersion()), responseHandler);
        }
    }

    @Test
    public void testProtocolUsed()
            throws Exception
    {
        servlet.setResponseBody("Hello world ;)");

        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        HttpVersion version = executeRequest(request, new HttpVersionResponseHandler());
        assertEquals(version, testedHttpVersion());
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

    private static class HttpVersionResponseHandler
            implements ResponseHandler<HttpVersion, RuntimeException>
    {
        @Override
        public HttpVersion handleException(Request request, Exception exception)
                throws RuntimeException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public HttpVersion handle(Request request, Response response)
                throws RuntimeException
        {
            return response.getHttpVersion();
        }
    }
}
