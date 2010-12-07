package com.proofpoint.configuration;

import com.google.common.collect.Maps;
import com.google.inject.Binder;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.spi.Message;
import com.proofpoint.configuration.MyEnum;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Map;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestConfig
{
    @Test
    public void testFoo()
            throws Exception
    {
        Map<String, String> config = Maps.newHashMap();
        config.put("hello", "world");
        config.put("thevalue", "value");

        Injector injector = createInjector(config, Thing.class);

        Thing t = injector.getInstance(Thing.class);
        assertEquals(t.getName(), "world");
    }

    @Test
    public void testDefaultValue()
            throws Exception
    {
        Map<String, String> config = Maps.newHashMap();
        Injector injector = createInjector(config, Thing.class);
        Thing t = injector.getInstance(Thing.class);
        assertEquals(t.getName(), "woof");
    }

    @Test
    public void testDefaultViaImpl()
            throws Exception
    {
        Injector injector = createInjector(Collections.<String, String>emptyMap(), LegacyConfig2.class);
        LegacyConfig2 config = injector.getInstance(LegacyConfig2.class);
        assertEquals(config.getOption(), "default");
    }

    @Test
    public void testProvidedOverridesDefault()
            throws Exception
    {
        Map<String, String> properties = Maps.newHashMap();
        properties.put("option", "provided");

        Injector injector = createInjector(properties, LegacyConfig2.class);
        LegacyConfig2 instance = injector.getInstance(LegacyConfig2.class);

        assertEquals(instance.getOption(), "provided");
    }

    @Test
    public void testMissingDefault()
            throws Exception
    {
        try {
            createInjector(Collections.<String, String>emptyMap(), LegacyConfig3.class)
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
            Injector injector = createInjector(Collections.<String, String>emptyMap(), LegacyConfig4.class);
            injector.getInstance(LegacyConfig4.class);
            fail("Expected exception due to abstract method without @Config annotation");
        }
        catch (CreationException e) {
            // do nothing
        }
    }

    @Test
    public void testTypes()
    {
        Map<String, String> properties = Maps.newHashMap();
        properties.put("stringOption", "a string");
        properties.put("booleanOption", "true");
        properties.put("boxedBooleanOption", "true");
        properties.put("byteOption", Byte.toString(Byte.MAX_VALUE));
        properties.put("boxedByteOption", Byte.toString(Byte.MAX_VALUE));
        properties.put("shortOption", Short.toString(Short.MAX_VALUE));
        properties.put("boxedShortOption", Short.toString(Short.MAX_VALUE));
        properties.put("integerOption", Integer.toString(Integer.MAX_VALUE));
        properties.put("boxedIntegerOption", Integer.toString(Integer.MAX_VALUE));
        properties.put("longOption", Long.toString(Long.MAX_VALUE));
        properties.put("boxedLongOption", Long.toString(Long.MAX_VALUE));
        properties.put("floatOption", Float.toString(Float.MAX_VALUE));
        properties.put("boxedFloatOption", Float.toString(Float.MAX_VALUE));
        properties.put("doubleOption", Double.toString(Double.MAX_VALUE));
        properties.put("boxedDoubleOption", Double.toString(Double.MAX_VALUE));
        properties.put("myEnumOption", MyEnum.FOO.toString());
        properties.put("valueClassOption", "a value class");

        Injector injector = createInjector(properties, LegacyConfig1.class);
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

        injector = createInjector(properties, Config1.class);
        Config1 config = injector.getInstance(Config1.class);
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
            Injector injector = createInjector(Collections.<String, String>emptyMap(), ConfigWithNoAnnotations.class);
            injector.getInstance(ConfigWithNoAnnotations.class);
            fail("Expected exception due to missing @Config annotations");
        }
        catch (CreationException e) {
            // do nothing
        }
    }

    private Injector createInjector(Map<String, String> properties, final Class<?>... configClasses)
    {
        Module module = new Module() {
            @Override
            public void configure(Binder binder)
            {
                for ( Class<?> configClass : configClasses ) {
                    ConfigurationModule.bindConfig(binder, configClass);
                }
            }
        };
        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties);
        List<Message> messages = new ConfigurationValidator(configurationFactory).validate(module);
        return Guice.createInjector(new ConfigurationModule(configurationFactory), module, new ValidationErrorModule(messages));
    }
    
    public abstract static class Thing
    {
        @Config("hello")
        @Default("woof")
        public abstract String getName();
    }
}