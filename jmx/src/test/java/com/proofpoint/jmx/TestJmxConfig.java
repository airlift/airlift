package com.proofpoint.jmx;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import java.util.Map;

import static com.proofpoint.configuration.testing.ConfigAssertions.assertFullMapping;
import static com.proofpoint.configuration.testing.ConfigAssertions.assertLegacyEquivalence;
import static com.proofpoint.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static com.proofpoint.configuration.testing.ConfigAssertions.recordDefaults;

public class TestJmxConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(JmxConfig.class)
                .setEnabled(false)
                .setRmiRegistryPort(null)
                .setRmiServerPort(null));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("jmx.enabled", "true")
                .put("jmx.rmiregistry.port", "1")
                .put("jmx.rmiserver.port", "2")
                .build();

        JmxConfig expected = new JmxConfig()
                .setEnabled(true)
                .setRmiRegistryPort(1)
                .setRmiServerPort(2);

        assertFullMapping(properties, expected);
    }

    @Test
    public void testLegacyEquivalence()
    {
        assertLegacyEquivalence(JmxConfig.class, ImmutableMap.<String, String>of());
    }

}
