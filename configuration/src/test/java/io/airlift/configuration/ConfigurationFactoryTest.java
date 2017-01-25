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
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.spi.Message;
import io.airlift.testing.Assertions;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static io.airlift.configuration.ConfigBinder.configBinder;

public class ConfigurationFactoryTest
{
    @Test
    public void testAnnotatedGettersThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-value", "some value");
        properties.put("boolean-value", "true");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, monitor, binder -> configBinder(binder).bindConfig(AnnotatedGetter.class));

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
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-value", "some value");
        properties.put("boolean-value", "true");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, monitor, binder -> configBinder(binder).bindConfig(AnnotatedSetter.class));
        AnnotatedSetter annotatedSetter = injector.getInstance(AnnotatedSetter.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        Assert.assertNotNull(annotatedSetter);
        Assert.assertEquals(annotatedSetter.getStringValue(), "some value");
        Assert.assertEquals(annotatedSetter.isBooleanValue(), true);
    }

    @Test
    public void testConfigurationDespiteLegacyConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-a", "this is a");
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, monitor, binder -> configBinder(binder).bindConfig(LegacyConfigPresent.class));
        LegacyConfigPresent legacyConfigPresent = injector.getInstance(LegacyConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        Assert.assertNotNull(legacyConfigPresent);
        Assert.assertEquals(legacyConfigPresent.getStringA(), "this is a");
        Assert.assertEquals(legacyConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationThroughLegacyConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-value", "this is a");
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, monitor, binder -> configBinder(binder).bindConfig(LegacyConfigPresent.class));
        LegacyConfigPresent legacyConfigPresent = injector.getInstance(LegacyConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("string-value", "replaced", "Use 'string-a'");
        Assert.assertNotNull(legacyConfigPresent);
        Assert.assertEquals(legacyConfigPresent.getStringA(), "this is a");
        Assert.assertEquals(legacyConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationWithRedundantLegacyConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-value", "this is a");
        properties.put("string-a", "this is a");
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, monitor, binder -> configBinder(binder).bindConfig(LegacyConfigPresent.class));
        LegacyConfigPresent legacyConfigPresent = injector.getInstance(LegacyConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("string-value", "replaced", "Use 'string-a'");
        Assert.assertNotNull(legacyConfigPresent);
        Assert.assertEquals(legacyConfigPresent.getStringA(), "this is a");
        Assert.assertEquals(legacyConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationWithConflictingLegacyConfigThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-value", "this is the old value");
        properties.put("string-a", "this is a");
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, monitor, binder -> configBinder(binder).bindConfig(LegacyConfigPresent.class));

            Assert.fail("Expected an exception in object creation due to conflicting configuration");
        } catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(1);
            monitor.assertMatchingWarningRecorded("string-value", "replaced", "Use 'string-a'");
            Assertions.assertContainsAllOf(e.getMessage(), "string-value", "conflicts with property", "string-a") ;
        }
    }

    @Test
    public void testConfigurationDespiteDeprecatedConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, monitor, binder -> configBinder(binder).bindConfig(DeprecatedConfigPresent.class));
        DeprecatedConfigPresent deprecatedConfigPresent = injector.getInstance(DeprecatedConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        Assert.assertNotNull(deprecatedConfigPresent);
        Assert.assertEquals(deprecatedConfigPresent.getStringA(), "defaultA");
        Assert.assertEquals(deprecatedConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationThroughDeprecatedConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-a", "this is a");
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, monitor, binder -> configBinder(binder).bindConfig(DeprecatedConfigPresent.class));
        DeprecatedConfigPresent deprecatedConfigPresent = injector.getInstance(DeprecatedConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("string-a", "deprecated and should not be used");
        Assert.assertNotNull(deprecatedConfigPresent);
        Assert.assertEquals(deprecatedConfigPresent.getStringA(), "this is a");
        Assert.assertEquals(deprecatedConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testDefunctPropertyInConfigThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-value", "this is a");
        properties.put("defunct-value", "this shouldn't work");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, monitor, binder -> configBinder(binder).bindConfig(DefunctConfigPresent.class));

            Assert.fail("Expected an exception in object creation due to use of defunct config");
        } catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Defunct property", "'defunct-value", "cannot be configured");
        }
    }

    @Test
    public void testSuccessfulBeanValidation()
    {
        Map<String, String> properties = new HashMap<>();
        properties.put("string-value", "has a value");
        properties.put("int-value", "50");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, monitor, binder -> configBinder(binder).bindConfig(BeanValidationClass.class));
        BeanValidationClass beanValidationClass = injector.getInstance(BeanValidationClass.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);
        Assert.assertNotNull(beanValidationClass);
        Assert.assertEquals(beanValidationClass.getStringValue(), "has a value");
        Assert.assertEquals(beanValidationClass.getIntValue(), 50);
    }

    @Test
    public void testFailedBeanValidation()
    {
        Map<String, String> properties = new HashMap<>();
        // string-value left at invalid default
        properties.put("int-value", "5000");  // out of range
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, monitor, binder -> configBinder(binder).bindConfig(BeanValidationClass.class));
        } catch (CreationException e) {
            monitor.assertNumberOfErrors(2);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Invalid configuration property", "int-value", "must be less than or equal to 100", "BeanValidationClass");
            monitor.assertMatchingErrorRecorded("Invalid configuration property", "string-value", "may not be null", "BeanValidationClass");
        }
    }


    private static Injector createInjector(Map<String, String> properties, TestMonitor monitor, Module module)
    {
        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties, null, monitor);
        configurationFactory.registerConfigurationClasses(ImmutableList.of(module));
        List<Message> messages = configurationFactory.validateRegisteredConfigurationProvider();
        return Guice.createInjector(new ConfigurationModule(configurationFactory), module, new ValidationErrorModule(messages));
    }


    @SuppressWarnings("unused")
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

    public static class DeprecatedConfigPresent
    {
        private String stringA = "defaultA";
        private String stringB = "defaultB";

        @Deprecated
        public String getStringA()
        {
            return stringA;
        }

        @Deprecated
        @Config("string-a")
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

    @SuppressWarnings("unused")
    @DefunctConfig("defunct-value")
    public static class DefunctConfigPresent
    {
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
    }

    public static class BeanValidationClass
    {
        @NotNull
        private String stringValue;

        private int myIntValue;

        public String getStringValue()
        {
            return stringValue;
        }

        @Config("string-value")
        public void setStringValue(String value)
        {
            this.stringValue = value;
        }

        @Min(1)
        @Max(100)
        public int getIntValue()
        {
            return myIntValue;
        }

        @Config("int-value")
        public void setIntValue(int value)
        {
            this.myIntValue = value;
        }
    }
}
