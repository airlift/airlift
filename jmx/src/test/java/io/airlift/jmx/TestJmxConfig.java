package io.airlift.jmx;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import java.util.Map;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;

public class TestJmxConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(JmxConfig.class)
                .setRmiEnabled(true)
                .setRmiRegistryPort(null)
                .setRmiServerPort(null));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("jmx.rmi.enabled", "false")
                .put("jmx.rmiregistry.port", "1")
                .put("jmx.rmiserver.port", "2")
                .build();

        JmxConfig expected = new JmxConfig()
                .setRmiEnabled(false)
                .setRmiRegistryPort(1)
                .setRmiServerPort(2);

        assertFullMapping(properties, expected);
    }

}
