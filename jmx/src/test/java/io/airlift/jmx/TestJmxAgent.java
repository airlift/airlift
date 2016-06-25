package io.airlift.jmx;

import org.testng.annotations.Test;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import java.io.IOException;
import java.lang.management.ManagementFactory;

import static org.testng.Assert.assertTrue;

public class TestJmxAgent
{
    @Test
    public void testRandomPorts()
            throws Exception
    {
        verify(new JmxConfig());
    }

    @Test
    public void testRegistryPort()
            throws Exception
    {
        JmxConfig config = new JmxConfig().setRmiRegistryPort(NetUtils.findUnusedPort());

        verify(config);
    }

    @Test
    public void testRmiServerPort()
            throws Exception
    {
        JmxConfig config = new JmxConfig().setRmiServerPort(NetUtils.findUnusedPort());

        verify(config);
    }

    @Test
    public void testBothPorts()
            throws Exception
    {
        JmxConfig config = new JmxConfig()
                .setRmiServerPort(NetUtils.findUnusedPort())
                .setRmiRegistryPort(NetUtils.findUnusedPort());

        verify(config);
    }

    @Test
    public void testRestart()
            throws Exception
    {
        JmxConfig config = new JmxConfig()
                .setRmiServerPort(NetUtils.findUnusedPort())
                .setRmiRegistryPort(NetUtils.findUnusedPort());

        verify(config);
        verify(config);
    }

    private void verify(JmxConfig config)
            throws IOException
    {
        try (JmxAgent agent = new JmxAgent(config, ManagementFactory.getPlatformMBeanServer())) {
            agent.start();
            JMXServiceURL url = agent.getUrl();

            JMXConnector connector = JMXConnectorFactory.connect(url);
            connector.connect();

            MBeanServerConnection connection = connector.getMBeanServerConnection();
            assertTrue(connection.getMBeanCount() > 0);
        }
    }
}
