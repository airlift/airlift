package io.airlift.http.server.testing;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.StringResponseHandler;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.HttpServer;
import io.airlift.http.server.HttpServerConfig;
import io.airlift.http.server.HttpServerFeatures;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.HttpsConfig;
import io.airlift.node.NodeInfo;
import io.airlift.units.Duration;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.io.Resources.getResource;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class TestTestingHttps2Server
        extends AbstractTestTestingHttpServer
{
    TestTestingHttps2Server()
    {
        super(HttpServerFeatures.builder()
                .withCaseSensitiveHeaderCache(true)
                .build());
    }

    @Override
    protected HttpClientConfig getHttpClientConfig()
    {
        return super.getHttpClientConfig()
                .setVerifyHostname(false)
                .setHttp2Enabled(true) // Enable HTTP/2 protocol
                .setKeyStorePath(getResource("clientcert-java/client.keystore").getPath())
                .setKeyStorePassword("airlift")
                .setTrustStorePath(getResource("clientcert-java/client.truststore").getPath())
                .setTrustStorePassword("airlift");
    }

    @Override
    protected TestingHttpServer createTestingHttpServer(HttpServerFeatures serverFeatures, DummyServlet servlet, Map<String, String> params)
            throws IOException
    {
        NodeInfo nodeInfo = new NodeInfo("test");
        HttpServerConfig config = new HttpServerConfig()
                .setHttpPort(0)
                .setHttpEnabled(false)
                .setHttpsEnabled(true);

        HttpsConfig httpsConfig = new HttpsConfig()
                .setHttpsPort(0)
                .setKeystorePath(getResource("clientcert-java/server.keystore").getPath())
                .setKeystorePassword("airlift");

        HttpServerInfo httpServerInfo = new HttpServerInfo(config, Optional.of(httpsConfig), nodeInfo);
        return new TestingHttpServer(httpServerInfo, nodeInfo, config, Optional.of(httpsConfig), servlet, params, Set.of(), Set.of(), serverFeatures, HttpServer.ClientCertificate.NONE);
    }

    @Override
    protected TestingHttpServer createTestingHttpServerWithFilter(HttpServerFeatures serverFeatures, DummyServlet servlet, Map<String, String> params, DummyFilter filter)
            throws IOException
    {
        NodeInfo nodeInfo = new NodeInfo("test");
        HttpServerConfig config = new HttpServerConfig()
                .setHttpPort(0)
                .setHttpEnabled(false)
                .setHttpsEnabled(true);

        HttpsConfig httpsConfig = new HttpsConfig()
                .setHttpsPort(0)
                .setKeystorePath(getResource("clientcert-java/server.keystore").getPath())
                .setKeystorePassword("airlift");

        HttpServerInfo httpServerInfo = new HttpServerInfo(config, Optional.of(httpsConfig), nodeInfo);
        return new TestingHttpServer(httpServerInfo, nodeInfo, config, Optional.of(httpsConfig), servlet, params, ImmutableSet.of(filter), ImmutableSet.of(), serverFeatures, HttpServer.ClientCertificate.NONE);
    }

    @Test
    @Override
    public void testForwardedHeaderIsRejected()
            throws Exception
    {
        DummyServlet servlet = new DummyServlet();
        TestingHttpServer server = createTestingHttpServer(serverFeatures, servlet, ImmutableMap.of());

        try {
            server.start();
            try (HttpClient client = new JettyHttpClient(getHttpClientConfig().setConnectTimeout(new Duration(1, SECONDS)))) {
                Request request = prepareGet()
                        .setUri(server.getBaseUrl())
                        .setHeader(HttpHeaders.X_FORWARDED_FOR, "129.0.0.1")
                        .setHeader(HttpHeaders.X_FORWARDED_HOST, "localhost.localdomain")
                        .build();
                StringResponseHandler.StringResponse execute = client.execute(request, createStringResponseHandler());
                assertThat(execute.getStatusCode()).isEqualTo(406);
                // Headers always lower-cased in HTTP/2
                assertThat(execute.getBody())
                        .containsAnyOf("Server configuration does not allow processing of the x-forwarded-for", "Server configuration does not allow processing of the x-forwarded-host");
            }
        }
        finally {
            server.stop();
        }
    }
}
