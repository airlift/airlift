/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.configuration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.BindingAnnotation;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.spi.Message;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Supplier;

import static com.google.inject.name.Names.named;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static io.airlift.configuration.MyEnum.BAR;
import static io.airlift.configuration.MyEnum.FOO;
import static io.airlift.configuration.SwitchModule.switchModule;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.within;

public class TestConfig
{
    @Retention(RUNTIME)
    @Target({FIELD, PARAMETER, METHOD})
    @BindingAnnotation
    public @interface MyAnnotation
    {
    }

    private final Map<String, String> properties = ImmutableMap.<String, String>builder()
            .put("stringOption", "a string")
            .put("booleanOption", "true")
            .put("boxedBooleanOption", "true")
            .put("byteOption", Byte.toString(Byte.MAX_VALUE))
            .put("boxedByteOption", Byte.toString(Byte.MAX_VALUE))
            .put("shortOption", Short.toString(Short.MAX_VALUE))
            .put("boxedShortOption", Short.toString(Short.MAX_VALUE))
            .put("integerOption", Integer.toString(Integer.MAX_VALUE))
            .put("boxedIntegerOption", Integer.toString(Integer.MAX_VALUE))
            .put("longOption", Long.toString(Long.MAX_VALUE))
            .put("boxedLongOption", Long.toString(Long.MAX_VALUE))
            .put("floatOption", Float.toString(Float.MAX_VALUE))
            .put("boxedFloatOption", Float.toString(Float.MAX_VALUE))
            .put("doubleOption", Double.toString(Double.MAX_VALUE))
            .put("boxedDoubleOption", Double.toString(Double.MAX_VALUE))
            .put("myEnumOption", "FOO")
            .put("myEnumSet", "FOO,bar")
            .put("myEnumList", "BAR,foo")
            .put("myIntegerList", "10,12,14,16")
            .put("myPathList", "/dev/null,/proc/self")
            .put("myEnumSecondOption", "bar") // lowercase
            .put("pathOption", "/dev/null")
            .put("valueClassOption", "a value class")
            .put("optionalValueClassOption", "a value class")
            .put("optionalPathOption", "/dev/null")
            .build();

    @Test
    public void testConfig()
    {
        Injector injector = createInjector(properties, createModule(Key.get(Config1.class), Config1.class, null));
        verifyConfig(injector.getInstance(Config1.class));
    }

    @Test
    public void testConfigWithAnnotationType()
    {
        Injector injector = createInjector(properties, createModule(Key.get(Config1.class, MyAnnotation.class), Config1.class, null));
        verifyConfig(injector.getInstance(Key.get(Config1.class, MyAnnotation.class)));
    }

    @Test
    public void testConfigWithAnnotationObject()
    {
        Injector injector = createInjector(properties, createModule(Key.get(Config1.class, named("boo")), Config1.class, null));
        verifyConfig(injector.getInstance(Key.get(Config1.class, named("boo"))));
    }

    @Test
    public void testPrefixConfigTypes()
    {
        Injector injector = createInjector(prefix("prefix", properties), createModule(Key.get(Config1.class), Config1.class, "prefix"));
        verifyConfig(injector.getInstance(Config1.class));
    }

    @Test
    public void testConfigDefaults()
    {
        Injector injector = createInjector(ImmutableMap.of(), createModule(
                Key.get(Config1.class), Config1.class,
                null,
                new StringOptionDefaults("default string")));

        Config1 config = injector.getInstance(Config1.class);
        assertThat("default string").isEqualTo(config.getStringOption());
    }

    @Test
    public void testConfigDefaultsWithAnnotationType()
    {
        Injector injector = createInjector(ImmutableMap.of(), createModule(
                Key.get(Config1.class, MyAnnotation.class), Config1.class,
                null,
                new StringOptionDefaults("default string")));

        Config1 config = injector.getInstance(Key.get(Config1.class, MyAnnotation.class));
        assertThat("default string").isEqualTo(config.getStringOption());
    }

    @Test
    public void testConfigDefaultsWithAnnotationObject()
    {
        Injector injector = createInjector(ImmutableMap.of(), createModule(
                Key.get(Config1.class, named("boo")), Config1.class,
                null,
                new StringOptionDefaults("default string")));

        Config1 config = injector.getInstance(Key.get(Config1.class, named("boo")));
        assertThat("default string").isEqualTo(config.getStringOption());
    }

    @Test
    public void testConfigDefaultsOverride()
    {
        Injector injector = createInjector(ImmutableMap.of(), createModule(
                Key.get(Config1.class), Config1.class,
                null,
                new StringOptionDefaults("default string"),
                new StringOptionDefaults("another default string"),
                new StringOptionDefaults("final default string")));

        Config1 config = injector.getInstance(Config1.class);
        assertThat("final default string").isEqualTo(config.getStringOption());
    }

    @Test
    public void testPropertiesOverrideDefaults()
    {
        Injector injector = createInjector(properties, createModule(
                Key.get(Config1.class), Config1.class,
                null,
                new StringOptionDefaults("default string"),
                new StringOptionDefaults("another default string"),
                new StringOptionDefaults("final default string")));
        verifyConfig(injector.getInstance(Config1.class));
    }

    private static void verifyConfig(Config1 config)
    {
        assertThat("a string").isEqualTo(config.getStringOption());
        assertThat(true).isEqualTo(config.getBooleanOption());
        assertThat(Boolean.TRUE).isEqualTo(config.getBoxedBooleanOption());
        assertThat(Byte.MAX_VALUE).isEqualTo(config.getByteOption());
        assertThat(Byte.valueOf(Byte.MAX_VALUE)).isEqualTo(config.getBoxedByteOption());
        assertThat(Short.MAX_VALUE).isEqualTo(config.getShortOption());
        assertThat(Short.valueOf(Short.MAX_VALUE)).isEqualTo(config.getBoxedShortOption());
        assertThat(Integer.MAX_VALUE).isEqualTo(config.getIntegerOption());
        assertThat(Integer.valueOf(Integer.MAX_VALUE)).isEqualTo(config.getBoxedIntegerOption());
        assertThat(Long.MAX_VALUE).isEqualTo(config.getLongOption());
        assertThat(Long.valueOf(Long.MAX_VALUE)).isEqualTo(config.getBoxedLongOption());
        assertThat(Float.MAX_VALUE).isCloseTo(config.getFloatOption(), within(0f));
        assertThat(Float.MAX_VALUE).isEqualTo(config.getBoxedFloatOption());
        assertThat(Double.MAX_VALUE).isCloseTo(config.getDoubleOption(), within(0.));
        assertThat(Double.MAX_VALUE).isEqualTo(config.getBoxedDoubleOption());
        assertThat(FOO).isEqualTo(config.getMyEnumOption());
        assertThat(BAR).isEqualTo(config.getMyEnumSecondOption());
        assertThat(Paths.get("/dev/null")).isEqualTo(config.getPathOption());
        assertThat(config.getValueClassOption().getValue()).isEqualTo("a value class");
        assertThat(config.getMyEnumList()).isEqualTo(ImmutableList.of(BAR, FOO));
        assertThat(config.getMyEnumSet()).isEqualTo(ImmutableSet.of(BAR, FOO));
        assertThat(config.getMyIntegerList()).isEqualTo(ImmutableList.of(10, 12, 14, 16));
        assertThat(config.getMyPathList()).isEqualTo(ImmutableList.of(Paths.get("/dev/null"), Paths.get("/proc/self")));
        assertThat(config.getOptionalValueClassOption())
                .isPresent()
                .hasValueSatisfying(value -> assertThat(value.getValue())
                        .isEqualTo("a value class"));
        assertThat(config.getOptionalPathOption())
                .contains(Paths.get("/dev/null"));
    }

    @Test
    public void testDetectsNoConfigAnnotations()
    {
        try {
            Injector injector = createInjector(Collections.<String, String>emptyMap(), createModule(Key.get(ConfigWithNoAnnotations.class), ConfigWithNoAnnotations.class, null));
            injector.getInstance(ConfigWithNoAnnotations.class);
            fail("Expected exception due to missing @Config annotations");
        }
        catch (CreationException e) {
            // do nothing
        }
    }

    @Test
    public void testConfigGlobalDefaults()
    {
        byte globalDefaultValue = 1;
        int defaultValue = 2;
        int customValue = 3;

        Module module = binder -> {
            configBinder(binder).bindConfigGlobalDefaults(Config1.class, (config -> {
                config.setByteOption(globalDefaultValue);
                config.setIntegerOption(globalDefaultValue);
                config.setLongOption(globalDefaultValue);
            }));
            configBinder(binder).bindConfigDefaults(Config1.class, MyAnnotation.class, (config -> {
                config.setIntegerOption(defaultValue);
                config.setLongOption(defaultValue);
            }));
            configBinder(binder).bindConfig(Config1.class, MyAnnotation.class);
        };

        Injector injector = createInjector(ImmutableMap.of("longOption", "" + customValue), module);

        Config1 config = injector.getInstance(Key.get(Config1.class, MyAnnotation.class));
        assertThat(config.getByteOption()).isEqualTo(globalDefaultValue);
        assertThat(config.getIntegerOption()).isEqualTo(defaultValue);
        assertThat(config.getLongOption()).isEqualTo(customValue);
    }

    @Test
    public void testConfigurationBindingListener()
    {
        List<ConfigurationBinding<?>> seenBindings = new ArrayList<>();
        Module module = binder -> {
            ConfigBinder configBinder = configBinder(binder);
            configBinder.bindConfig(AnotherConfig.class);

            configBinder.bindConfigurationBindingListener((configurationBinding, callbackConfigBinder) -> {
                seenBindings.add(configurationBinding);
                callbackConfigBinder.bindConfig(Config1.class);
                callbackConfigBinder.bindConfig(Config1.class, MyAnnotation.class);
            });
        };
        Injector injector = createInjector(properties, module);

        verifyConfig(injector.getInstance(Config1.class));
        verifyConfig(injector.getInstance(Key.get(Config1.class, MyAnnotation.class)));

        assertThat(seenBindings).hasSize(3);
        assertThat(ImmutableSet.copyOf(seenBindings)).isEqualTo(ImmutableSet.of(
                new ConfigurationBinding<>(Key.get(Config1.class), Config1.class, Optional.empty()),
                new ConfigurationBinding<>(Key.get(Config1.class, MyAnnotation.class), Config1.class, Optional.empty()),
                new ConfigurationBinding<>(Key.get(AnotherConfig.class), AnotherConfig.class, Optional.empty())));
    }

    @Test
    public void testSwitchModule()
    {
        Module a = binder -> binder.bind(String.class).toInstance("used value a");
        Module b = binder -> binder.bind(String.class).toInstance("used value b");

        Supplier<Module> module = () -> new AbstractConfigurationAwareModule()
        {
            @Override
            protected void setup(Binder binder)
            {
                ConfigBinder configBinder = configBinder(binder);
                configBinder.bindConfig(SwitchConfig.class);

                install(switchModule(
                        SwitchConfig.class,
                        SwitchConfig::getValue,
                        value -> {
                            switch (value) {
                                case A:
                                    return a;
                                case B:
                                    return b;
                                default:
                                    throw new RuntimeException("Not supported value: " + value);
                            }
                        }));
            }
        };

        assertThat(createInjector(ImmutableMap.of("value", "a"), module.get()).getInstance(String.class)).isEqualTo("used value a");
        assertThat(createInjector(ImmutableMap.of("value", "b"), module.get()).getInstance(String.class)).isEqualTo("used value b");
        assertThatThrownBy(() -> createInjector(ImmutableMap.of("value", "c"), module.get()))
                .hasStackTraceContaining("Not supported value: C");
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

    @SafeVarargs
    private static <T> Module createModule(Key<T> key, Class<T> configClass, String prefix, ConfigDefaults<T>... configDefaults)
    {
        Module module = binder -> {
            ConfigBinder configBinder = configBinder(binder);

            configBinder.bindConfig(key, configClass, prefix);

            for (ConfigDefaults<T> configDefault : configDefaults) {
                configBinder.bindConfigDefaults(key, configDefault);
            }
        };

        return module;
    }

    private static Map<String, String> prefix(String prefix, Map<String, String> properties)
    {
        Builder<String, String> builder = ImmutableMap.builder();
        for (Entry<String, String> entry : properties.entrySet()) {
            builder.put(prefix + "." + entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    private static class StringOptionDefaults
            implements ConfigDefaults<Config1>
    {
        private final String stringOptionDefault;

        private StringOptionDefaults(String stringOptionDefault)
        {
            this.stringOptionDefault = stringOptionDefault;
        }

        @Override
        public void setDefaults(Config1 config)
        {
            config.setStringOption(stringOptionDefault);
        }
    }

    public static class AnotherConfig
    {
        private String stringOption;

        public String getStringOption()
        {
            return stringOption;
        }

        @Config("stringOption")
        public AnotherConfig setStringOption(String stringOption)
        {
            this.stringOption = stringOption;
            return this;
        }
    }

    public static class SwitchConfig
            implements SwitchInterface
    {
        SwitchValue value;

        @Override
        public SwitchValue getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(SwitchValue value)
        {
            this.value = value;
        }
    }

    public interface SwitchInterface
    {
        SwitchValue getValue();
    }

    public enum SwitchValue
    {
        A, B, C
    }
}
