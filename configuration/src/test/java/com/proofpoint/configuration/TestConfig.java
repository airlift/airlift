package com.proofpoint.configuration;

import com.google.inject.Binder;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import org.testng.annotations.Test;

import java.util.Properties;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestConfig
{
    @Test
    public void testFoo()
            throws Exception
    {
        Properties c = new Properties();
        c.put("hello", "world");
        c.put("thevalue", "value");

        Injector injector = createInjector(c, Thing.class);

        Thing t = injector.getInstance(Thing.class);
        assertEquals(t.getName(), "world");
    }

    @Test
    public void testDefaultValue()
            throws Exception
    {
        Properties c = new Properties();
        Injector injector = createInjector(c, Thing.class);
        Thing t = injector.getInstance(Thing.class);
        assertEquals(t.getName(), "woof");
    }

    @Test
    public void testDefaultViaImpl()
            throws Exception
    {
        Injector injector = createInjector(new Properties(), Config2.class);
        Config2 config = injector.getInstance(Config2.class);
        assertEquals(config.getOption(), "default");
    }

    @Test
    public void testProvidedOverridesDefault()
            throws Exception
    {
        Properties properties = new Properties();
        properties.put("option", "provided");

        Injector injector = createInjector(properties, Config2.class);
        Config2 config = injector.getInstance(Config2.class);
        
        assertEquals(config.getOption(), "provided");
    }

    @Test
    public void testMissingDefault()
            throws Exception
    {
        try {
            createInjector(new Properties(), Config3.class).getInstance(Config3.class);
            fail("Expected exception due to missing value");
        }
        catch (Exception e) {
        }
    }

    @Test
    public void testDetectsAbstractMethod()
            throws Exception
    {
        try {
            createInjector(new Properties(), Config4.class).getInstance(Config4.class);
            fail("Expected exception due to abstract method without @Config annotation");
        }
        catch (CreationException e) {
        }
    }

    @Test
    public void testTypes()
    {
        Properties c = new Properties()
        {{
                setProperty("stringOption", "a string");
                setProperty("booleanOption", "true");
                setProperty("boxedBooleanOption", "true");
                setProperty("byteOption", Byte.toString(Byte.MAX_VALUE));
                setProperty("boxedByteOption", Byte.toString(Byte.MAX_VALUE));
                setProperty("shortOption", Short.toString(Short.MAX_VALUE));
                setProperty("boxedShortOption", Short.toString(Short.MAX_VALUE));
                setProperty("integerOption", Integer.toString(Integer.MAX_VALUE));
                setProperty("boxedIntegerOption", Integer.toString(Integer.MAX_VALUE));
                setProperty("longOption", Long.toString(Long.MAX_VALUE));
                setProperty("boxedLongOption", Long.toString(Long.MAX_VALUE));
                setProperty("floatOption", Float.toString(Float.MAX_VALUE));
                setProperty("boxedFloatOption", Float.toString(Float.MAX_VALUE));
                setProperty("doubleOption", Double.toString(Double.MAX_VALUE));
                setProperty("boxedDoubleOption", Double.toString(Double.MAX_VALUE));
            }};

        Injector injector = createInjector(c, Config1.class);
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
        assertEquals(Float.valueOf(Float.MAX_VALUE), config.getBoxedFloatOption());
        assertEquals(Double.MAX_VALUE, config.getDoubleOption(), 0);
        assertEquals(Double.valueOf(Double.MAX_VALUE), config.getBoxedDoubleOption());
    }

    private Injector createInjector(Properties c, final Class<?>... configClasses)
    {
        Injector injector = Guice.createInjector(new ConfigurationModule(c, new Module()
        {
            public void configure(Binder binder)
            {
                for (Class<?> configClass : configClasses) {
                    ConfigurationModule.bindConfig(binder, configClass);
                }
            }
        }));

        return injector;
    }
    
    public abstract static class Thing
    {
        @Config("hello")
        @Default("woof")
        public abstract String getName();
    }
}