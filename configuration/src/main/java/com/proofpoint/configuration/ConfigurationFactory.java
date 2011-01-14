package com.proofpoint.configuration;

import com.google.common.collect.ImmutableMap;
import com.google.inject.ConfigurationException;
import com.proofpoint.configuration.ConfigurationMetadata.AttributeMetadata;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.CallbackFilter;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.FixedValue;
import net.sf.cglib.proxy.NoOp;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.proofpoint.configuration.Errors.exceptionFor;
import static java.lang.String.format;

public class ConfigurationFactory
{
    private final Map<String, String> properties;

    public ConfigurationFactory(Map<String, String> properties)
    {
        this.properties = ImmutableMap.copyOf(properties);
    }

    /**
     * Temporary method - returns an bridge factory that calls {@link #createLegacyConfig(Class)}
     * instead of {@link #build(Class)}
     *
     * @return bridged factory
     */
    @Deprecated
    public ConfigurationFactory createLegacyFactory()
    {
        return new ConfigurationFactory(properties)
        {
            @SuppressWarnings({"deprecation"})
            @Override
            public <T> T build(Class<T> configClass)
            {
                return createLegacyConfig(configClass);
            }

            @Override
            public <T> T build(Class<T> configClass, String prefix, T instance)
            {
                throw new UnsupportedOperationException();
            }
        };
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public <T> T build(Class<T> configClass)
    {
        return build(configClass, "", null);
    }

    public <T> T build(Class<T> configClass, String prefix, T instance)
    {
        if (configClass == null) {
            throw new NullPointerException("configClass is null");
        }

        if (prefix == null) {
            prefix = "";
        } else if (!prefix.isEmpty()) {
            prefix = prefix + ".";
        }

        ConfigurationMetadata<T> configurationMetadata = ConfigurationMetadata.getValidConfigurationMetadata(configClass);

        if (instance == null) {
            instance = newInstance(configurationMetadata);
        }

        Errors errors = new Errors();
        for (AttributeMetadata attribute : configurationMetadata.getAttributes().values()) {
            try {
                setConfigProperty(instance,attribute, prefix);
            }
            catch (InvalidConfigurationException e) {
                errors.add(e.getCause(), e.getMessage());
            }
        }
        errors.throwIfHasErrors();
        
        return instance;
    }

    private <T> T newInstance(ConfigurationMetadata<T> configurationMetadata)
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

    private <T> void setConfigProperty(T instance, AttributeMetadata attribute, String prefix)
            throws InvalidConfigurationException
    {
        // Get property value
        Object value = getPropertyValue(attribute, prefix, false);

        // If we did not get a value, do not call the setter
        if (value == null) {
            return;
        }

        try {
            attribute.getSetter().invoke(instance, value);
        }
        catch (Throwable e) {
            if (e instanceof InvocationTargetException && e.getCause() != null) {
                e = e.getCause();
            }
            throw new InvalidConfigurationException(e, "Error invoking configuration method [%s]", attribute.getSetter().toGenericString());
        }
    }

    private String findOperativeProperty(AttributeMetadata attribute, String prefix)
        throws ConfigurationException
    {
        String operativeName = attribute.getPropertyName() == null ? null : prefix + attribute.getPropertyName();
        String operativeValue = operativeName == null ? null : properties.get(operativeName);

        Errors errors = new Errors();

        for (String deprecatedName : attribute.getDeprecatedNames()) {
            String fullName = prefix + deprecatedName;
            String value = properties.get(fullName);
            if (value != null) {
                // todo add something to deal with the presence of deprecated config

                if (operativeValue == null) {
                    operativeValue = value;
                    operativeName = fullName;
                } else if (value != operativeValue) {
                    errors.add("Value for property '%s' (=%s) conflicts with property '%s' (=%s)", fullName, value, operativeName, operativeValue);
                }
            }
        }

        errors.throwIfHasErrors();
        return operativeName;
    }

    private Object getPropertyValue(AttributeMetadata attribute, String prefix, boolean isLegacy)
            throws InvalidConfigurationException
    {
        // Get the property value
        String propertyName = findOperativeProperty(attribute, prefix);
        String value = propertyName == null ? null : properties.get(propertyName);

        // For legacy configuration objects...
        // If no value specified, check for @Default
        // todo remove this when legacy is removed
        if (isLegacy && value == null && attribute.getGetter().isAnnotationPresent(Default.class)) {
            value =  attribute.getGetter().getAnnotation(Default.class).value();
        }

        if (value == null) {
            return null;
        }

        // coerce the property value to the final type
        Class<?> propertyType;
        if (isLegacy) {
            propertyType = attribute.getGetter().getReturnType();
        }
        else {
            propertyType = attribute.getSetter().getParameterTypes()[0];
        }

        Object finalValue = coerce(propertyType, value);
        if (finalValue == null) {
            throw new InvalidConfigurationException(format("Could not coerce value '%s' to %s for attribute '%s' (property '%s') in [%s]",
                    value,
                    propertyType.getName(),
                    attribute.getName(),
                    propertyName,
                    attribute.getSetter().toGenericString()));
        }
        return finalValue;
    }

    @Deprecated
    public <T> T createLegacyConfig(Class<T> configClass)
    {
        Errors errors = new Errors();
        
        // cglib callbacks
        ArrayList<Callback> callbacks = new ArrayList<Callback>();
        callbacks.add(NoOp.INSTANCE);
        final Map<Method, Integer> slots = new HashMap<Method, Integer>();
        int count = 1;

        // normal injections

        for (Method method : configClass.getMethods()) {
            Config config = method.getAnnotation(Config.class);
            if (config != null) {

                AttributeMetadata attributeMetadata = new AttributeMetadata(configClass, method.getName(), null, config.value(), null, method, null);
                Object value = null;
                try {
                    value = getPropertyValue(attributeMetadata, "", true);
                }
                catch (InvalidConfigurationException e) {
                    errors.add(e.getCause(), e.getMessage());
                }

                if (value != null) {
                    slots.put(method, count++);
                    callbacks.add(new ConstantValue(value));
                }
            }
            else if (Modifier.isAbstract(method.getModifiers())) {
                errors.add("Method [%s] is abstract but does not have an @Config annotation", method.toGenericString());
            }
        }

        T result = null;
        try {
            Enhancer e = new Enhancer();
            e.setSuperclass(configClass);
            e.setCallbackFilter(new CallbackFilter()
            {
                public int accept(Method method)
                {
                    return slots.containsKey(method) ? slots.get(method) : 0;
                }
            });
            e.setCallbacks(callbacks.toArray(new Callback[callbacks.size()]));
            result = (T) e.create();
        }
        catch (Exception e) {
            errors.add(e, "Error creating instance of configuration class [%s]", configClass.getName());
        }

        errors.throwIfHasErrors();
        return result;
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

    private static final class ConstantValue implements FixedValue
    {
        private final Object value;

        private ConstantValue(Object value)
        {
            this.value = value;
        }

        public Object loadObject()
        {
            return value;
        }
    }
}
