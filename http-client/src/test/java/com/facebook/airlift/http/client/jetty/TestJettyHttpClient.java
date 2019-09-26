package com.facebook.airlift.http.client.jetty;

import com.facebook.airlift.http.client.AbstractHttpClientTest;
import com.facebook.airlift.http.client.HttpClientConfig;
import com.facebook.airlift.http.client.HttpRequestFilter;
import com.facebook.airlift.http.client.Request;
import com.facebook.airlift.http.client.ResponseHandler;
import com.facebook.airlift.http.client.TestingRequestFilter;
import com.facebook.airlift.http.client.spnego.KerberosConfig;
import com.google.common.collect.ImmutableList;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import static com.facebook.airlift.testing.Closeables.closeQuietly;

public class TestJettyHttpClient
        extends AbstractHttpClientTest
{
    private JettyHttpClient httpClient;

    @BeforeClass
    public void setUpHttpClient()
    {
        httpClient = new JettyHttpClient("test-shared", createClientConfig(), new KerberosConfig(), ImmutableList.of(new TestingRequestFilter()));
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
                .setHttp2Enabled(false);
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
        try (JettyHttpClient client = new JettyHttpClient("test-private", config, new KerberosConfig(), ImmutableList.<HttpRequestFilter>of(new TestingRequestFilter()))) {
            return client.execute(request, responseHandler);
        }
    }
}
