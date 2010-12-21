package com.proofpoint.configuration;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.spi.Message;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.testng.Assert;
import org.testng.annotations.Test;

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
        AnnotatedSetter AnnotatedSetter = injector.getInstance(AnnotatedSetter.class);
        Assert.assertNotNull(AnnotatedSetter);
        Assert.assertEquals(AnnotatedSetter.getStringValue(), "some value");
        Assert.assertEquals(AnnotatedSetter.isBooleanValue(), true);
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
}
