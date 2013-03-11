package com.proofpoint.log;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import com.proofpoint.units.DataSize;
import com.proofpoint.units.DataSize.Unit;
import org.testng.annotations.Test;

import java.util.Map;

public class TestLoggingConfiguration
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(LoggingConfiguration.class)
                .setConsoleEnabled(true)
                .setLogPath(null)
                .setMaxSegmentSize(new DataSize(100, Unit.MEGABYTE))
                .setMaxHistory(30)
                .setLevelsFile(null)
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("log.enable-console", "false")
                .put("log.output-file", "var/log/foo.log")
                .put("log.max-size", "1GB")
                .put("log.max-history", "25")
                .put("log.levels-file", "var/log/log-levels-test.cfg")
                .build();

        LoggingConfiguration expected = new LoggingConfiguration()
                .setConsoleEnabled(false)
                .setLogPath("var/log/foo.log")
                .setMaxSegmentSize(new DataSize(1, Unit.GIGABYTE))
                .setMaxHistory(25)
                .setLevelsFile("var/log/log-levels-test.cfg");

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testDeprecatedProperties()
    {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .put("log.max-size", "300B")
                .build();

        Map<String, String> oldProperties = new ImmutableMap.Builder<String, String>()
                .put("log.max-size-in-bytes", "300")
                .build();

        ConfigAssertions.assertDeprecatedEquivalence(LoggingConfiguration.class, currentProperties, oldProperties);
    }

}
