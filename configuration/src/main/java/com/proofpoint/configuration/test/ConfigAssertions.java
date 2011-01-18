package com.proofpoint.configuration.test;

import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationMetadata;
import com.proofpoint.configuration.ConfigurationMetadata.AttributeMetadata;
import com.proofpoint.testing.Assertions;
import org.testng.Assert;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class ConfigAssertions
{
    private ConfigAssertions()
    {
    }

    public static <T> void assertAttributesEqual(Map<String, String> properties, T expected)
    {

        Assert.assertNotNull(expected, "expected");
        assertPropertiesSupported(properties.keySet(), expected.getClass());

        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties);
        T actual = (T) configurationFactory.build(expected.getClass());
        assertAttributesEqual(actual, expected);
    }

    public static void assertPropertiesSupported(Set<String> propertyNames, Class<?> configClass)
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getValidConfigurationMetadata(configClass);
        Set<String> supportedProperties = new TreeSet<String>();
        for (AttributeMetadata attribute : metadata.getAttributes().values()) {
            if (attribute.getPropertyName() != null) {
                supportedProperties.add(attribute.getPropertyName());
            }
            supportedProperties.addAll(attribute.getDeprecatedNames());
        }
        if (!supportedProperties.containsAll(propertyNames)) {
            TreeSet<String> unsupportedProperties = new TreeSet<String>(propertyNames);
            unsupportedProperties.removeAll(supportedProperties);
            throw new AssertionError("Unsupported properties: " + unsupportedProperties);
        }
    }

    public static <T> void assertAttributesEqual(T actual, T expected)
    {
        Assert.assertNotNull(actual, "actual");
        Assert.assertNotNull(expected, "expected");

        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getValidConfigurationMetadata(actual.getClass());
        for (AttributeMetadata attribute : metadata.getAttributes().values()) {
            Method getter = attribute.getGetter();
            if (getter == null) {
                continue;
            }
            Object actualAttributeValue = invoke(actual, getter);
            Object expectedAttributeValue = invoke(expected, getter);
            Assert.assertEquals(actualAttributeValue, expectedAttributeValue, attribute.getName());
        }
    }

    public static void assertDefaults(Object actual)
    {

    }

    public static void assertNoDefaults(Object actual)
    {
        Assert.assertNotNull(actual, "actual");
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getValidConfigurationMetadata(actual.getClass());
        Object expected = newInstance(metadata.getConstructor());
        assertAttributesNotEqual(actual, expected);
    }

    public static <T> void assertAttributesNotEqual(T actual, T expected)
    {
        Assert.assertNotNull(actual, "actual");
        Assert.assertNotNull(expected, "expected");

        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getValidConfigurationMetadata(actual.getClass());
        for (AttributeMetadata attribute : metadata.getAttributes().values()) {
            Method getter = attribute.getGetter();
            if (getter == null) {
                continue;
            }
            Object actualAttributeValue = invoke(actual, getter);
            Object expectedAttributeValue = invoke(expected, getter);
            Assertions.assertNotEquals(actualAttributeValue, expectedAttributeValue, attribute.getName());
        }
    }

    private static <T> Object invoke(T actual, Method getter)
    {
        try {
            return getter.invoke(actual);
        }
        catch (Exception e) {
            AssertionError error = new AssertionError(String.format("Exception invoking %s", getter.toGenericString()));
            error.initCause(e);
            throw error;
        }
    }

    private static <T> T newInstance(Constructor<T> constructor)
    {
        try {
            return constructor.newInstance();
        }
        catch (Exception e) {
            AssertionError error = new AssertionError(String.format("Exception invoking %s", constructor.toGenericString()));
            error.initCause(e);
            throw error;
        }
    }
}
