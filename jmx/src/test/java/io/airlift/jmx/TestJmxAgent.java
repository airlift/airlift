package io.airlift.jmx;

import org.testng.annotations.Test;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import static org.testng.Assert.assertTrue;

public class TestJmxAgent
{
    @Test
    public void testSanity()
            throws Exception
    {
        JmxAgent agent = new JmxAgent(new JmxConfig());
        JMXServiceURL url = agent.getUrl();

        JMXConnector connector = JMXConnectorFactory.connect(url);
        connector.connect();

        MBeanServerConnection connection = connector.getMBeanServerConnection();
        assertTrue(connection.getMBeanCount() > 0);
    }
}
