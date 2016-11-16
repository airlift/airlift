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

import static org.testng.Assert.assertEquals;

public class HttpServerInfoTest
{
    @Test
    public void testIPv6Url()
            throws Exception
    {
        NodeConfig nodeConfig = new NodeConfig();
        nodeConfig.setEnvironment("test");
        nodeConfig.setNodeInternalAddress("::1");
        nodeConfig.setNodeExternalAddress("2001:db8:85a3::8a2e:370:7334");

        NodeInfo nodeInfo = new NodeInfo(nodeConfig);

        HttpServerConfig serverConfig = new HttpServerConfig();
        serverConfig.setHttpEnabled(true);
        serverConfig.setHttpPort(80);
        serverConfig.setHttpsEnabled(true);
        serverConfig.setHttpsPort(443);
        serverConfig.setAdminEnabled(true);
        serverConfig.setAdminPort(444);

        HttpServerInfo httpServerInfo = new HttpServerInfo(serverConfig, nodeInfo);

        assertEquals(httpServerInfo.getHttpUri(), new URI("http://[::1]:80"));
        assertEquals(httpServerInfo.getHttpExternalUri(), new URI("http://[2001:db8:85a3::8a2e:370:7334]:80"));

        assertEquals(httpServerInfo.getHttpsUri(), new URI("https://[::1]:443"));
        assertEquals(httpServerInfo.getHttpsExternalUri(), new URI("https://[2001:db8:85a3::8a2e:370:7334]:443"));

        assertEquals(httpServerInfo.getAdminUri(), new URI("https://[::1]:444"));
        assertEquals(httpServerInfo.getAdminExternalUri(), new URI("https://[2001:db8:85a3::8a2e:370:7334]:444"));
    }
}
