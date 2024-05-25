package io.airlift.http.server.testing;

import com.google.common.collect.ImmutableSet;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.server.HttpServer;
import io.airlift.http.server.HttpServerConfig;
import io.airlift.http.server.HttpServerFeatures;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.HttpsConfig;
import io.airlift.node.NodeInfo;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.io.Resources.getResource;

public class TestTestingHttps2Server
        extends AbstractTestTestingHttpServer
{
    TestTestingHttps2Server()
    {
        super(HttpServerFeatures.builder()
                .withHttp2(true)
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
}
