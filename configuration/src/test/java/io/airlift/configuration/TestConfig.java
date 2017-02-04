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
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.spi.Message;
import org.testng.annotations.Test;

import javax.inject.Qualifier;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import static com.google.inject.name.Names.named;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestConfig
{
    @Retention(RUNTIME)
    @Target({FIELD, PARAMETER, METHOD})
    @Qualifier
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
            .put("myEnumOption", MyEnum.FOO.toString())
            .put("valueClassOption", "a value class")
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
        assertEquals("default string", config.getStringOption());
    }

    @Test
    public void testConfigDefaultsWithAnnotationType()
    {
        Injector injector = createInjector(ImmutableMap.of(), createModule(
                Key.get(Config1.class, MyAnnotation.class), Config1.class,
                null,
                new StringOptionDefaults("default string")));

        Config1 config = injector.getInstance(Key.get(Config1.class, MyAnnotation.class));
        assertEquals("default string", config.getStringOption());
    }

    @Test
    public void testConfigDefaultsWithAnnotationObject()
    {
        Injector injector = createInjector(ImmutableMap.of(), createModule(
                Key.get(Config1.class, named("boo")), Config1.class,
                null,
                new StringOptionDefaults("default string")));

        Config1 config = injector.getInstance(Key.get(Config1.class, named("boo")));
        assertEquals("default string", config.getStringOption());
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
        assertEquals("final default string", config.getStringOption());
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
        assertEquals("a string", config.getStringOption());
        assertEquals(true, config.getBooleanOption());
        assertEquals(Boolean.TRUE, config.getBoxedBooleanOption());
        assertEquals(Byte.MAX_VALUE, config.getByteOption());
        assertEquals(Byte.valueOf(Byte.MAX_VALUE), config.getBoxedByteOption());
        assertEquals(Short.MAX_VALUE, config.getShortOption());
        assertEquals(Short.valueOf(Short.MAX_VALUE), config.getBoxedShortOption());
        assertEquals(Integer.MAX_VALUE, config.getIntegerOption());
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), config.getBoxedIntegerOption());
        assertEquals(Long.MAX_VALUE, config.getLongOption());
        assertEquals(Long.valueOf(Long.MAX_VALUE), config.getBoxedLongOption());
        assertEquals(Float.MAX_VALUE, config.getFloatOption(), 0);
        assertEquals(Float.MAX_VALUE, config.getBoxedFloatOption());
        assertEquals(Double.MAX_VALUE, config.getDoubleOption(), 0);
        assertEquals(Double.MAX_VALUE, config.getBoxedDoubleOption());
        assertEquals(MyEnum.FOO, config.getMyEnumOption());
        assertEquals(config.getValueClassOption().getValue(), "a value class");
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
            throws Exception
    {
        int globalDefaultValue = 1;
        int defaultValue = 2;
        int customValue = 3;

        Module module = binder -> {
            configBinder(binder).bindConfigGlobalDefaults(Config1.class, (config -> {
                config.setByteOption((byte) globalDefaultValue);
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
        assertEquals(config.getByteOption(), globalDefaultValue);
        assertEquals(config.getIntegerOption(), defaultValue);
        assertEquals(config.getLongOption(), customValue);
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

        assertEquals(seenBindings.size(), 3);
        assertEquals(
                ImmutableSet.copyOf(seenBindings),
                ImmutableSet.of(
                        new ConfigurationBinding<>(Key.get(Config1.class), Config1.class, Optional.empty()),
                        new ConfigurationBinding<>(Key.get(Config1.class, MyAnnotation.class), Config1.class, Optional.empty()),
                        new ConfigurationBinding<>(Key.get(AnotherConfig.class), AnotherConfig.class, Optional.empty())));
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
}
