package com.proofpoint.configuration;

import com.google.common.collect.Maps;
import com.google.inject.Binder;
import com.google.inject.ConfigurationException;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.internal.ImmutableMap;
import com.google.inject.internal.ImmutableMap.Builder;
import com.google.inject.spi.Message;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

@SuppressWarnings( { "UnnecessaryLocalVariable", "deprecation" })
public class TestConfig
{
    private ImmutableMap<String,String> properties;

    @Test
    public void testLegacy()
    {
        try
        {
            ConfigurationFactory            factory = new ConfigurationFactory(new HashMap<String, String>()).createLegacyFactory();
            LegacyPathConfig                config = factory.build(LegacyPathConfig.class);
            assertNotNull(config);
        }
        catch ( ConfigurationException e )
        {
            fail("Legacy issue", e);
        }
    }

    @Test
    public void testFoo()
            throws Exception
    {
        Map<String, String> config = Maps.newHashMap();
        config.put("hello", "world");
        config.put("thevalue", "value");

        Injector injector = createInjector(config, createLegacyModule(Thing.class));

        Thing t = injector.getInstance(Thing.class);
        assertEquals(t.getName(), "world");
    }

    @Test
    public void testDefaultValue()
            throws Exception
    {
        Map<String, String> config = Maps.newHashMap();
        Injector injector = createInjector(config, createLegacyModule(Thing.class));
        Thing t = injector.getInstance(Thing.class);
        assertEquals(t.getName(), "woof");
    }

    @Test
    public void testDefaultViaImpl()
            throws Exception
    {
        Injector injector = createInjector(Collections.<String, String>emptyMap(), createLegacyModule(LegacyConfig2.class));
        LegacyConfig2 config = injector.getInstance(LegacyConfig2.class);
        assertEquals(config.getOption(), "default");
    }

    @Test
    public void testProvidedOverridesDefault()
            throws Exception
    {
        Map<String, String> properties = Maps.newHashMap();
        properties.put("option", "provided");

        Injector injector = createInjector(properties, createLegacyModule(LegacyConfig2.class));
        LegacyConfig2 instance = injector.getInstance(LegacyConfig2.class);

        assertEquals(instance.getOption(), "provided");
    }

    @Test
    public void testMissingDefault()
            throws Exception
    {
        try {
            createInjector(Collections.<String, String>emptyMap(), createLegacyModule(LegacyConfig3.class))
                    .getInstance(LegacyConfig3.class);
            fail("Expected exception due to missing value");
        }
        catch (Exception e) {
            // do nothing
        }
    }

    @Test
    public void testDetectsAbstractMethod()
            throws Exception
    {
        try {
            Injector injector = createInjector(Collections.<String, String>emptyMap(), createLegacyModule(LegacyConfig4.class));
            injector.getInstance(LegacyConfig4.class);
            fail("Expected exception due to abstract method without @Config annotation");
        }
        catch (CreationException e) {
            // do nothing
        }
    }

    @Test
    public void testLegacyConfigTypes()
    {
        Injector injector = createInjector(properties, createLegacyModule(LegacyConfig1.class));
        LegacyConfig1 legacyConfig = injector.getInstance(LegacyConfig1.class);
        assertEquals("a string", legacyConfig.getStringOption());
        assertEquals(true, legacyConfig.getBooleanOption());
        assertEquals(Boolean.TRUE, legacyConfig.getBoxedBooleanOption());
        assertEquals(Byte.MAX_VALUE, legacyConfig.getByteOption());
        assertEquals(Byte.valueOf(Byte.MAX_VALUE), legacyConfig.getBoxedByteOption());
        assertEquals(Short.MAX_VALUE, legacyConfig.getShortOption());
        assertEquals(Short.valueOf(Short.MAX_VALUE), legacyConfig.getBoxedShortOption());
        assertEquals(Integer.MAX_VALUE, legacyConfig.getIntegerOption());
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), legacyConfig.getBoxedIntegerOption());
        assertEquals(Long.MAX_VALUE, legacyConfig.getLongOption());
        assertEquals(Long.valueOf(Long.MAX_VALUE), legacyConfig.getBoxedLongOption());
        assertEquals(Float.MAX_VALUE, legacyConfig.getFloatOption(), 0);
        assertEquals(Float.MAX_VALUE, legacyConfig.getBoxedFloatOption());
        assertEquals(Double.MAX_VALUE, legacyConfig.getDoubleOption(), 0);
        assertEquals(Double.MAX_VALUE, legacyConfig.getBoxedDoubleOption());
        assertEquals(MyEnum.FOO, legacyConfig.getMyEnumOption());
        assertEquals(legacyConfig.getValueClassOption().getValue(), "a value class");
    }

    @Test
    public void testConfig()
    {
        Injector injector = createInjector(properties, createModule(Config1.class, null, null));
        verifyConfig(injector.getInstance(Config1.class));
    }

    @Test
    public void testPrefixConfigTypes()
    {
        Injector injector = createInjector(prefix("prefix", properties), createModule(Config1.class, "prefix", null));
        verifyConfig(injector.getInstance(Config1.class));
    }

    @Test
    public void testDefaults()
    {
        Config1 defaults = new Config1();
        defaults.setStringOption("default value");
        assertEquals(defaults.getStringOption(), "default value");

        // verify defaults passed through
        Injector injector = createInjector(Collections.<String, String>emptyMap(), createModule(Config1.class, null, defaults));
        Config1 config = injector.getInstance(Config1.class);
        assertEquals(config.getStringOption(), "default value");

        // verify defaults can be overridden
        injector = createInjector(properties, createModule(Config1.class, null, defaults));
        verifyConfig(injector.getInstance(Config1.class));
    }

    private void verifyConfig(Config1 config)
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
            Injector injector = createInjector(Collections.<String, String>emptyMap(), createModule(ConfigWithNoAnnotations.class, null, null));
            injector.getInstance(ConfigWithNoAnnotations.class);
            fail("Expected exception due to missing @Config annotations");
        }
        catch (CreationException e) {
            // do nothing
        }
    }

    private Injector createInjector(Map<String, String> properties, Module module)
    {
        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties);
        List<Message> messages = new ConfigurationValidator(configurationFactory).validate(module);
        return Guice.createInjector(new ConfigurationModule(configurationFactory), module, new ValidationErrorModule(messages));
    }

    private <T> Module createLegacyModule(final Class<T> configClass)
    {
        Module module = new Module() {
            @Override
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder, configClass);
            }
        };
        return module;
    }

    private <T> Module createModule(final Class<T> configClass, final String prefix, final T defaults)
    {
        Module module = new Module() {
            @Override
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).prefixedWith(prefix).to(configClass).withDefaults(defaults);
            }
        };
        return module;
    }

    @BeforeMethod
    protected void setUp()
            throws Exception
    {
        properties = ImmutableMap.<String, String>builder()
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
    }

    private Map<String, String> prefix(String prefix, Map<String, String> properties)
    {
        Builder<String, String> builder = ImmutableMap.builder();
        for (Entry<String, String> entry : properties.entrySet()) {
            builder.put(prefix + "." + entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    public abstract static class Thing
    {
        @Config("hello")
        @Default("woof")
        public abstract String getName();
    }
}