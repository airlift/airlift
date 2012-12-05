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
package com.proofpoint.jmx;

import com.proofpoint.node.NodeInfo;
import org.testng.annotations.Test;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;

import static org.testng.Assert.assertTrue;

public class TestJMXAgent
{
    @Test
    public void testAuto()
            throws IOException
    {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        JmxAgent agent = new JmxAgent(server, new NodeInfo("test"), new JmxConfig());
        agent.start();

        JMXConnector connector = JMXConnectorFactory.connect(agent.getURL());
        connector.connect();

        MBeanServerConnection connection = connector.getMBeanServerConnection();
        assertTrue(connection.getMBeanCount() > 0);
    }

    @Test
    public void testSpecificHost()
            throws IOException
    {
        final String host = InetAddress.getLocalHost().getCanonicalHostName();

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        JmxAgent agent = new JmxAgent(server, new NodeInfo("test"), new JmxConfig().setHostname(host));
        agent.start();

        JMXConnector connector = JMXConnectorFactory.connect(agent.getURL());
        connector.connect();

        MBeanServerConnection connection = connector.getMBeanServerConnection();
        assertTrue(connection.getMBeanCount() > 0);
    }

    @Test
    public void testSpecificHostAndPort()
            throws IOException
    {
        final String host = InetAddress.getLocalHost().getCanonicalHostName();
        final int port = NetUtils.findUnusedPort();

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        JmxConfig config = new JmxConfig()
                .setRmiRegistryPort(port)
                .setHostname(host);

        JmxAgent agent = new JmxAgent(server, new NodeInfo("test"), config);
        agent.start();

        JMXConnector connector = JMXConnectorFactory.connect(agent.getURL());
        connector.connect();

        MBeanServerConnection connection = connector.getMBeanServerConnection();
        assertTrue(connection.getMBeanCount() > 0);
    }
}
