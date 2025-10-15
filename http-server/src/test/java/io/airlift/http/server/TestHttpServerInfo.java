/*
 * Copyright 2010 Proofpoint, Inc.
 *
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
package io.airlift.http.server;

import static io.airlift.testing.Closeables.closeAll;
import static org.assertj.core.api.Assertions.assertThat;

import io.airlift.node.NodeConfig;
import io.airlift.node.NodeInfo;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class TestHttpServerInfo {
    @Test
    public void testIPv6Url() throws Exception {
        NodeConfig nodeConfig = new NodeConfig();
        nodeConfig.setEnvironment("test");
        nodeConfig.setNodeInternalAddress("::1");
        nodeConfig.setNodeExternalAddress("2001:db8::2:1");

        NodeInfo nodeInfo = new NodeInfo(nodeConfig);

        HttpServerConfig serverConfig = new HttpServerConfig();
        serverConfig.setHttpEnabled(true);
        serverConfig.setHttpPort(0);
        serverConfig.setHttpsEnabled(true);

        HttpsConfig httpsConfig = new HttpsConfig().setHttpsPort(0);

        HttpServerInfo httpServerInfo = new HttpServerInfo(serverConfig, Optional.ofNullable(httpsConfig), nodeInfo);

        int httpPort = httpServerInfo.getHttpUri().getPort();
        assertThat(httpServerInfo.getHttpUri()).isEqualTo(new URI("http://[::1]:" + httpPort));
        assertThat(httpServerInfo.getHttpExternalUri()).isEqualTo(new URI("http://[2001:db8::2:1]:" + httpPort));

        int httpsPort = httpServerInfo.getHttpsUri().getPort();
        assertThat(httpServerInfo.getHttpsUri()).isEqualTo(new URI("https://[::1]:" + httpsPort));
        assertThat(httpServerInfo.getHttpsExternalUri()).isEqualTo(new URI("https://[2001:db8::2:1]:" + httpsPort));

        closeChannels(httpServerInfo);
    }

    static void closeChannels(HttpServerInfo info) throws IOException {
        closeAll(info.getHttpChannel(), info.getHttpsChannel());
    }
}
