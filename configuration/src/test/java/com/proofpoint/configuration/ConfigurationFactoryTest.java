package com.proofpoint.configuration;

import com.google.inject.Binder;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.spi.Message;
import com.proofpoint.testing.Assertions;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ConfigurationFactoryTest
{

    @Test
    public void testAnnotatedGettersThrows()
    {
        Map<String, String> properties = new TreeMap<String, String>();
        properties.put("string-value", "some value");
        properties.put("boolean-value", "true");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, monitor, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(AnnotatedGetter.class);
                }
            });

            Assert.fail("Expected an exception in object creation due to conflicting configuration");
        } catch (CreationException e) {
            monitor.assertNumberOfErrors(2);
            Assertions.assertContainsAllOf(e.getMessage(), "not a valid setter", "getStringValue") ;
            Assertions.assertContainsAllOf(e.getMessage(), "not a valid setter", "isBooleanValue") ;
        }
    }

    @Test
    public void testAnnotatedSetters()
    {
        Map<String, String> properties = new TreeMap<String, String>();
        properties.put("string-value", "some value");
        properties.put("boolean-value", "true");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, monitor, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(AnnotatedSetter.class);
            }
        });
        AnnotatedSetter annotatedSetter = injector.getInstance(AnnotatedSetter.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        Assert.assertNotNull(annotatedSetter);
        Assert.assertEquals(annotatedSetter.getStringValue(), "some value");
        Assert.assertEquals(annotatedSetter.isBooleanValue(), true);
    }

    @Test
    public void testConfigurationDespiteDeprecatedConfig()
    {
        Map<String, String> properties = new TreeMap<String, String>();
        properties.put("string-a", "this is a");
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, monitor, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(LegacyConfigPresent.class);
            }
        });
        LegacyConfigPresent legacyConfigPresent = injector.getInstance(LegacyConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        Assert.assertNotNull(legacyConfigPresent);
        Assert.assertEquals(legacyConfigPresent.getStringA(), "this is a");
        Assert.assertEquals(legacyConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationThroughDeprecatedConfig()
    {
        Map<String, String> properties = new TreeMap<String, String>();
        properties.put("string-value", "this is a");
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, monitor, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(LegacyConfigPresent.class);
            }
        });
        LegacyConfigPresent legacyConfigPresent = injector.getInstance(LegacyConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("string-value", "replaced", "Use 'string-a'");
        Assert.assertNotNull(legacyConfigPresent);
        Assert.assertEquals(legacyConfigPresent.getStringA(), "this is a");
        Assert.assertEquals(legacyConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationWithRedundantDeprecatedConfig()
    {
        Map<String, String> properties = new TreeMap<String, String>();
        properties.put("string-value", "this is a");
        properties.put("string-a", "this is a");
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, monitor, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(LegacyConfigPresent.class);
            }
        });
        LegacyConfigPresent legacyConfigPresent = injector.getInstance(LegacyConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("string-value", "replaced", "Use 'string-a'");
        Assert.assertNotNull(legacyConfigPresent);
        Assert.assertEquals(legacyConfigPresent.getStringA(), "this is a");
        Assert.assertEquals(legacyConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationWithConflictingDeprecatedConfigThrows()
    {
        Map<String, String> properties = new TreeMap<String, String>();
        properties.put("string-value", "this is the old value");
        properties.put("string-a", "this is a");
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, monitor, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(LegacyConfigPresent.class);
                }
            });

            Assert.fail("Expected an exception in object creation due to conflicting configuration");
        } catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(1);
            monitor.assertMatchingWarningRecorded("string-value", "replaced", "Use 'string-a'");
            Assertions.assertContainsAllOf(e.getMessage(), "string-value", "conflicts with property", "string-a") ;
        }
    }

    private Injector createInjector(Map<String, String> properties, TestMonitor monitor, Module module)
    {
        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties, monitor);
        List<Message> messages = new ConfigurationValidator(configurationFactory).validate(module);
        return Guice.createInjector(new ConfigurationModule(configurationFactory), module, new ValidationErrorModule(messages));
    }


    public static class AnnotatedGetter {
        private String stringValue;
        private boolean booleanValue;

        @Config("string-value")
        public String getStringValue()
        {
            return stringValue;
        }

        public void setStringValue(String stringValue)
        {
            this.stringValue = stringValue;
        }

        @Config("boolean-value")
        public boolean isBooleanValue()
        {
            return booleanValue;
        }

        public void setBooleanValue(boolean booleanValue)
        {
            this.booleanValue = booleanValue;
        }
    }
    
    public static class AnnotatedSetter {
        private String stringValue;
        private boolean booleanValue;

        public String getStringValue()
        {
            return stringValue;
        }

        @Config("string-value")
        public void setStringValue(String stringValue)
        {
            this.stringValue = stringValue;
        }

        public boolean isBooleanValue()
        {
            return booleanValue;
        }

        @Config("boolean-value")
        public void setBooleanValue(boolean booleanValue)
        {
            this.booleanValue = booleanValue;
        }
    }

    public static class LegacyConfigPresent
    {
        private String stringA = "defaultA";
        private String stringB = "defaultB";

        public String getStringA()
        {
            return stringA;
        }

        @Config("string-a")
        @LegacyConfig("string-value")
        public void setStringA(String stringValue)
        {
            this.stringA = stringValue;
        }

        public String getStringB()
        {
            return stringB;
        }

        @Config("string-b")
        public void setStringB(String stringValue)
        {
            this.stringB = stringValue;
        }
    }
}
