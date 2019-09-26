package com.facebook.airlift.configuration;

import com.facebook.airlift.configuration.ConfigurationInspector.ConfigAttribute;
import com.facebook.airlift.configuration.ConfigurationInspector.ConfigRecord;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.facebook.airlift.configuration.ConfigBinder.configBinder;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Iterables.getOnlyElement;
import static org.testng.Assert.assertEquals;

public class TestConfigurationInspector
{
    @Test
    public void testDefaultValue()
    {
        List<String> defaultString = getConfigRecord().getAttributes()
                .stream()
                .map(ConfigAttribute::getDefaultValue)
                .collect(toImmutableList());
        assertEquals(defaultString, ImmutableList.of("----"));
    }

    @Test
    public void testCurrentValue()
    {
        List<String> currentString = getConfigRecord().getAttributes()
                .stream()
                .map(ConfigAttribute::getCurrentValue)
                .collect(toImmutableList());
        assertEquals(currentString, ImmutableList.of("string"));
    }

    private ConfigRecord<?> getConfigRecord()
    {
        Map<String, String> properties = ImmutableMap.of("stringValue", "string");
        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties);
        configurationFactory.registerConfigurationClasses(binder -> configBinder(binder).bindConfig(ConfigWithOptionalValue.class));
        return getOnlyElement(new ConfigurationInspector().inspect(configurationFactory));
    }

    public static class ConfigWithOptionalValue
    {
        private String stringValue;

        @Config("stringValue")
        public ConfigWithOptionalValue setStringValue(String stringValue)
        {
            this.stringValue = stringValue;
            return this;
        }

        public Optional<String> getStringValue()
        {
            return Optional.ofNullable(stringValue);
        }
    }
}
