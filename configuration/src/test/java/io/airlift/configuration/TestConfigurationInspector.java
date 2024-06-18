package io.airlift.configuration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.ConfigurationInspector.ConfigAttribute;
import io.airlift.configuration.ConfigurationInspector.ConfigRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static org.assertj.core.api.Assertions.assertThat;

public class TestConfigurationInspector
{
    @Test
    public void testDefaultValue()
    {
        List<String> defaultString = getConfigRecord().getAttributes()
                .stream()
                .map(ConfigAttribute::getDefaultValue)
                .collect(toImmutableList());
        assertThat(defaultString).isEqualTo(ImmutableList.of("----"));
    }

    @Test
    public void testCurrentValue()
    {
        List<String> currentString = getConfigRecord().getAttributes()
                .stream()
                .map(ConfigAttribute::getCurrentValue)
                .collect(toImmutableList());
        assertThat(currentString).isEqualTo(ImmutableList.of("string"));
    }

    private ConfigRecord<?> getConfigRecord()
    {
        Map<String, String> properties = ImmutableMap.of("stringValue", "string");
        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties);
        configurationFactory.registerConfigurationClasses(binder -> configBinder(binder).bindConfig(ConfigWithOptionalValue.class));
        return new ConfigurationInspector()
                .inspect(configurationFactory).stream()
                .collect(onlyElement());
    }

    public static class ConfigWithOptionalValue
    {
        private String stringValue;
        private boolean booleanValue;

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

        @Config("booleanValue")
        @ConfigHidden
        public ConfigWithOptionalValue setHiddenValue(boolean booleanValue)
        {
            this.booleanValue = booleanValue;
            return this;
        }

        public Optional<Boolean> getHiddenValue()
        {
            return Optional.ofNullable(booleanValue);
        }
    }
}
