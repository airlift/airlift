package com.proofpoint.configuration;

import com.google.inject.Binder;
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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

 public class TestConfig
{
    private ImmutableMap<String,String> properties;

    @Test
    public void testConfig()
    {
        Injector injector = createInjector(properties, createModule(Config1.class, null));
        verifyConfig(injector.getInstance(Config1.class));
    }

    @Test
    public void testPrefixConfigTypes()
    {
        Injector injector = createInjector(prefix("prefix", properties), createModule(Config1.class, "prefix"));
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
            Injector injector = createInjector(Collections.<String, String>emptyMap(), createModule(ConfigWithNoAnnotations.class, null));
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
        List<Message> messages = new ConfigurationValidator(configurationFactory, null).validate(module);
        return Guice.createInjector(new ConfigurationModule(configurationFactory), module, new ValidationErrorModule(messages));
    }

    private <T> Module createModule(final Class<T> configClass, final String prefix)
    {
        Module module = new Module() {
            @Override
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).prefixedWith(prefix).to(configClass);
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
}