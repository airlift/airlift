package io.airlift.http.client.jetty;

import io.airlift.http.client.AbstractHttpClientTest;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.ResponseHandler;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.URI;

import static com.google.common.io.Resources.getResource;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestJettyHttpsClientPem
        extends AbstractHttpClientTest
{
    public TestJettyHttpsClientPem()
    {
        super(getResource("server.keystore").toString());
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
    public <T, E extends Exception> T executeRequest(CloseableTestHttpServer server, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        return executeRequest(server, createClientConfig(), request, responseHandler);
    }

    @Override
    public <T, E extends Exception> T executeRequest(CloseableTestHttpServer server, HttpClientConfig config, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        config.setKeyStorePath(getResource("client.pem").getPath())
                .setTrustStorePath(getResource("ca.crt").getPath());

        JettyHttpClient client = server.createClient(config);
        return client.execute(request, responseHandler);
    }

    // TLS connections seem to have some conditions that do not respect timeouts
    @RepeatedTest(value = 10, failureThreshold = 5)
    @Timeout(20)
    @Override
    public void testConnectTimeout()
            throws Exception
    {
        super.testConnectTimeout();
    }

    @Test
    public void testCertHostnameMismatch()
            throws Exception
    {
        try (CloseableTestHttpServer server = newServer()) {
            URI uri = new URI("https", null, "127.0.0.1", server.baseURI().getPort(), "/", null, null);
            Request request = prepareGet()
                    .setUri(uri)
                    .build();

            try (JettyHttpClient client = new JettyHttpClient("test", createClientConfig())) {
                assertThatThrownBy(() -> client.execute(request, new ExceptionResponseHandler()))
                        .isInstanceOf(IOException.class);
            }
        }
    }
}
