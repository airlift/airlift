package io.airlift.jmx;

import static org.assertj.core.api.Assertions.assertThat;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import org.junit.jupiter.api.Test;

public class TestJmxAgent {
    @Test
    public void testAgent() throws Exception {
        JmxAgent agent = new JmxAgent(new JmxConfig().setRmiRegistryPort(8012));

        JMXServiceURL url = agent.getUrl();

        assertThat(url.toString()).matches("service:jmx:rmi:///jndi/rmi://.*:\\d+/jmxrmi");

        JMXConnector connector = JMXConnectorFactory.connect(url);
        connector.connect();
    }
}
