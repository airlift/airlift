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
    public void testAnnotatedGetters()
    {
        Map<String, String> properties = new TreeMap<String, String>();
        properties.put("string-value", "some value");
        properties.put("boolean-value", "true");
        Injector injector = createInjector(properties, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(AnnotatedGetter.class);
            }
        });
        AnnotatedGetter annotatedGetter = injector.getInstance(AnnotatedGetter.class);
        Assert.assertNotNull(annotatedGetter);
        Assert.assertEquals(annotatedGetter.getStringValue(), "some value");
        Assert.assertEquals(annotatedGetter.isBooleanValue(), true);
    }

    @Test
    public void testAnnotatedSetters()
    {
        Map<String, String> properties = new TreeMap<String, String>();
        properties.put("string-value", "some value");
        properties.put("boolean-value", "true");
        Injector injector = createInjector(properties, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(AnnotatedSetter.class);
            }
        });
        AnnotatedSetter annotatedSetter = injector.getInstance(AnnotatedSetter.class);
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
        Injector injector = createInjector(properties, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(DeprecatedConfigPresent.class);
            }
        });
        DeprecatedConfigPresent deprecatedConfigPresent = injector.getInstance(DeprecatedConfigPresent.class);
        Assert.assertNotNull(deprecatedConfigPresent);
        Assert.assertEquals(deprecatedConfigPresent.getStringA(), "this is a");
        Assert.assertEquals(deprecatedConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationThroughDeprecatedConfig()
    {
        Map<String, String> properties = new TreeMap<String, String>();
        properties.put("string-value", "this is a");
        properties.put("string-b", "this is b");
        Injector injector = createInjector(properties, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(DeprecatedConfigPresent.class);
            }
        });
        DeprecatedConfigPresent deprecatedConfigPresent = injector.getInstance(DeprecatedConfigPresent.class);
        Assert.assertNotNull(deprecatedConfigPresent);
        Assert.assertEquals(deprecatedConfigPresent.getStringA(), "this is a");
        Assert.assertEquals(deprecatedConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationWithRedundantDeprecatedConfig()
    {
        Map<String, String> properties = new TreeMap<String, String>();
        properties.put("string-value", "this is a");
        properties.put("string-a", "this is a");
        properties.put("string-b", "this is b");
        Injector injector = createInjector(properties, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(DeprecatedConfigPresent.class);
            }
        });
        DeprecatedConfigPresent deprecatedConfigPresent = injector.getInstance(DeprecatedConfigPresent.class);
        Assert.assertNotNull(deprecatedConfigPresent);
        Assert.assertEquals(deprecatedConfigPresent.getStringA(), "this is a");
        Assert.assertEquals(deprecatedConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationWithConflictingDeprecatedConfigThrows()
    {
        Map<String, String> properties = new TreeMap<String, String>();
        properties.put("string-value", "this is the old value");
        properties.put("string-a", "this is a");
        properties.put("string-b", "this is b");
        try {
            createInjector(properties, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(DeprecatedConfigPresent.class);
                }
            });

            Assert.fail("Expected an exception in object creation due to conflicting configuration");
        } catch (CreationException e) {
            Assertions.assertContainsAllOf(e.getMessage(), "string-value", "conflicts with property", "string-a") ;
        }
    }

    private Injector createInjector(Map<String, String> properties, Module module)
    {
        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties);
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

    public static class DeprecatedConfigPresent
    {
        private String stringA;
        private String stringB;

        public String getStringA()
        {
            return stringA;
        }

        public DeprecatedConfigPresent()
        {
            this.stringA = "defaultA";
            this.stringB = "defaultB";
        }

        @Config("string-a")
        @DeprecatedConfig("string-value")
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
