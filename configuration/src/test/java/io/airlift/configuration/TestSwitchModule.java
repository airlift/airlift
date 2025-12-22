package io.airlift.configuration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.spi.Message;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.airlift.configuration.SwitchModule.switchModule;
import static org.assertj.core.api.Assertions.assertThat;

public class TestSwitchModule
{
    @Test
    public void testSwitchModule()
    {
        Module switchModule = switchModule(
                SomeConfig.class,
                SomeConfig::getSomeOption,
                value -> binder -> binder.bind(String.class).toInstance(value.toUpperCase(Locale.ENGLISH)));

        Injector injector = createInjector(ImmutableMap.of("someOption", "x"), switchModule);
        assertThat(injector.getInstance(String.class)).isEqualTo("X");
    }

    @Test
    public void testSwitchModuleWithPrefix()
    {
        Module switchModule = switchModule(
                SomeConfig.class,
                "prefix",
                SomeConfig::getSomeOption,
                value -> binder -> binder.bind(String.class).toInstance(value.toUpperCase(Locale.ENGLISH)));

        Injector injector = createInjector(ImmutableMap.of("prefix.someOption", "x"), switchModule);
        assertThat(injector.getInstance(String.class)).isEqualTo("X");
    }

    private static Injector createInjector(Map<String, String> properties, Module module)
    {
        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties);
        configurationFactory.registerConfigurationClasses(ImmutableList.of(module));
        List<Message> messages = configurationFactory.validateRegisteredConfigurationProvider();
        return Guice.createInjector(
                new ConfigurationModule(configurationFactory),
                module,
                new ValidationErrorModule(messages),
                Binder::requireExplicitBindings);
    }

    public static class SomeConfig
    {
        private String someOption;

        public String getSomeOption()
        {
            return someOption;
        }

        @Config("someOption")
        public SomeConfig setSomeOption(String someOption)
        {
            this.someOption = someOption;
            return this;
        }
    }
}
