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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.google.common.io.Resources.getResource;

public class TestJettyHttpClientHttp3
        extends AbstractHttpClientTest
{
    private JettyHttpClient httpClient;
    private Path tempPemDir;

    public TestJettyHttpClientHttp3()
            throws IOException
    {
        super("localhost", getResource("localhost.keystore").toString());
        tempPemDir = Files.createTempDirectory("pem");
    }

    @BeforeClass
    public void setUpHttpClient()
    {
        httpClient = new JettyHttpClient("test-shared", createClientConfig(), ImmutableList.of(new TestingRequestFilter()), ImmutableSet.of(new TestingStatusListener(statusCounts)));
    }

    @AfterClass(alwaysRun = true)
    public void tearDownHttpClient()
            throws IOException
    {
        closeQuietly(httpClient);
        Files.delete(tempPemDir);
    }

    @Override
    protected HttpClientConfig createClientConfig()
    {
        return new HttpClientConfig()
                .setHttp2Enabled(true)
                .setHttp3Enabled(true)
                .setHttp3PemPath(tempPemDir.toString())
                .setKeyStorePath(getResource("localhost.keystore").getPath())
                .setKeyStorePassword("changeit")
                .setTrustStorePath(getResource("localhost.truststore").getPath())
                .setTrustStorePassword("changeit");
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
                .setKeyStorePassword("changeit")
                .setTrustStorePath(getResource("localhost.truststore").getPath())
                .setTrustStorePassword("changeit");

        try (JettyHttpClient client = new JettyHttpClient("test-private", config, ImmutableList.of(new TestingRequestFilter()), ImmutableSet.of(new TestingStatusListener(statusCounts)))) {
            return client.execute(request, responseHandler);
        }
    }

    @Override
    public void testConnectNoReadClose()
    {
    }

    @Override
    public void testConnectReadRequestClose()
    {
    }

    @Override
    public void testConnectReadRequestWriteJunkHangup()
    {
    }

    @Override
    public void testConnectionRefused()
    {
    }
}
