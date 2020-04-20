package io.airlift.jmx;

import com.google.common.net.HostAndPort;
import org.testng.SkipException;
import org.testng.annotations.Test;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestJmxAgent
{
    @Test
    public void testJava8Agent()
            throws Exception
    {
        if (JavaVersion.current().getMajor() > 8) {
            throw new SkipException("Incompatible Java version: " + JavaVersion.current());
        }

        HostAndPort address = JmxAgent8.getRunningAgentAddress(null, null);

        JmxAgent agent = new JmxAgent8(new JmxConfig().setRmiRegistryPort(8012));
        if (address == null) {
            // if agent wasn't running, it must have been started by the instantiation of JmxAgent
            address = JmxAgent8.getRunningAgentAddress(null, null);
            assertNotNull(address);
        }

        JMXServiceURL url = agent.getUrl();

        assertEquals(url.toString(), format("service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi", address.getHost(), address.getPort()));

        JMXConnector connector = JMXConnectorFactory.connect(url);
        connector.connect();
    }

    @Test
    public void testJava9Agent()
            throws Exception
    {
        if (JavaVersion.current().getMajor() < 9) {
            throw new SkipException("Incompatible Java version: " + JavaVersion.current());
        }

        JmxAgent agent = new JmxAgent9(new JmxConfig().setRmiRegistryPort(8012));

        JMXServiceURL url = agent.getUrl();

        assertThat(url.toString()).matches("service:jmx:rmi:///jndi/rmi://.*:\\d+/jmxrmi");

        JMXConnector connector = JMXConnectorFactory.connect(url);
        connector.connect();
    }
}
