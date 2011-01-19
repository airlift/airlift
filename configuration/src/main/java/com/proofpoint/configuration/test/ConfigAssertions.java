package com.proofpoint.configuration.test;

import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationMetadata;
import com.proofpoint.configuration.ConfigurationMetadata.AttributeMetadata;
import com.proofpoint.testing.Assertions;
import org.testng.Assert;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class ConfigAssertions
{
    private ConfigAssertions()
    {
    }

    public static <T> void assertDefaults(Map<String, Object> expectedAttributeValues, Class<T> configClass)
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getValidConfigurationMetadata(configClass);

        // verify all supplied attributes are supported
        if (!metadata.getAttributes().keySet().containsAll(expectedAttributeValues.keySet())) {
            TreeSet<String> unsupportedAttributes = new TreeSet<String>(expectedAttributeValues.keySet());
            unsupportedAttributes.removeAll(metadata.getAttributes().keySet());
            Assert.fail("Unsupported attributes: " + unsupportedAttributes);
        }

        // verify all supplied attributes are supported not deprecated
        Set<String> nonDeprecatedAttributes = new TreeSet<String>();
        for (AttributeMetadata attribute : metadata.getAttributes().values()) {
            if (attribute.getPropertyName() != null) {
                nonDeprecatedAttributes.add(attribute.getName());
            }
        }
        if (!nonDeprecatedAttributes.containsAll(expectedAttributeValues.keySet())) {
            TreeSet<String> unsupportedAttributes = new TreeSet<String>(expectedAttributeValues.keySet());
            unsupportedAttributes.removeAll(nonDeprecatedAttributes);
            Assert.fail("Deprecated attributes: " + unsupportedAttributes);
        }

        // verify all attributes are tested
        if (!expectedAttributeValues.keySet().containsAll(nonDeprecatedAttributes)) {
            TreeSet<String> untestedAttributes = new TreeSet<String>(nonDeprecatedAttributes);
            untestedAttributes.removeAll(expectedAttributeValues.keySet());
            Assert.fail("Untested attributes: " + untestedAttributes);
        }

        // create an uninitialized default instance
        T actual = newDefaultInstance(configClass);

        // verify each attribute is either the supplied default value
        for (AttributeMetadata attribute : metadata.getAttributes().values()) {
            Method getter = attribute.getGetter();
            if (getter == null) {
                continue;
            }
            Object actualAttributeValue = invoke(actual, getter);
            Object expectedAttributeValue = expectedAttributeValues.get(attribute.getName());

            Assert.assertEquals(expectedAttributeValue, actualAttributeValue, attribute.getName());
        }
    }

    public static <T> void assertFullMapping(Map<String, String> properties, T expected)
    {
        Assert.assertNotNull(properties, "properties");
        Assert.assertNotNull(expected, "expected");

        Class<T> configClass = (Class<T>) expected.getClass();
        ConfigurationMetadata<T> metadata = ConfigurationMetadata.getValidConfigurationMetadata(configClass);

        // verify all supplied properties are supported and not deprecated
        assertPropertiesSupported(metadata, properties.keySet(), false);

        // verify that every (non-deprecated) property is tested
        Set<String> nonDeprecatedProperties = new TreeSet<String>();
        for (AttributeMetadata attribute : metadata.getAttributes().values()) {
            if (attribute.getPropertyName() != null) {
                nonDeprecatedProperties.add(attribute.getPropertyName());
            }
        }
        if (!properties.keySet().equals(nonDeprecatedProperties)) {
            TreeSet<String> untestedProperties = new TreeSet<String>(nonDeprecatedProperties);
            untestedProperties.removeAll(properties.keySet());
            Assert.fail(String.format("Untested properties " + untestedProperties));
        }

        // verify that none of the values are the same as a default for the configuration
        T actual = newInstance(configClass, properties);
        T defaultInstance = newDefaultInstance(configClass);
        assertAttributesNotEqual(metadata, actual, defaultInstance);

        // verify that a configuration object created from the properties is equivalent to the expected object
        assertAttributesEqual(metadata, actual, expected);
    }

    public static <T> void assertDeprecatedEquivalence(Class<T> configClass, Map<String, String> currentProperties, Map<String, String> oldProperties, Map<String, String>... evenOlderPropertiesList)
    {
        Assert.assertNotNull(configClass, "configClass");
        Assert.assertNotNull(currentProperties, "currentProperties");
        Assert.assertNotNull(oldProperties, "oldProperties");
        Assert.assertNotNull(evenOlderPropertiesList, "evenOlderPropertiesList");

        ConfigurationMetadata<T> metadata = ConfigurationMetadata.getValidConfigurationMetadata(configClass);

        // verify all current properties are supported and not deprecated
        assertPropertiesSupported(metadata, currentProperties.keySet(), false);

        // verify all old properties are supported (deprecation allowed)
        assertPropertiesSupported(metadata, oldProperties.keySet(), true);
        for (Map<String, String> evenOlderProperties : evenOlderPropertiesList) {
            assertPropertiesSupported(metadata, evenOlderProperties.keySet(), true);
        }

        // verify that all deprecated properties are tested
        Set<String> knownDeprecatedProperties = new TreeSet<String>();
        for (AttributeMetadata attribute : metadata.getAttributes().values()) {
            knownDeprecatedProperties.addAll(attribute.getDeprecatedNames());
        }
        Set<String> suppliedDeprecatedProperties = new TreeSet<String>();
        suppliedDeprecatedProperties.addAll(oldProperties.keySet());
        for (Map<String, String> evenOlderProperties : evenOlderPropertiesList) {
            suppliedDeprecatedProperties.addAll(evenOlderProperties.keySet());
        }
        if (!suppliedDeprecatedProperties.containsAll(knownDeprecatedProperties)) {
            TreeSet<String> untestedDeprecatedProperties = new TreeSet<String>(knownDeprecatedProperties);
            untestedDeprecatedProperties.removeAll(suppliedDeprecatedProperties);
            Assert.fail("Untested deprecated properties: " + untestedDeprecatedProperties);
        }

        // verify property sets create equivalent configurations
        T currentConfiguration = newInstance(configClass, currentProperties);
        T oldConfiguration = newInstance(configClass, oldProperties);
        assertAttributesEqual(metadata, currentConfiguration, oldConfiguration);
        for (Map<String, String> evenOlderProperties : evenOlderPropertiesList) {
            T evenOlderConfiguration = newInstance(configClass, evenOlderProperties);
            assertAttributesEqual(metadata, currentConfiguration, evenOlderConfiguration);
        }
    }

    private static void assertPropertiesSupported(ConfigurationMetadata<?> metadata, Set<String> propertyNames, boolean allowDeprecatedProperties)
    {
        Set<String> supportedProperties = new TreeSet<String>();
        Set<String> nonDeprecatedProperties = new TreeSet<String>();
        for (AttributeMetadata attribute : metadata.getAttributes().values()) {
            if (attribute.getPropertyName() != null) {
                nonDeprecatedProperties.add(attribute.getPropertyName());
                supportedProperties.add(attribute.getPropertyName());
            }
            supportedProperties.addAll(attribute.getDeprecatedNames());
        }
        if (!supportedProperties.containsAll(propertyNames)) {
            TreeSet<String> unsupportedProperties = new TreeSet<String>(propertyNames);
            unsupportedProperties.removeAll(supportedProperties);
            Assert.fail("Unsupported properties: " + unsupportedProperties);
        }

        // check for usage of deprecated properties
        if (!allowDeprecatedProperties && !nonDeprecatedProperties.containsAll(propertyNames)) {
            TreeSet<String> deprecatedProperties = new TreeSet<String>(propertyNames);
            deprecatedProperties.removeAll(nonDeprecatedProperties);
            Assert.fail("Deprecated properties: " + deprecatedProperties);
        }
    }

    private static <T> void assertAttributesEqual(ConfigurationMetadata<T> metadata, T actual, T expected)
    {
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

    private static <T> void assertAttributesNotEqual(ConfigurationMetadata<T> metadata , T actual, T expected)
    {
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

    private static <T> T newInstance(Class<T> configClass, Map<String, String> properties)
    {
        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties);
        return configurationFactory.build(configClass);
    }

    private static <T> T newDefaultInstance(Class<T> configClass)
    {
        try {
            return configClass.newInstance();
        }
        catch (Exception e) {
            AssertionError error = new AssertionError(String.format("Exception creating default instance of %s", configClass.getName()));
            error.initCause(e);
            throw error;
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
}
