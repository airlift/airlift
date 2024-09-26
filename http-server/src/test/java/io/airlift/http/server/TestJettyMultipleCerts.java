package io.airlift.http.server;
/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Closer;
import com.google.common.net.HostAndPort;
import io.airlift.event.client.NullEventClient;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.HttpServer.ClientCertificate;
import io.airlift.node.NodeConfig;
import io.airlift.node.NodeInfo;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;

import static com.google.common.io.Resources.getResource;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestJettyMultipleCerts
{
    @Test
    public void test()
            throws Exception
    {
        HttpServlet servlet = new HttpServlet()
        {
            @Override
            protected void service(HttpServletRequest request, HttpServletResponse response)
                    throws IOException
            {
                response.setStatus(200);
                response.getOutputStream().write((request.getServerName() + " OK").getBytes(UTF_8));
            }
        };

        HttpServerConfig config = new HttpServerConfig()
                .setLogEnabled(false)
                .setHttpEnabled(false)
                .setHttpPort(0)
                .setHttpsEnabled(true);
        HttpsConfig httpsConfig = new HttpsConfig()
                .setHttpsPort(0)
                .setKeystorePath(getResource("multiple-certs/server.p12").getPath())
                .setKeystorePassword("airlift");

        NodeInfo nodeInfo = new NodeInfo(new NodeConfig()
                .setEnvironment("test")
                .setNodeInternalAddress("localhost"));

        HttpServerInfo httpServerInfo = new HttpServerInfo(config, Optional.of(httpsConfig), nodeInfo);
        HttpServerProvider serverProvider = new HttpServerProvider(
                httpServerInfo,
                nodeInfo,
                config,
                Optional.of(httpsConfig),
                servlet,
                ImmutableSet.of(new DummyFilter()),
                ImmutableSet.of(),
                false,
                false,
                false,
                ClientCertificate.NONE,
                new RequestStats(),
                new NullEventClient(),
                Optional.empty());

        try (Closer closer = Closer.create()) {
            HttpServer server = serverProvider.get();
            closer.register(() -> {
                try {
                    server.stop();
                }
                catch (Exception ignore) {
                }
            });
            server.start();

            JettyHttpClient client = new JettyHttpClient(new HttpClientConfig()
                    .setTrustStorePath(getResource("multiple-certs/server.p12").getPath())
                    .setTrustStorePassword("airlift"));
            closer.register(client);

            int httpsPort = httpServerInfo.getHttpsUri().getPort();

            tryHost(client, HostAndPort.fromParts("localhost", httpsPort));

            for (String name : List.of("127.0.0.1", "::1")) {
                assertThatThrownBy(() -> tryHost(client, HostAndPort.fromParts(name, httpsPort)))
                        .hasMessageStartingWith(name + " Failed communicating with server")
                        .hasRootCauseMessage("No subject alternative names present");
            }

            // test is skipped unless name is manually added to /etc/hosts:
            //
            // 127.0.0.1 single1 single2 single3 single4
            //
            for (String name : List.of("single1", "single2", "single3", "single4")) {
                if (doesDomainResolveToLocalhost(name)) {
                    tryHost(client, HostAndPort.fromParts(name, httpsPort));
                }
            }
        }
    }

    private static boolean doesDomainResolveToLocalhost(String name)
    {
        try {
            InetAddress localhost = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
            return ImmutableList.copyOf(InetAddress.getAllByName(name)).contains(localhost);
        }
        catch (UnknownHostException ignored) {
            return false;
        }
    }

    private static void tryHost(JettyHttpClient client, HostAndPort address)
    {
        try {
            StatusResponse statusResponse = client.execute(
                    prepareGet()
                            .setUri(URI.create("https://" + address))
                            .build(),
                    createStatusResponseHandler());
            assertThat(statusResponse.getStatusCode()).isEqualTo(200);
        }
        catch (Exception e) {
            throw new RuntimeException(address.getHost() + " " + e.getMessage(), e);
        }
    }
}
