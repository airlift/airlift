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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ConfigurationFactoryTest
{

    class TestMonitor implements Problems.Monitor
    {
        private List<Message> errors = new ArrayList<Message>();
        private List<Message> warnings = new ArrayList<Message>();

        @Override
        public void onError(Message error)
        {
            errors.add(error);
        }

        @Override
        public void onWarning(Message warning)
        {
            warnings.add(warning);
        }

        public void assertNumberOfErrors(int expected)
        {
            Assert.assertEquals(errors.size(), expected, "Number of errors is incorrect");
        }

        public void assertNumberOfWarnings(int expected)
        {
            Assert.assertEquals(warnings.size(), expected, "Number of warnings is incorrect");
        }

        private void assertMatchingWarningRecorded(String... parts)
        {
            for (Message warning : warnings) {
                boolean matched = true;
                for (String part : parts) {
                    if (!warning.getMessage().contains(part)) {
                        matched = false;
                    }
                }
                if (matched) {
                    return;
                }
            }
            Assert.fail("Expected message not found in monitor warning list");
        }
    }

    @Test
    public void testAnnotatedGetters()
    {
        Map<String, String> properties = new TreeMap<String, String>();
        properties.put("string-value", "some value");
        properties.put("boolean-value", "true");
        TestMonitor monitor = new TestMonitor();

        Injector injector = createInjector(properties, monitor, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(AnnotatedGetter.class);
            }
        });
        AnnotatedGetter annotatedGetter = injector.getInstance(AnnotatedGetter.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
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
                ConfigurationModule.bindConfig(binder).to(DeprecatedConfigPresent.class);
            }
        });
        DeprecatedConfigPresent deprecatedConfigPresent = injector.getInstance(DeprecatedConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
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
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, monitor, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(DeprecatedConfigPresent.class);
            }
        });
        DeprecatedConfigPresent deprecatedConfigPresent = injector.getInstance(DeprecatedConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("string-value", "deprecated", "Use 'string-a'");
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
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, monitor, new Module()
        {
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(DeprecatedConfigPresent.class);
            }
        });
        DeprecatedConfigPresent deprecatedConfigPresent = injector.getInstance(DeprecatedConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("string-value", "deprecated", "Use 'string-a'");
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
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, monitor, new Module()
            {
                public void configure(Binder binder)
                {
                    ConfigurationModule.bindConfig(binder).to(DeprecatedConfigPresent.class);
                }
            });

            Assert.fail("Expected an exception in object creation due to conflicting configuration");
        } catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(1);
            monitor.assertMatchingWarningRecorded("string-value", "deprecated", "Use 'string-a'");
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

    public static class DeprecatedConfigPresent
    {
        private String stringA = "defaultA";
        private String stringB = "defaultB";

        public String getStringA()
        {
            return stringA;
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
