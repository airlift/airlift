package io.airlift.jmx;

import com.google.common.net.HostAndPort;
import org.testng.annotations.Test;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import static java.lang.String.format;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestJmxAgent
{
    @Test
    public void testSanity()
            throws Exception
    {
        HostAndPort address = JmxAgent.getRunningAgentAddress(null, null);

        JmxAgent agent = new JmxAgent(new JmxConfig());
        if (address == null) {
            // if agent wasn't running, it must have been started by the instantiation of JmxAgent
            address = JmxAgent.getRunningAgentAddress(null, null);
            assertNotNull(address);
        }

        JMXServiceURL url = agent.getUrl();

        assertEquals(url.toString(), format("service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi", address.getHostText(), address.getPort()));

        JMXConnector connector = JMXConnectorFactory.connect(url);
        connector.connect();

        MBeanServerConnection connection = connector.getMBeanServerConnection();
        assertTrue(connection.getMBeanCount() > 0);
    }
}
