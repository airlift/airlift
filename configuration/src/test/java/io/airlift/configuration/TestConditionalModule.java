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
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class TestConditionalModule
{
    @Test
    public void testConditionalModule()
    {
        Supplier<AbstractConfigurationAwareModule> moduleXY = () -> {
            Module moduleX = ConditionalModule.conditionalModule(SomeConfig.class, config -> config.getSomeOption().equals("x"), binder -> binder.bind(String.class).toInstance("X"));
            Module moduleY = ConditionalModule.conditionalModule(SomeConfig.class, config -> config.getSomeOption().equals("y"), binder -> binder.bind(String.class).toInstance("Y"));
            return new AbstractConfigurationAwareModule()
            {
                @Override
                protected void setup(Binder binder)
                {
                    install(moduleX);
                    install(moduleY);
                }
            };
        };

        Supplier<Module> moduleXElseZ = () -> ConditionalModule.conditionalModule(SomeConfig.class,
                config -> config.getSomeOption().equals("x"),
                binder -> binder.bind(String.class).toInstance("X"),
                binder -> binder.bind(String.class).toInstance("Z"));

        Injector injector;
        injector = createInjector(ImmutableMap.of("someOption", "x"), moduleXY.get());
        assertThat(injector.getInstance(String.class)).isEqualTo("X");

        injector = createInjector(ImmutableMap.of("someOption", "y"), moduleXY.get());
        assertThat(injector.getInstance(String.class)).isEqualTo("Y");

        injector = createInjector(ImmutableMap.of("someOption", "x"), moduleXElseZ.get());
        assertThat(injector.getInstance(String.class)).isEqualTo("X");

        injector = createInjector(ImmutableMap.of("someOption", "v"), moduleXElseZ.get());
        assertThat(injector.getInstance(String.class)).isEqualTo("Z");
    }

    @Test
    public void testConditionalModuleWithPrefix()
    {
        Supplier<AbstractConfigurationAwareModule> moduleXY = () -> {
            Module moduleX = ConditionalModule.conditionalModule(SomeConfig.class, "prefix", config -> config.getSomeOption().equals("x"), binder -> binder.bind(String.class).toInstance("X"));
            Module moduleY = ConditionalModule.conditionalModule(SomeConfig.class, "prefix", config -> config.getSomeOption().equals("y"), binder -> binder.bind(String.class).toInstance("Y"));
            return new AbstractConfigurationAwareModule()
            {
                @Override
                protected void setup(Binder binder)
                {
                    install(moduleX);
                    install(moduleY);
                }
            };
        };
        Supplier<Module> moduleXElseZ = () -> ConditionalModule.conditionalModule(SomeConfig.class,
                "prefix",
                config -> config.getSomeOption().equals("x"),
                binder -> binder.bind(String.class).toInstance("X"),
                binder -> binder.bind(String.class).toInstance("Z"));

        Injector injector;
        injector = createInjector(ImmutableMap.of("prefix.someOption", "x"), moduleXY.get());
        assertThat(injector.getInstance(String.class)).isEqualTo("X");

        injector = createInjector(ImmutableMap.of("prefix.someOption", "y"), moduleXY.get());
        assertThat(injector.getInstance(String.class)).isEqualTo("Y");

        injector = createInjector(ImmutableMap.of("prefix.someOption", "x"), moduleXElseZ.get());
        assertThat(injector.getInstance(String.class)).isEqualTo("X");

        injector = createInjector(ImmutableMap.of("prefix.someOption", "v"), moduleXElseZ.get());
        assertThat(injector.getInstance(String.class)).isEqualTo("Z");
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
