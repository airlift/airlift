package io.airlift.http.client.jetty;

import io.airlift.http.client.AbstractHttpClientTest;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.ResponseHandler;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.google.common.io.Resources.getResource;

public class TestJettyHttpClientHttp3
        extends AbstractHttpClientTest
{
    private Path tempPemDir;

    public TestJettyHttpClientHttp3()
            throws IOException
    {
        super(getResource("localhost.keystore").toString());
        tempPemDir = Files.createTempDirectory("pem");
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
    public <T, E extends Exception> T executeRequest(CloseableTestHttpServer server, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        return executeRequest(server, createClientConfig(), request, responseHandler);
    }

    @Override
    public <T, E extends Exception> T executeRequest(CloseableTestHttpServer server, HttpClientConfig config, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        try (JettyHttpClient client = server.createClient(config)) {
            return client.execute(request, responseHandler);
        }
    }

    @Override
    @Disabled
    @Test
    public void testConnectNoReadClose()
            throws Exception
    {
        super.testConnectNoReadClose();
    }

    @Override
    @Disabled
    @Test
    public void testConnectReadRequestClose()
            throws Exception
    {
        super.testConnectReadRequestClose();
    }

    @Override
    @Disabled
    @Test
    public void testConnectReadRequestWriteJunkHangup()
            throws Exception
    {
        super.testConnectReadRequestWriteJunkHangup();
    }

    @Override
    @Disabled
    @Test
    public void testConnectionRefused()
            throws Exception
    {
        super.testConnectionRefused();
    }

    @Override
    @Disabled
    @Test
    public void testPutMethodWithLargeStreamingBodyGenerator()
            throws Exception
    {
        super.testPutMethodWithLargeStreamingBodyGenerator();
    }

    @Override
    @Disabled
    @Test
    public void testPutMethodWithStreamingBodyGenerator()
            throws Exception
    {
        super.testPutMethodWithStreamingBodyGenerator();
    }
}
