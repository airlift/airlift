package com.proofpoint.configuration.test;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MapMaker;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationMetadata;
import com.proofpoint.configuration.ConfigurationMetadata.AttributeMetadata;
import com.proofpoint.testing.Assertions;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.testng.Assert;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentMap;

public final class ConfigAssertions
{
    private static final Method GET_RECORDING_CONFIG_METHOD;

    static {
        try {
            GET_RECORDING_CONFIG_METHOD = $$RecordingConfigProxy.class.getMethod("$$getRecordedConfig");
        }
        catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

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
            if (attribute.getInjectionPoint().getProperty() != null) {
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
            if (attribute.getInjectionPoint().getProperty() != null) {
                nonDeprecatedProperties.add(attribute.getInjectionPoint().getProperty());
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
            for (ConfigurationMetadata.InjectionPointMetaData deprecated : attribute.getLegacyInjectionPoints()) {
                knownDeprecatedProperties.add(deprecated.getProperty());
            }
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
            if (attribute.getInjectionPoint().getProperty() != null) {
                nonDeprecatedProperties.add(attribute.getInjectionPoint().getProperty());
                supportedProperties.add(attribute.getInjectionPoint().getProperty());
            }
            for (ConfigurationMetadata.InjectionPointMetaData deprecated : attribute.getLegacyInjectionPoints()) {
                supportedProperties.add(deprecated.getProperty());
            }
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

    private static <T> void assertAttributesNotEqual(ConfigurationMetadata<T> metadata, T actual, T expected)
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

    public static <T> void assertRecordedDefaults(T recordedConfig)
    {
        $$RecordedConfigData<T> recordedConfigData = getRecordedConfig(recordedConfig);
        Set<Method> invokedMethods = recordedConfigData.getInvokedMethods();

        T config = recordedConfigData.getInstance();

        Class<T> configClass = (Class<T>) config.getClass();
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getValidConfigurationMetadata(configClass);

        // collect information about the attributes that have been set
        Map<String, Object> attributeValues = new TreeMap<String, Object>();
        Set<String> setDeprecatedAttributes = new TreeSet<String>();
        Set<Method> validSetterMethods = new HashSet<Method>();
        for (AttributeMetadata attribute : metadata.getAttributes().values()) {
            if (attribute.getInjectionPoint().getProperty() != null) {
                validSetterMethods.add(attribute.getInjectionPoint().getSetter());
            }

            if (invokedMethods.contains(attribute.getInjectionPoint().getSetter())) {
                if (attribute.getInjectionPoint().getProperty() != null) {
                    Object value = invoke(config, attribute.getGetter());
                    attributeValues.put(attribute.getName(), value);
                } else {
                    setDeprecatedAttributes.add(attribute.getName());
                }
            }
        }

        // verify no deprecated attribute setters have been called
        if (!setDeprecatedAttributes.isEmpty()) {
            Assert.fail("Invoked deprecated attribute setter methods: " + setDeprecatedAttributes);
        }

        // verify no other methods have been set
        if (!validSetterMethods.containsAll(invokedMethods)) {
            Set<Method> invalidInvocations = new HashSet<Method>(invokedMethods);
            invalidInvocations.removeAll(validSetterMethods);
            Assert.fail("Invoked non-attribute setter methods: " + invalidInvocations);

        }
        assertDefaults(attributeValues, configClass);
    }

    public static <T> T recordDefaults(Class<T> type)
    {
        final T instance = newDefaultInstance(type);
        T proxy = (T) Enhancer.create(type, new Class[]{$$RecordingConfigProxy.class}, new MethodInterceptor()
        {
            private final ConcurrentMap<Method, Object> invokedMethods = new MapMaker().makeMap();

            @Override
            public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy)
                    throws Throwable
            {
                if (GET_RECORDING_CONFIG_METHOD.equals(method)) {
                    return new $$RecordedConfigData<T>(instance, ImmutableSet.copyOf(invokedMethods.keySet()));
                }

                invokedMethods.put(method, Boolean.TRUE);

                Object result = methodProxy.invoke(instance, args);
                if (result == instance) {
                    return proxy;
                }
                else {
                    return result;
                }
            }
        });

        return proxy;
    }

    static <T> $$RecordedConfigData<T> getRecordedConfig(T config)
    {
        if (!(config instanceof $$RecordingConfigProxy)) {
            throw new IllegalArgumentException("Configuration was not created with the recordDefaults method");
        }
        return (($$RecordingConfigProxy<T>) config).$$getRecordedConfig();
    }

    public static class $$RecordedConfigData<T>
    {
        private final T instance;
        private final Set<Method> invokedMethods;

        public $$RecordedConfigData(T instance, Set<Method> invokedMethods)
        {
            this.instance = instance;
            this.invokedMethods = invokedMethods;
        }

        public T getInstance()
        {
            return instance;
        }

        public Set<Method> getInvokedMethods()
        {
            return invokedMethods;
        }
    }

    public static interface $$RecordingConfigProxy<T>
    {
        $$RecordedConfigData<T> $$getRecordedConfig();
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
        } catch (Exception e) {
            AssertionError error = new AssertionError(String.format("Exception creating default instance of %s", configClass.getName()));
            error.initCause(e);
            throw error;
        }
    }

    private static <T> Object invoke(T actual, Method getter)
    {
        try {
            return getter.invoke(actual);
        } catch (Exception e) {
            AssertionError error = new AssertionError(String.format("Exception invoking %s", getter.toGenericString()));
            error.initCause(e);
            throw error;
        }
    }
}
