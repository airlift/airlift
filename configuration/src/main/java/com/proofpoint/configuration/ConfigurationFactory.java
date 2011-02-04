package com.proofpoint.configuration;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MapMaker;
import com.google.inject.ConfigurationException;
import com.google.inject.spi.Message;
import com.proofpoint.configuration.ConfigurationMetadata.AttributeMetadata;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.proofpoint.configuration.Problems.exceptionFor;
import static java.lang.String.format;

public class ConfigurationFactory
{
    private final Map<String, String> properties;
    private final Problems.Monitor monitor;
    private final ConcurrentMap<Class<?>, ConfigurationMetadata<?>> metadataCache;
    private final ConcurrentMap<ConfigurationProvider<?>, Object> instanceCache = new ConcurrentHashMap<ConfigurationProvider<?>, Object>();

    public ConfigurationFactory(Map<String, String> properties)
    {
        this(properties, Problems.NULL_MONITOR);
    }

    ConfigurationFactory(Map<String, String> properties, final Problems.Monitor monitor)
    {
        this.monitor = monitor;
        this.properties = ImmutableMap.copyOf(properties);

        metadataCache = new MapMaker().weakKeys().weakValues().makeComputingMap(new Function<Class<?>, ConfigurationMetadata<?>>()
        {
            @Override
            public ConfigurationMetadata<?> apply(Class<?> configClass)
            {
                return ConfigurationMetadata.getConfigurationMetadata(configClass, monitor);
            }
        });
    }

    public Map<String, String> getProperties()
    {
        return properties;
    }

    Map<ConfigurationProvider<?>, Object> getInstanceCache()
    {
        return ImmutableMap.copyOf(instanceCache);
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

        // check for a prebuilt instance
        T instance = (T) instanceCache.get(configurationProvider);
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
        T existingValue = (T) instanceCache.putIfAbsent(configurationProvider, instance);
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
            prefix = prefix + ".";
        }

        ConfigurationMetadata<T> configurationMetadata = (ConfigurationMetadata<T>) metadataCache.get(configClass);
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

        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        for (ConstraintViolation violation : validator.validate(instance)) {
            problems.addError("Constraint violation with property prefix '%s': %s %s (for class %s)",
                    prefix, violation.getPropertyPath(), violation.getMessage(), configClass.getName());
        }

        problems.throwIfHasErrors();

        return new ConfigurationHolder<T>(instance, problems);
    }

    private <T> T newInstance(ConfigurationMetadata<T> configurationMetadata)
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
        String value = injectionPoint == null ? null : properties.get(prefix + injectionPoint.getProperty());

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
}
