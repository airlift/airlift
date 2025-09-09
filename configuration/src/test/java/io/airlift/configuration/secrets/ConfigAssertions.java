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
package io.airlift.configuration.secrets;

import com.google.common.collect.ImmutableSet;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.configuration.ConfigurationMetadata;
import io.airlift.configuration.ConfigurationMetadata.AttributeMetadata;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.google.common.collect.Sets.newConcurrentHashSet;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/*
 * This is a copy of io.airlift.configuration.testing.ConfigAssertions to avoid
 * a dependency loop, because the configuration-testing module depends on
 * this one.
 */
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

    private ConfigAssertions() {}

    public static <T> void assertDefaults(Map<String, Object> expectedAttributeValues, Class<T> configClass)
    {
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getValidConfigurationMetadata(configClass);

        // verify all supplied attributes are supported
        if (!metadata.getAttributes().keySet().containsAll(expectedAttributeValues.keySet())) {
            Set<String> unsupportedAttributes = new TreeSet<>(expectedAttributeValues.keySet());
            unsupportedAttributes.removeAll(metadata.getAttributes().keySet());
            throw new AssertionError("Unsupported attributes: " + unsupportedAttributes);
        }

        // verify all supplied attributes are supported not deprecated
        Set<String> nonDeprecatedAttributes = new TreeSet<>();
        for (AttributeMetadata attribute : metadata.getAttributes().values()) {
            if (attribute.getInjectionPoint().getProperty() != null) {
                nonDeprecatedAttributes.add(attribute.getName());
            }
        }
        if (!nonDeprecatedAttributes.containsAll(expectedAttributeValues.keySet())) {
            Set<String> unsupportedAttributes = new TreeSet<>(expectedAttributeValues.keySet());
            unsupportedAttributes.removeAll(nonDeprecatedAttributes);
            throw new AssertionError("Deprecated attributes: " + unsupportedAttributes);
        }

        // verify all attributes are tested
        if (!expectedAttributeValues.keySet().containsAll(nonDeprecatedAttributes)) {
            Set<String> untestedAttributes = new TreeSet<>(nonDeprecatedAttributes);
            untestedAttributes.removeAll(expectedAttributeValues.keySet());
            throw new AssertionError("Untested attributes: " + untestedAttributes);
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

            if (!Objects.deepEquals(actualAttributeValue, expectedAttributeValue)) {
                throw new AssertionError(notEquals(attribute.getName(), actualAttributeValue, expectedAttributeValue));
            }
        }
    }

    public static <T> void assertFullMapping(Map<String, String> properties, T expected)
    {
        requireNonNull(properties, "properties");
        requireNonNull(expected, "expected");

        Class<T> configClass = getClass(expected);
        ConfigurationMetadata<T> metadata = ConfigurationMetadata.getValidConfigurationMetadata(configClass);

        // verify all supplied properties are supported and not deprecated
        assertPropertiesSupported(metadata, properties.keySet(), false);

        // verify that every (non-deprecated) property is tested
        Set<String> nonDeprecatedProperties = new TreeSet<>();
        for (AttributeMetadata attribute : metadata.getAttributes().values()) {
            if (attribute.getInjectionPoint().getProperty() != null) {
                nonDeprecatedProperties.add(attribute.getInjectionPoint().getProperty());
            }
        }
        if (!properties.keySet().equals(nonDeprecatedProperties)) {
            Set<String> untestedProperties = new TreeSet<>(nonDeprecatedProperties);
            untestedProperties.removeAll(properties.keySet());
            throw new AssertionError("Untested properties " + untestedProperties);
        }

        // verify that none of the values are the same as a default for the configuration
        T actual = newInstance(configClass, properties);
        T defaultInstance = newDefaultInstance(configClass);
        assertAttributesNotEqual(metadata, actual, defaultInstance);

        // verify that a configuration object created from the properties is equivalent to the expected object
        assertAttributesEqual(metadata, actual, expected);
    }

    private static void assertPropertiesSupported(ConfigurationMetadata<?> metadata, Set<String> propertyNames, boolean allowDeprecatedProperties)
    {
        Set<String> supportedProperties = new TreeSet<>();
        Set<String> nonDeprecatedProperties = new TreeSet<>();
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
            Set<String> unsupportedProperties = new TreeSet<>(propertyNames);
            unsupportedProperties.removeAll(supportedProperties);
            throw new AssertionError("Unsupported properties: " + unsupportedProperties);
        }

        // check for usage of deprecated properties
        if (!allowDeprecatedProperties && !nonDeprecatedProperties.containsAll(propertyNames)) {
            Set<String> deprecatedProperties = new TreeSet<>(propertyNames);
            deprecatedProperties.removeAll(nonDeprecatedProperties);
            throw new AssertionError("Deprecated properties: " + deprecatedProperties);
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
            if (!Objects.deepEquals(actualAttributeValue, expectedAttributeValue)) {
                throw new AssertionError(notEquals(attribute.getName(), actualAttributeValue, expectedAttributeValue));
            }
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

            if (Objects.deepEquals(actualAttributeValue, expectedAttributeValue)) {
                throw new AssertionError("Attribute value matches the default: " + attribute.getName());
            }
        }
    }

    public static <T> void assertRecordedDefaults(T recordedConfig)
    {
        $$RecordedConfigData<T> recordedConfigData = getRecordedConfig(recordedConfig);
        Set<Method> invokedMethods = recordedConfigData.invokedMethods();

        T config = recordedConfigData.instance();

        Class<T> configClass = getClass(config);
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getValidConfigurationMetadata(configClass);

        // collect information about the attributes that have been set
        Map<String, Object> attributeValues = new TreeMap<>();
        Set<String> setDeprecatedAttributes = new TreeSet<>();
        Set<Method> validSetterMethods = new HashSet<>();
        for (AttributeMetadata attribute : metadata.getAttributes().values()) {
            if (attribute.getInjectionPoint().getProperty() != null) {
                validSetterMethods.add(attribute.getInjectionPoint().getSetter());
            }

            if (invokedMethods.contains(attribute.getInjectionPoint().getSetter())) {
                if (attribute.getInjectionPoint().getProperty() != null) {
                    Object value = invoke(config, attribute.getGetter());
                    attributeValues.put(attribute.getName(), value);
                }
                else {
                    setDeprecatedAttributes.add(attribute.getName());
                }
            }
        }

        // verify no deprecated attribute setters have been called
        if (!setDeprecatedAttributes.isEmpty()) {
            throw new AssertionError("Invoked deprecated attribute setter methods: " + setDeprecatedAttributes);
        }

        // verify no other methods have been set
        if (!validSetterMethods.containsAll(invokedMethods)) {
            Set<Method> invalidInvocations = new HashSet<>(invokedMethods);
            invalidInvocations.removeAll(validSetterMethods);
            throw new AssertionError("Invoked non-attribute setter methods: " + invalidInvocations);
        }
        assertDefaults(attributeValues, configClass);
    }

    public static <T> T recordDefaults(Class<T> type)
    {
        Class<? extends T> loaded = new ByteBuddy()
                .subclass(type)
                .implement($$RecordingConfigProxy.class)
                .method(ElementMatchers.any())
                .intercept(createInvocationHandler(type))
                .make()
                .load(type.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();

        try {
            return loaded.getConstructor().newInstance();
        }
        catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to instantiate proxy class for " + type.getName(), e);
        }
    }

    @SuppressWarnings("ObjectEquality")
    private static <T> InvocationHandlerAdapter createInvocationHandler(Class<T> type)
    {
        T instance = newDefaultInstance(type);
        Set<Method> invokedMethods = newConcurrentHashSet();

        return InvocationHandlerAdapter.of((proxy, method, args) -> {
            if (GET_RECORDING_CONFIG_METHOD.equals(method)) {
                return new $$RecordedConfigData<>(instance, ImmutableSet.copyOf(invokedMethods));
            }

            invokedMethods.add(method);

            Object result = method.invoke(instance, args);
            if (result == instance) {
                return proxy;
            }
            return result;
        });
    }

    @SuppressWarnings("unchecked")
    static <T> $$RecordedConfigData<T> getRecordedConfig(T config)
    {
        if (!(config instanceof $$RecordingConfigProxy)) {
            throw new IllegalArgumentException("Configuration was not created with the recordDefaults method");
        }
        return (($$RecordingConfigProxy<T>) config).$$getRecordedConfig();
    }

    @SuppressWarnings("checkstyle:TypeName")
    public record $$RecordedConfigData<T>(T instance, Set<Method> invokedMethods)
    {
        public $$RecordedConfigData
        {
            requireNonNull(instance, "instance is null");
            invokedMethods = ImmutableSet.copyOf(invokedMethods);
        }
    }

    @SuppressWarnings({"checkstyle:TypeName", "checkstyle:MethodName"})
    public interface $$RecordingConfigProxy<T>
    {
        $$RecordedConfigData<T> $$getRecordedConfig();
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> getClass(T object)
    {
        return (Class<T>) object.getClass();
    }

    private static <T> T newInstance(Class<T> configClass, Map<String, String> properties)
    {
        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties);
        return configurationFactory.build(configClass);
    }

    private static <T> T newDefaultInstance(Class<T> configClass)
    {
        try {
            return configClass.getConstructor().newInstance();
        }
        catch (ReflectiveOperationException e) {
            throw new AssertionError("Exception creating default instance of " + configClass.getName(), e);
        }
    }

    private static <T> Object invoke(T actual, Method getter)
    {
        try {
            return getter.invoke(actual);
        }
        catch (ReflectiveOperationException e) {
            throw new AssertionError("Exception invoking " + getter.toGenericString(), e);
        }
    }

    private static String notEquals(String message, Object actual, Object expected)
    {
        return format("%s expected [%s] but found [%s]", message, expected, actual);
    }
}
