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
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.spi.Message;
import org.testng.annotations.Test;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static io.airlift.testing.Assertions.assertContainsAllOf;
import static java.util.Objects.requireNonNull;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;

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
            fail("Expected an exception in object creation due to conflicting configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(2);
            assertContainsAllOf(e.getMessage(), "not a valid setter", "getStringValue");
            assertContainsAllOf(e.getMessage(), "not a valid setter", "isBooleanValue");
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
        assertNotNull(annotatedSetter);
        assertEquals(annotatedSetter.getStringValue(), "some value");
        assertEquals(annotatedSetter.isBooleanValue(), true);
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
        assertNotNull(legacyConfigPresent);
        assertEquals(legacyConfigPresent.getStringA(), "this is a");
        assertEquals(legacyConfigPresent.getStringB(), "this is b");
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
        monitor.assertMatchingWarningRecorded("Configuration property 'string-value' has been replaced. Use 'string-a' instead.");
        assertNotNull(legacyConfigPresent);
        assertEquals(legacyConfigPresent.getStringA(), "this is a");
        assertEquals(legacyConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationWithPrefixThroughLegacyConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("example.string-value", "this is a");
        properties.put("example.string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, monitor, binder -> configBinder(binder).bindConfig(LegacyConfigPresent.class, "example"));
        LegacyConfigPresent legacyConfigPresent = injector.getInstance(LegacyConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("Configuration property 'example.string-value' has been replaced. Use 'example.string-a' instead.");
        assertNotNull(legacyConfigPresent);
        assertEquals(legacyConfigPresent.getStringA(), "this is a");
        assertEquals(legacyConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationWithRedundantLegacyConfigThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("string-value", "this is a");
        properties.put("string-a", "this is a");
        properties.put("string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, monitor, binder -> configBinder(binder).bindConfig(LegacyConfigPresent.class));
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(1);
            monitor.assertMatchingWarningRecorded("string-value", "replaced", "Use 'string-a'");
            assertContainsAllOf(e.getMessage(), "string-value", "conflicts with property", "string-a");
        }
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
            fail("Expected an exception in object creation due to conflicting configuration");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(1);
            monitor.assertMatchingWarningRecorded("string-value", "replaced", "Use 'string-a'");
            assertContainsAllOf(e.getMessage(), "string-value", "conflicts with property", "string-a");
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
        assertNotNull(deprecatedConfigPresent);
        assertEquals(deprecatedConfigPresent.getStringA(), "defaultA");
        assertEquals(deprecatedConfigPresent.getStringB(), "this is b");
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
        monitor.assertMatchingWarningRecorded("Configuration property 'string-a' is deprecated and should not be used");
        assertNotNull(deprecatedConfigPresent);
        assertEquals(deprecatedConfigPresent.getStringA(), "this is a");
        assertEquals(deprecatedConfigPresent.getStringB(), "this is b");
    }

    @Test
    public void testConfigurationWithPrefixThroughDeprecatedConfig()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("example.string-a", "this is a");
        properties.put("example.string-b", "this is b");
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(properties, monitor, binder -> configBinder(binder).bindConfig(DeprecatedConfigPresent.class, "example"));
        DeprecatedConfigPresent deprecatedConfigPresent = injector.getInstance(DeprecatedConfigPresent.class);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(1);
        monitor.assertMatchingWarningRecorded("Configuration property 'example.string-a' is deprecated and should not be used");
        assertNotNull(deprecatedConfigPresent);
        assertEquals(deprecatedConfigPresent.getStringA(), "this is a");
        assertEquals(deprecatedConfigPresent.getStringB(), "this is b");
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
            fail("Expected an exception in object creation due to use of defunct config");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Defunct property 'defunct-value'", "cannot be configured");
        }
    }

    @Test
    public void testDefunctPropertyWithPrefixInConfigThrows()
    {
        Map<String, String> properties = new TreeMap<>();
        properties.put("example.string-value", "this is a");
        properties.put("example.defunct-value", "this shouldn't work");
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, monitor, binder -> configBinder(binder).bindConfig(DefunctConfigPresent.class, "example"));
            fail("Expected an exception in object creation due to use of defunct config");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Defunct property 'example.defunct-value'", "cannot be configured");
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
        assertNotNull(beanValidationClass);
        assertEquals(beanValidationClass.getStringValue(), "has a value");
        assertEquals(beanValidationClass.getIntValue(), 50);
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
            fail("Expected an exception in object creation due to failed bean validation");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(2);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Invalid configuration property int-value: must be less than or equal to 100", "BeanValidationClass");
            monitor.assertMatchingErrorRecorded("Invalid configuration property string-value: may not be null", "BeanValidationClass");
        }
    }

    @Test
    public void testFailedBeanValidationPrefix()
    {
        Map<String, String> properties = new HashMap<>();
        // string-value left at invalid default
        properties.put("example.int-value", "5000");  // out of range
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, monitor, binder -> configBinder(binder).bindConfig(BeanValidationClass.class, "example"));
            fail("Expected an exception in object creation due to failed bean validation");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(2);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Invalid configuration property example.int-value: must be less than or equal to 100", "BeanValidationClass");
            monitor.assertMatchingErrorRecorded("Invalid configuration property example.string-value: may not be null", "BeanValidationClass");
        }
    }

    @Test
    public void testFailedCoercion()
    {
        Map<String, String> properties = new HashMap<>();
        properties.put("int-value", "abc %s xyz");  // not an int
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, monitor, binder -> configBinder(binder).bindConfig(BeanValidationClass.class));
            fail("Expected an exception in object creation due to failed coercion");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Could not coerce value 'abc %s xyz' to int (property 'int-value')", "BeanValidationClass");
        }
    }

    @Test
    public void testFailedCoercionPrefix()
    {
        Map<String, String> properties = new HashMap<>();
        properties.put("example.int-value", "abc %s xyz");  // not an int
        TestMonitor monitor = new TestMonitor();
        try {
            createInjector(properties, monitor, binder -> configBinder(binder).bindConfig(BeanValidationClass.class, "example"));
            fail("Expected an exception in object creation due to failed coercion");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Could not coerce value 'abc %s xyz' to int (property 'example.int-value')", "BeanValidationClass");
        }
    }

    @Test
    public void testFromString()
    {
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(ImmutableMap.of("value", "value-good-for-fromString"), monitor, binder -> configBinder(binder).bindConfig(FromStringClass.class));
        assertSame(injector.getInstance(FromStringClass.class).value, FromStringClass.Value.FROM_STRING_VALUE);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);

        monitor = new TestMonitor();
        try {
            createInjector(ImmutableMap.of("value", "value-good-for-valueOf"), monitor, binder -> configBinder(binder).bindConfig(FromStringClass.class));
            fail("Expected an exception in object creation due to failed coercion");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Could not coerce value 'value-good-for-valueOf' to", "(property 'value')", "FromStringClass");
        }
    }

    @Test
    public void testEnumWithFromString()
    {
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(ImmutableMap.of("value", "yes"), monitor, binder -> configBinder(binder).bindConfig(EnumWithFromStringClass.class));
        assertSame(injector.getInstance(EnumWithFromStringClass.class).value, EnumWithFromStringClass.Value.TRUE);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);

        monitor = new TestMonitor();
        try {
            createInjector(ImmutableMap.of("value", "TRUE"), monitor, binder -> configBinder(binder).bindConfig(EnumWithFromStringClass.class));
            fail("Expected an exception in object creation due to failed coercion");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Could not coerce value 'TRUE' to", "(property 'value')", "EnumWithFromStringClass");
        }
    }

    @Test
    public void testValueOf()
    {
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(ImmutableMap.of("value", "value-good-for-valueOf"), monitor, binder -> configBinder(binder).bindConfig(ValueOfClass.class));
        assertSame(injector.getInstance(ValueOfClass.class).value, ValueOfClass.Value.VALUE_OF_VALUE);
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);

        monitor = new TestMonitor();
        try {
            createInjector(ImmutableMap.of("value", "anything"), monitor, binder -> configBinder(binder).bindConfig(ValueOfClass.class));
            fail("Expected an exception in object creation due to failed coercion");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Could not coerce value 'anything' to", "(property 'value')", "ValueOfClass");
        }
    }

    @Test
    public void testStringConstructor()
    {
        TestMonitor monitor = new TestMonitor();
        Injector injector = createInjector(ImmutableMap.of("value", "constructor-value"), monitor, binder -> configBinder(binder).bindConfig(StringConstructorClass.class));
        assertEquals(injector.getInstance(StringConstructorClass.class).value.string, "constructor-argument: constructor-value");
        monitor.assertNumberOfErrors(0);
        monitor.assertNumberOfWarnings(0);

        monitor = new TestMonitor();
        try {
            createInjector(ImmutableMap.of("value", "bad-value"), monitor, binder -> configBinder(binder).bindConfig(StringConstructorClass.class));
            fail("Expected an exception in object creation due to failed coercion");
        }
        catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Could not coerce value 'bad-value' to", "(property 'value')", "StringConstructorClass");
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
    public static class AnnotatedGetter
    {
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

    public static class AnnotatedSetter
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

    public static class FromStringClass
    {
        public static class Value
        {
            static final Value FROM_STRING_VALUE = new Value("");

            public static Value fromString(String value)
            {
                checkArgument("value-good-for-fromString".equals(value));
                return FROM_STRING_VALUE;
            }

            public static Value valueOf(String value)
            {
                checkArgument("value-good-for-valueOf".equals(value));
                return new Value("");
            }

            public Value(String ignored) {}
        }

        private Value value;

        public Value getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(Value value)
        {
            this.value = value;
        }
    }

    public static class EnumWithFromStringClass
    {
        public enum Value
        {
            TRUE, FALSE;

            public static Value fromString(String string)
            {
                switch (requireNonNull(string, "string is null")) {
                    case "yes":
                        return TRUE;
                    case "no":
                        return FALSE;
                    default:
                        throw new IllegalArgumentException("Invalid value: " + string);
                }
            }
        }

        private Value value;

        public Value getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(Value value)
        {
            this.value = value;
        }
    }

    public static class ValueOfClass
    {
        public static class Value
        {
            static final Value VALUE_OF_VALUE = new Value("");

            public static Value valueOf(String value)
            {
                checkArgument("value-good-for-valueOf".equals(value));
                return VALUE_OF_VALUE;
            }

            public Value(String ignored) {}
        }

        private Value value;

        public Value getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(Value value)
        {
            this.value = value;
        }
    }

    public static class StringConstructorClass
    {
        public static class Value
        {
            private final String string;

            public Value(String string)
            {
                checkArgument(!"bad-value".equals(string));
                this.string = "constructor-argument: " + requireNonNull(string, "string is null");
            }
        }

        private Value value;

        public Value getValue()
        {
            return value;
        }

        @Config("value")
        public void setValue(Value value)
        {
            this.value = value;
        }
    }
}
