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
package com.proofpoint.configuration;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.inject.Binding;
import com.google.inject.ConfigurationException;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.spi.DefaultElementVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.Message;
import com.google.inject.spi.ProviderInstanceBinding;
import com.proofpoint.configuration.ConfigurationMetadata.AttributeMetadata;
import com.proofpoint.configuration.ConfigurationMetadata.InjectionPointMetaData;
import com.proofpoint.configuration.Problems.Monitor;
import org.apache.bval.jsr303.ApacheValidationProvider;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Throwables.propagate;
import static com.google.common.collect.Sets.newConcurrentHashSet;
import static com.proofpoint.configuration.ConfigurationMetadata.getConfigurationMetadata;
import static com.proofpoint.configuration.ConfigurationMetadata.isConfigClass;
import static com.proofpoint.configuration.Problems.exceptionFor;
import static java.lang.String.format;

public final class ConfigurationFactory
{
    private static final Validator VALIDATOR = Validation.byProvider(ApacheValidationProvider.class).configure().buildValidatorFactory().getValidator();

    private final Map<String, String> properties;
    private final Map<String, String> applicationDefaults;
    private final Problems.Monitor monitor;
    private final Set<String> unusedProperties = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Collection<String> initialErrors;
    private final ConcurrentMap<ConfigurationProvider<?>, Object> instanceCache = new ConcurrentHashMap<>();
    private final Set<String> usedProperties = newConcurrentHashSet();
    private final Set<ConfigurationProvider<?>> registeredProviders = newConcurrentHashSet();
    private final LoadingCache<Class<?>, ConfigurationMetadata<?>> metadataCache = CacheBuilder.newBuilder()
            .build(new CacheLoader<Class<?>, ConfigurationMetadata<?>>()
            {
                @Override
                public ConfigurationMetadata<?> load(Class<?> configClass)
                {
                    return getConfigurationMetadata(configClass, monitor);
                }
            });

    public ConfigurationFactory(Map<String, String> properties)
    {
        this(properties, ImmutableMap.<String, String>of(), properties.keySet(), ImmutableList.<String>of(), Problems.NULL_MONITOR);
    }

    ConfigurationFactory(Map<String, String> properties, Map<String, String> applicationDefaults, Set<String> expectToUse, Collection<String> errors, final Monitor monitor)
    {
        this.monitor = monitor;
        this.properties = ImmutableMap.copyOf(properties);
        this.applicationDefaults = ImmutableMap.copyOf(applicationDefaults);
        unusedProperties.addAll(expectToUse);
        initialErrors = ImmutableList.copyOf(errors);
    }

    public Map<String, String> getProperties()
    {
        return properties;
    }

    /**
     * Marks the specified property as consumed.
     */
    @Beta
    public void consumeProperty(String property)
    {
        Preconditions.checkNotNull(property, "property is null");
        usedProperties.add(property);
        unusedProperties.remove(property);
    }

    @Deprecated
    public Set<String> getUsedProperties()
    {
        return ImmutableSortedSet.copyOf(usedProperties);
    }

    Set<String> getUnusedProperties()
    {

        return ImmutableSortedSet.copyOf(unusedProperties);
    }

    Monitor getMonitor()
    {
        return monitor;
    }

    Collection<String> getInitialErrors()
    {
        return initialErrors;
    }

    /**
     * Registers all configuration classes in the module so they can be part of configuration inspection.
     */
    @Beta
    public void registerConfigurationClasses(Module module)
    {
        registeredProviders.addAll(getAllProviders(module));
    }

    Iterable<ConfigurationProvider<?>> getConfigurationProviders()
    {
        return ImmutableList.copyOf(registeredProviders);
    }

    public <T> T build(Class<T> configClass)
    {
        return build(configClass, null).instance;
    }

    /**
     * This is used by the configuration provider
     */
    <T> T build(ConfigurationProvider<T> configurationProvider, WarningsMonitor warningsMonitor)
    {
        Preconditions.checkNotNull(configurationProvider, "configurationProvider");
        registeredProviders.add(configurationProvider);

        // check for a prebuilt instance
        T instance = getCachedInstance(configurationProvider);
        if (instance != null) {
            return instance;
        }

        ConfigurationHolder<T> holder = build(configurationProvider.getConfigClass(), configurationProvider.getPrefix());
        instance = holder.instance;

        // inform caller about warnings
        if (warningsMonitor != null) {
            for (Message message : holder.problems.getWarnings()) {
                warningsMonitor.onWarning(message.toString());
            }
        }

        // add to instance cache
        T existingValue = putCachedInstance(configurationProvider, instance);
        // if key was already associated with a value, there was a
        // creation race and we lost. Just use the winners' instance;
        if (existingValue != null) {
            return existingValue;
        }
        return instance;
    }

    <T> T buildDefaults(ConfigurationProvider<T> configurationProvider)
    {
        return build(configurationProvider.getConfigClass(), configurationProvider.getPrefix(), true, new Problems());
    }

    @SuppressWarnings("unchecked")
    private <T> T getCachedInstance(ConfigurationProvider<T> configurationProvider)
    {
        return (T) instanceCache.get(configurationProvider);
    }

    @SuppressWarnings("unchecked")
    private <T> T putCachedInstance(ConfigurationProvider<T> configurationProvider, T instance)
    {
        return (T) instanceCache.putIfAbsent(configurationProvider, instance);
    }

    private <T> ConfigurationHolder<T> build(Class<T> configClass, String prefix)
    {
        Problems problems = new Problems(monitor);

        final T instance = build(configClass, prefix, false, problems);

        problems.throwIfHasErrors();

        return new ConfigurationHolder<>(instance, problems);
    }

    private <T> T build(Class<T> configClass, String prefix, boolean isDefault, Problems problems)
    {
        if (configClass == null) {
            throw new NullPointerException("configClass is null");
        }

        if (prefix == null) {
            prefix = "";
        }
        else if (!prefix.isEmpty()) {
            prefix += ".";
        }

        ConfigurationMetadata<T> configurationMetadata = getMetadata(configClass);
        configurationMetadata.getProblems().throwIfHasErrors();

        T instance = newInstance(configurationMetadata);

        for (AttributeMetadata attribute : configurationMetadata.getAttributes().values()) {
            try {
                setConfigProperty(instance, attribute, prefix, isDefault, problems);
            }
            catch (InvalidConfigurationException e) {
                problems.addError(e.getCause(), e.getMessage());
            }
        }

        if (isDefault) {
            return instance;
        }

        // Check that none of the defunct properties are still in use
        if (configClass.isAnnotationPresent(DefunctConfig.class)) {
            for (String value : configClass.getAnnotation(DefunctConfig.class).value()) {
                String key = prefix + value;
                if (!value.isEmpty() && properties.get(key) != null) {
                    problems.addError("Defunct property '%s' (class [%s]) cannot be configured.", key, configClass.toString());
                }
                if (!value.isEmpty() && applicationDefaults.get(key) != null) {
                    problems.addError("Defunct property '%s' (class [%s]) cannot have an application default.", key, configClass.toString());
                }
            }
        }

        for (ConstraintViolation<?> violation : VALIDATOR.validate(instance)) {
            AttributeMetadata attributeMetadata = configurationMetadata.getAttributes()
                    .get(LOWER_CAMEL.to(UPPER_CAMEL, violation.getPropertyPath().toString()));
            if (attributeMetadata != null) {
                problems.addError("Constraint violation for property '%s': %s (for class %s)",
                        prefix + attributeMetadata.getInjectionPoint().getProperty(), violation.getMessage(), configClass.getName());
            }
            else {
                problems.addError("Constraint violation with property prefix '%s': %s %s (for class %s)",
                        prefix, violation.getPropertyPath(), violation.getMessage(), configClass.getName());
            }
        }

        return instance;
    }

    @SuppressWarnings("unchecked")
    private <T> ConfigurationMetadata<T> getMetadata(Class<T> configClass)
    {
        try {
            return (ConfigurationMetadata<T>) metadataCache.getUnchecked(configClass);
        }
        catch (UncheckedExecutionException e) {
            throw propagate(e.getCause());
        }
    }

    private static <T> T newInstance(ConfigurationMetadata<T> configurationMetadata)
    {
        try {
            return configurationMetadata.getConstructor().newInstance();
        }
        catch (Throwable e) {
            if (e instanceof InvocationTargetException && e.getCause() != null) {
                e = e.getCause();
            }
            throw exceptionFor(e, "Error creating instance of configuration class [%s]", configurationMetadata.getConfigClass().getName());
        }
    }

    private <T> void setConfigProperty(T instance, AttributeMetadata attribute, String prefix, boolean isDefault, Problems problems)
            throws InvalidConfigurationException
    {
        // Get property value
        ConfigurationMetadata.InjectionPointMetaData injectionPoint = findOperativeInjectionPoint(attribute, prefix, isDefault, problems);

        // If we did not get an injection point, do not call the setter
        if (injectionPoint == null) {
            return;
        }

        Object value = getInjectedValue(attribute, injectionPoint, prefix, isDefault, problems);

        try {
            injectionPoint.getSetter().invoke(instance, value);
        }
        catch (Throwable e) {
            if (e instanceof InvocationTargetException && e.getCause() != null) {
                e = e.getCause();
            }
            throw new InvalidConfigurationException(e, "Error invoking configuration method [%s]", injectionPoint.getSetter().toGenericString());
        }
    }

    private ConfigurationMetadata.InjectionPointMetaData findOperativeInjectionPoint(AttributeMetadata attribute, String prefix, boolean isDefault, Problems problems)
            throws ConfigurationException
    {
        OperativeInjectionData operativeInjectionData = new OperativeInjectionData(attribute, prefix, problems);
        operativeInjectionData.consider(attribute.getInjectionPoint(), false);

        for (ConfigurationMetadata.InjectionPointMetaData injectionPoint : attribute.getLegacyInjectionPoints()) {
            operativeInjectionData.consider(injectionPoint, true);
        }

        if (!isDefault) {
            problems.throwIfHasErrors();

            InjectionPointMetaData injectionPoint = operativeInjectionData.operativeInjectionPoint;
            if (injectionPoint != null) {
                if (injectionPoint.getSetter().isAnnotationPresent(Deprecated.class)) {
                    problems.addWarning("Configuration property '%s' is deprecated and should not be used", prefix + injectionPoint.getProperty());
                }

                return injectionPoint;
            }
        }

        return operativeInjectionData.defaultInjectionPoint;
    }

    private class OperativeInjectionData
    {
        private AttributeMetadata attribute;
        private String prefix;
        private Problems problems;

        private String operativeDescription = null;
        InjectionPointMetaData operativeInjectionPoint = null;
        InjectionPointMetaData defaultInjectionPoint = null;

        public OperativeInjectionData(AttributeMetadata attribute, String prefix, Problems problems)
        {
            this.attribute = attribute;
            this.prefix = prefix;
            this.problems = problems;
        }

        public void consider(InjectionPointMetaData injectionPoint, boolean isLegacy)
        {
            if (injectionPoint == null) {
                return;
            }
            String fullName = prefix + injectionPoint.getProperty();
            if (injectionPoint.isConfigMap()) {
                final String mapPrefix = fullName + ".";
                for (String key : properties.keySet()) {
                    if (key.startsWith(mapPrefix)) {
                        if (isLegacy) {
                            warnLegacy(fullName);
                        }

                        if (operativeDescription == null) {
                            operativeInjectionPoint = injectionPoint;
                            operativeDescription = format("map property prefix '%s'", fullName);
                        }
                        else {
                            problems.addError("Map property prefix '%s' conflicts with %s", fullName, operativeDescription);
                        }
                        break;
                    }
                }
                for (String key : applicationDefaults.keySet()) {
                    if (key.startsWith(mapPrefix)) {
                        problems.addError("Cannot have application default property '%s' for a configuration map '%s'", key, fullName);
                    }
                }
            }
            else {
                if (applicationDefaults.get(fullName) != null) {
                    if (isLegacy) {
                        defaultLegacy(fullName);
                    }
                    else {
                        defaultInjectionPoint = injectionPoint;
                    }
                }
                String value = properties.get(fullName);
                if (value != null) {
                    if (isLegacy) {
                        warnLegacy(fullName);
                    }

                    if (operativeDescription == null) {
                        operativeInjectionPoint = injectionPoint;
                        final StringBuilder stringBuilder = new StringBuilder("property '").append(fullName).append("'");
                        if (!attribute.isSecuritySensitive()) {
                            stringBuilder.append(" (=").append(value).append(")");
                        }
                        operativeDescription = stringBuilder.toString();
                    }
                    else {
                        final StringBuilder stringBuilder = new StringBuilder("Configuration property '").append(fullName).append("'");
                        if (!attribute.isSecuritySensitive()) {
                            stringBuilder.append(" (=").append(value).append(")");
                        }
                        stringBuilder.append(" conflicts with ").append(operativeDescription);
                        problems.addError(stringBuilder.toString());
                    }
                }
            }
        }

        private void warnLegacy(String fullName)
        {
            problems.addWarning("Configuration %s", getLegacyDescription(fullName));
        }

        private void defaultLegacy(String fullName)
        {
            problems.addError("Application default %s", getLegacyDescription(fullName));
        }

        private String getLegacyDescription(String fullName)
        {
            String replacement = "deprecated.";
            if (attribute.getInjectionPoint() != null) {
                replacement = format("replaced. Use '%s' instead.", prefix + attribute.getInjectionPoint().getProperty());
            }
            return format("property '%s' has been %s", fullName, replacement);
        }
    }

    private Object getInjectedValue(AttributeMetadata attribute, InjectionPointMetaData injectionPoint, String prefix, boolean isDefault, Problems problems)
            throws InvalidConfigurationException
    {
        // Get the property value
        String name = prefix + injectionPoint.getProperty();
        final ConfigMap configMap = injectionPoint.getConfigMap();
        if (configMap != null) {
            return getInjectedMap(attribute, injectionPoint, name + ".", problems, configMap.key(), configMap.value());
        }

        String value = properties.get(name);
        if (isDefault || value == null) {
            value = applicationDefaults.get(name);
        }
        if (value == null) {
            return null;
        }

        // coerce the property value to the final type
        Class<?> propertyType = injectionPoint.getSetter().getParameterTypes()[0];

        Object finalValue = coerce(propertyType, value);
        final String valueDescription;
        if (attribute.isSecuritySensitive()) {
            valueDescription = "";
        }
        else {
            valueDescription = " '" + value + "'";
        }
        if (finalValue == null) {
            throw new InvalidConfigurationException(format("Could not coerce value%s to %s (property '%s') in order to call [%s]",
                    valueDescription,
                    propertyType.getName(),
                    name,
                    injectionPoint.getSetter().toGenericString()));
        }
        usedProperties.add(name);
        unusedProperties.remove(name);
        return finalValue;
    }

    private <K,V> Map<K, V> getInjectedMap(AttributeMetadata attribute, InjectionPointMetaData injectionPoint, String name, Problems problems, Class<K> keyClass, Class<V> valueClass)
    {
        final boolean valueIsConfigClass = isConfigClass(valueClass);

        final HashSet<String> keySet = new HashSet<>();
        for (String key : properties.keySet()) {
            if (key.startsWith(name)) {
                final String keySuffix = key.substring(name.length());
                if (valueIsConfigClass) {
                    keySet.add(keySuffix.split("\\.", 2)[0]);
                }
                else if (keySuffix.contains(".")) {
                    problems.addError("Configuration map has non-configuration value class %s, so key '%s' cannot be followed by '.' (property '%s') for call [%s]",
                            valueClass.getName(),
                            keySuffix.split("\\.", 2)[0],
                            key,
                            injectionPoint.getSetter().toGenericString());
                }
                else {
                    keySet.add(keySuffix);
                }
            }
        }

        final Map<K, String> coercedKeyMap = new HashMap<>();

        final Builder<K, V> builder = ImmutableMap.builder();
        for (String keyString : keySet) {
            K key = (K) coerce(keyClass, keyString);
            if (key == null) {
                problems.addError("Could not coerce map key '%s' to %s (property%s '%s') in order to call [%s]",
                        keyString,
                        keyClass.getName(),
                        valueIsConfigClass ? " prefix" : "",
                        name + keyString,
                        injectionPoint.getSetter().toGenericString());
                continue;
            }

            final String oldkeyString = coercedKeyMap.put(key, keyString);
            if (oldkeyString != null) {
                problems.addError("Configuration property prefixes ('%s' and '%s') convert to the same map key, preventing creation of map for call [%s]",
                        name + oldkeyString,
                        name + keyString,
                        injectionPoint.getSetter().toGenericString());
                continue;
            }
            final V value;
            if (valueIsConfigClass) {
                try {
                    value = build(valueClass, name + keyString, false, problems);
                }
                catch (ConfigurationException ignored) {
                    continue;
                }
            }
            else {
                value = (V) coerce(valueClass, properties.get(name + keyString));
                if (value == null) {
                    final String valueDescription;
                    if (attribute.isSecuritySensitive()) {
                        valueDescription = "";
                    }
                    else {
                        valueDescription = " '" + properties.get(name + keyString) + "'";
                    }
                    problems.addError("Could not coerce value%s to %s (property '%s') in order to call [%s]",
                            valueDescription,
                            valueClass.getName(),
                            name + keyString,
                            injectionPoint.getSetter().toGenericString());
                    continue;
                }
                usedProperties.add(name + keyString);
                unusedProperties.remove(name + keyString);
            }
            builder.put(key, value);
        }

        return builder.build();
    }

    private static Object coerce(Class<?> type, String value)
    {
        if (type.isPrimitive() && value == null) {
            return null;
        }

        try {
            if (String.class.isAssignableFrom(type)) {
                return value;
            }
            else if (Boolean.class.isAssignableFrom(type) || Boolean.TYPE.isAssignableFrom(type)) {
                return Boolean.valueOf(value);
            }
            else if (Byte.class.isAssignableFrom(type) || Byte.TYPE.isAssignableFrom(type)) {
                return Byte.valueOf(value);
            }
            else if (Short.class.isAssignableFrom(type) || Short.TYPE.isAssignableFrom(type)) {
                return Short.valueOf(value);
            }
            else if (Integer.class.isAssignableFrom(type) || Integer.TYPE.isAssignableFrom(type)) {
                return Integer.valueOf(value);
            }
            else if (Long.class.isAssignableFrom(type) || Long.TYPE.isAssignableFrom(type)) {
                return Long.valueOf(value);
            }
            else if (Float.class.isAssignableFrom(type) || Float.TYPE.isAssignableFrom(type)) {
                return Float.valueOf(value);
            }
            else if (Double.class.isAssignableFrom(type) || Double.TYPE.isAssignableFrom(type)) {
                return Double.valueOf(value);
            }
        }
        catch (Exception ignored) {
            // ignore the random exceptions from the built in types
            return null;
        }

        // Look for a static fromString(String) method
        try {
            Method fromString = type.getMethod("fromString", String.class);
            if (fromString.getReturnType().isAssignableFrom(type)) {
                return fromString.invoke(null, value);
            }
        }
        catch (Throwable ignored) {
        }

        // Look for a static valueOf(String) method
        try {
            Method valueOf = type.getMethod("valueOf", String.class);
            if (valueOf.getReturnType().isAssignableFrom(type)) {
                return valueOf.invoke(null, value);
            }
        }
        catch (Throwable ignored) {
        }

        // Look for a constructor taking a string
        try {
            Constructor<?> constructor = type.getConstructor(String.class);
            return constructor.newInstance(value);
        }
        catch (Throwable ignored) {
        }

        return null;
    }

    private static class ConfigurationHolder<T>
    {
        private final T instance;
        private final Problems problems;

        private ConfigurationHolder(T instance, Problems problems)
        {
            this.instance = instance;
            this.problems = problems;
        }
    }

    private static List<ConfigurationProvider<?>> getAllProviders(Module... modules)
    {
        final List<ConfigurationProvider<?>> providers = Lists.newArrayList();

        ElementsIterator elementsIterator = new ElementsIterator(modules);
        for (final Element element : elementsIterator) {
            element.acceptVisitor(new DefaultElementVisitor<Void>()
            {
                @Override
                public <T> Void visit(Binding<T> binding)
                {
                    // look for ConfigurationProviders...
                    if (binding instanceof ProviderInstanceBinding) {
                        ProviderInstanceBinding<?> providerInstanceBinding = (ProviderInstanceBinding<?>) binding;
                        Provider<?> provider = providerInstanceBinding.getProviderInstance();
                        if (provider instanceof ConfigurationProvider) {
                            ConfigurationProvider<?> configurationProvider = (ConfigurationProvider<?>) provider;
                            providers.add(configurationProvider);
                        }
                    }
                    return null;
                }
            });
        }
        return providers;
    }
}
