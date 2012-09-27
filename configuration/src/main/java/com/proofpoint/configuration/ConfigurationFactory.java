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
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.inject.Binding;
import com.google.inject.ConfigurationException;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.spi.DefaultElementVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.Message;
import com.google.inject.spi.ProviderInstanceBinding;
import com.proofpoint.configuration.ConfigurationMetadata.AttributeMetadata;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.proofpoint.configuration.Problems.exceptionFor;
import static java.lang.String.format;

public final class ConfigurationFactory
{
    private static final Validator VALIDATOR = Validation.byProvider(ApacheValidationProvider.class).configure().buildValidatorFactory().getValidator();

    private final Map<String, String> properties;
    private final Problems.Monitor monitor;
    private final Set<String> unusedProperties = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Collection<String> initialErrors;
    private final LoadingCache<Class<?>, ConfigurationMetadata<?>> metadataCache;
    private final ConcurrentMap<ConfigurationProvider<?>, Object> instanceCache = new ConcurrentHashMap<>();
    private final Set<String> usedProperties = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Set<ConfigurationProvider<?>> registeredProviders = Collections.newSetFromMap(new ConcurrentHashMap<ConfigurationProvider<?>, Boolean>());

    public ConfigurationFactory(Map<String, String> properties)
    {
        this(properties, Collections.<String>emptySet(), ImmutableList.<String>of(), Problems.NULL_MONITOR);
    }

    ConfigurationFactory(Map<String, String> properties, Set<String> expectToUse, Collection<String> errors, final Monitor monitor)
    {
        this.monitor = monitor;
        this.properties = ImmutableMap.copyOf(properties);
        unusedProperties.addAll(expectToUse);
        initialErrors = ImmutableList.copyOf(errors);

        metadataCache = CacheBuilder.newBuilder().weakKeys().weakValues()
                .build(new CacheLoader<Class<?>, ConfigurationMetadata<?>>()
                {
                    @Override
                    public ConfigurationMetadata<?> load(Class<?> configClass)
                    {
                        return ConfigurationMetadata.getConfigurationMetadata(configClass, monitor);
                    }
                });
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
        @SuppressWarnings("unchecked") T instance = (T) instanceCache.get(configurationProvider);
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
        @SuppressWarnings("unchecked") T existingValue = (T) instanceCache.putIfAbsent(configurationProvider, instance);
        // if key was already associated with a value, there was a
        // creation race and we lost. Just use the winners' instance;
        if (existingValue != null) {
            return existingValue;
        }
        return instance;
    }

    private <T> ConfigurationHolder<T> build(Class<T> configClass, String prefix)
    {
        if (configClass == null) {
            throw new NullPointerException("configClass is null");
        }

        if (prefix == null) {
            prefix = "";
        } else if (!prefix.isEmpty()) {
            prefix += ".";
        }

        @SuppressWarnings("unchecked") ConfigurationMetadata<T> configurationMetadata = (ConfigurationMetadata<T>) metadataCache.getUnchecked(configClass);
        configurationMetadata.getProblems().throwIfHasErrors();

        T instance = newInstance(configurationMetadata);

        Problems problems = new Problems(monitor);

        for (AttributeMetadata attribute : configurationMetadata.getAttributes().values()) {
            try {
                setConfigProperty(instance, attribute, prefix, problems);
            } catch (InvalidConfigurationException e) {
                problems.addError(e.getCause(), e.getMessage());
            }
        }

        // Check that none of the defunct properties are still in use
        if (configClass.isAnnotationPresent(DefunctConfig.class)) {
            for (String value : configClass.getAnnotation(DefunctConfig.class).value()) {
                if (!value.isEmpty() && properties.get(prefix + value) != null) {
                    problems.addError("Defunct property '%s' (class [%s]) cannot be configured.", value, configClass.toString());
                }
            }
        }

        for (ConstraintViolation<?> violation : VALIDATOR.validate(instance)) {
            problems.addError("Constraint violation with property prefix '%s': %s %s (for class %s)",
                    prefix, violation.getPropertyPath(), violation.getMessage(), configClass.getName());
        }

        problems.throwIfHasErrors();

        return new ConfigurationHolder<T>(instance, problems);
    }

    private static <T> T newInstance(ConfigurationMetadata<T> configurationMetadata)
    {
        try {
            return configurationMetadata.getConstructor().newInstance();
        } catch (Throwable e) {
            if (e instanceof InvocationTargetException && e.getCause() != null) {
                e = e.getCause();
            }
            throw exceptionFor(e, "Error creating instance of configuration class [%s]", configurationMetadata.getConfigClass().getName());
        }
    }

    private <T> void setConfigProperty(T instance, AttributeMetadata attribute, String prefix, Problems problems)
            throws InvalidConfigurationException
    {
        // Get property value
        ConfigurationMetadata.InjectionPointMetaData injectionPoint = findOperativeInjectionPoint(attribute, prefix, problems);

        // If we did not get an injection point, do not call the setter
        if (injectionPoint == null) {
            return;
        }

        if (injectionPoint.getSetter().isAnnotationPresent(Deprecated.class)) {
            problems.addWarning("Configuration property '%s' is deprecated and should not be used", injectionPoint.getProperty());
        }

        Object value = getInjectedValue(injectionPoint, prefix);

        try {
            injectionPoint.getSetter().invoke(instance, value);
        } catch (Throwable e) {
            if (e instanceof InvocationTargetException && e.getCause() != null) {
                e = e.getCause();
            }
            throw new InvalidConfigurationException(e, "Error invoking configuration method [%s]", injectionPoint.getSetter().toGenericString());
        }
    }

    private ConfigurationMetadata.InjectionPointMetaData findOperativeInjectionPoint(AttributeMetadata attribute, String prefix, Problems problems)
            throws ConfigurationException
    {
        ConfigurationMetadata.InjectionPointMetaData operativeInjectionPoint = attribute.getInjectionPoint();
        String operativeName = null;
        String operativeValue = null;
        if (operativeInjectionPoint != null) {
            operativeName = prefix + operativeInjectionPoint.getProperty();
            operativeValue = properties.get(operativeName);
        }

        for (ConfigurationMetadata.InjectionPointMetaData injectionPoint : attribute.getLegacyInjectionPoints()) {
            String fullName = prefix + injectionPoint.getProperty();
            String value = properties.get(fullName);
            if (value != null) {
                String replacement = "deprecated.";
                if (attribute.getInjectionPoint() != null) {
                    replacement = format("replaced. Use '%s' instead.", prefix + attribute.getInjectionPoint().getProperty());
                }
                problems.addWarning("Configuration property '%s' has been " + replacement, fullName);

                if (operativeValue == null) {
                    operativeInjectionPoint = injectionPoint;
                    operativeValue = value;
                    operativeName = fullName;
                } else if (!value.equals(operativeValue)) {
                    problems.addError("Value for property '%s' (=%s) conflicts with property '%s' (=%s)", fullName, value, operativeName, operativeValue);
                }
            }
        }

        problems.throwIfHasErrors();
        if (operativeValue == null) {
            // No injection from configuration
            return null;
        }

        return operativeInjectionPoint;
    }

    private Object getInjectedValue(ConfigurationMetadata.InjectionPointMetaData injectionPoint, String prefix)
            throws InvalidConfigurationException
    {
        // Get the property value
        String name = prefix + injectionPoint.getProperty();
        String value = properties.get(name);

        if (value == null) {
            return null;
        }

        // coerce the property value to the final type
        Class<?> propertyType = injectionPoint.getSetter().getParameterTypes()[0];

        Object finalValue = coerce(propertyType, value);
        if (finalValue == null) {
            throw new InvalidConfigurationException(format("Could not coerce value '%s' to %s (property '%s') in order to call [%s]",
                    value,
                    propertyType.getName(),
                    injectionPoint.getProperty(),
                    injectionPoint.getSetter().toGenericString()));
        }
        usedProperties.add(name);
        unusedProperties.remove(name);
        return finalValue;
    }

    private static Object coerce(Class<?> type, String value)
    {
        if (type.isPrimitive() && value == null) {
            return null;
        }


        try {
            if (String.class.isAssignableFrom(type)) {
                return value;
            } else if (Boolean.class.isAssignableFrom(type) || Boolean.TYPE.isAssignableFrom(type)) {
                return Boolean.valueOf(value);
            } else if (Byte.class.isAssignableFrom(type) || Byte.TYPE.isAssignableFrom(type)) {
                return Byte.valueOf(value);
            } else if (Short.class.isAssignableFrom(type) || Short.TYPE.isAssignableFrom(type)) {
                return Short.valueOf(value);
            } else if (Integer.class.isAssignableFrom(type) || Integer.TYPE.isAssignableFrom(type)) {
                return Integer.valueOf(value);
            } else if (Long.class.isAssignableFrom(type) || Long.TYPE.isAssignableFrom(type)) {
                return Long.valueOf(value);
            } else if (Float.class.isAssignableFrom(type) || Float.TYPE.isAssignableFrom(type)) {
                return Float.valueOf(value);
            } else if (Double.class.isAssignableFrom(type) || Double.TYPE.isAssignableFrom(type)) {
                return Double.valueOf(value);
            }
        } catch (Exception ignored) {
            // ignore the random exceptions from the built in types
            return null;
        }

        // Look for a static fromString(String) method
        try {
            Method fromString = type.getMethod("fromString", String.class);
            if (fromString.getReturnType().isAssignableFrom(type)) {
                return fromString.invoke(null, value);
            }
        } catch (Throwable ignored) {
        }

        // Look for a static valueOf(String) method
        try {
            Method valueOf = type.getMethod("valueOf", String.class);
            if (valueOf.getReturnType().isAssignableFrom(type)) {
                return valueOf.invoke(null, value);
            }
        } catch (Throwable ignored) {
        }

        // Look for a constructor taking a string
        try {
            Constructor<?> constructor = type.getConstructor(String.class);
            return constructor.newInstance(value);
        } catch (Throwable ignored) {
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
