package com.proofpoint.jmx;

import com.proofpoint.net.NetUtils;
import org.testng.annotations.Test;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.Inet4Address;

import static org.testng.Assert.assertTrue;

public class TestJMXAgent
{
    @Test
    public void testAuto()
            throws IOException
    {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        JMXAgent agent = new JMXAgent(server, null, null, null);
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
        String host = Inet4Address.getLocalHost().getCanonicalHostName();

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        JMXAgent agent = new JMXAgent(server, host, null, null);
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
        String host = Inet4Address.getLocalHost().getCanonicalHostName();
        int port = NetUtils.findUnusedPort();

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        JMXAgent agent = new JMXAgent(server, host, port, null);
        agent.start();

        JMXConnector connector = JMXConnectorFactory.connect(agent.getURL());
        connector.connect();

        MBeanServerConnection connection = connector.getMBeanServerConnection();
        assertTrue(connection.getMBeanCount() > 0);
    }

}
