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

import io.airlift.node.NodeConfig;
import io.airlift.node.NodeInfo;
import org.testng.annotations.Test;

import java.net.URI;

import static io.airlift.testing.Closeables.closeQuietly;
import static org.testng.Assert.assertEquals;

public class TestHttpServerInfo
{
    @Test
    public void testIPv6Url()
            throws Exception
    {
        NodeConfig nodeConfig = new NodeConfig();
        nodeConfig.setEnvironment("test");
        nodeConfig.setNodeInternalAddress("::1");
        nodeConfig.setNodeExternalAddress("2001:db8::2:1");

        NodeInfo nodeInfo = new NodeInfo(nodeConfig);

        HttpServerConfig serverConfig = new HttpServerConfig();
        serverConfig.setHttpEnabled(true);
        serverConfig.setHttpPort(0);
        serverConfig.setHttpsEnabled(true);
        serverConfig.setHttpsPort(0);
        serverConfig.setAdminEnabled(true);

        HttpServerInfo httpServerInfo = new HttpServerInfo(serverConfig, nodeInfo);

        int httpPort = httpServerInfo.getHttpUri().getPort();
        assertEquals(httpServerInfo.getHttpUri(), new URI("http://[::1]:" + httpPort));
        assertEquals(httpServerInfo.getHttpExternalUri(), new URI("http://[2001:db8::2:1]:" + httpPort));

        int httpsPort = httpServerInfo.getHttpsUri().getPort();
        assertEquals(httpServerInfo.getHttpsUri(), new URI("https://[::1]:" + httpsPort));
        assertEquals(httpServerInfo.getHttpsExternalUri(), new URI("https://[2001:db8::2:1]:" + httpsPort));

        int adminPort = httpServerInfo.getAdminUri().getPort();
        assertEquals(httpServerInfo.getAdminUri(), new URI("https://[::1]:" + adminPort));
        assertEquals(httpServerInfo.getAdminExternalUri(), new URI("https://[2001:db8::2:1]:" + adminPort));

        closeChannels(httpServerInfo);
    }

    static void closeChannels(HttpServerInfo info)
    {
        closeQuietly(info.getHttpChannel(), info.getHttpsChannel(), info.getAdminChannel());
    }
}
