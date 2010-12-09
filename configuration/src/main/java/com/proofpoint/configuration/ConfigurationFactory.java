package com.proofpoint.configuration;

import com.google.common.collect.ImmutableList;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.CallbackFilter;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.FixedValue;
import net.sf.cglib.proxy.NoOp;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

import static com.proofpoint.configuration.Errors.exceptionFor;

public class ConfigurationFactory
{
    private final Map<String, String> properties;

    public ConfigurationFactory(Map<String, String> properties)
    {
        this.properties = ImmutableMap.copyOf(properties);
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

        List<Method> methods = getAnnotatedMethods(configClass);

        if (methods.isEmpty()) {
            throw exceptionFor("Configuration class %s does not have any @Config annotations", configClass.getName());
        }

        if (instance == null) {
            instance = newInstance(configClass);
        }

        Errors errors = new Errors();
        for (Method method : methods) {
            setConfigProperty(instance, method, prefix, errors);
        }
        errors.throwIfHasErrors();
        
        return instance;
    }

    private List<Method> getAnnotatedMethods(Class<?> configClass)
    {
        // TODO: https://jira.proofpoint.com/browse/PLATFORM-10
        ImmutableList.Builder<Method> builder = ImmutableList.builder();
        for (Method method : configClass.getMethods()) {
            if (method.isAnnotationPresent(Config.class)) {
                builder.add(method);
            }
        }

        return builder.build();
    }
    
    private <T> T newInstance(Class<T> configClass)
    {
        // verify there is a public no-arg constructor
        Constructor<T> constructor;
        try {
            constructor = configClass.getDeclaredConstructor();
            if (!Modifier.isPublic(constructor.getModifiers())) {
                throw exceptionFor("Constructor %s is not public", constructor.toGenericString());
            }
        }
        catch (Exception e) {
            throw exceptionFor("Configuration class %s does not have a public no-arg constructor", configClass.getName());
        }

        try {
            return constructor.newInstance();
        }
        catch (Throwable e) {
            if (e instanceof InvocationTargetException && e.getCause() != null) {
                e = e.getCause();
            }
            throw exceptionFor(e, "Error creating instance of configuration class [%s]", configClass.getName());
        }
    }

    private <T> void setConfigProperty(T instance, Method method, String prefix, Errors errors)
    {
        // verify configuration method
        boolean valid = true;
        if (method.getParameterTypes().length != 1) {
            errors.add("@Config method [%s] must take only one parameter", method.toGenericString());
            valid = false;
        }

        if (!Modifier.isPublic(method.getModifiers())) {
            errors.add("@Config method [%s] is not public", method.toGenericString());
            valid = false;
        }

        // Get property value
        // Even if not valid still try to get the property value so we recored the errors
        Object value = getPropertyValue(method, prefix, errors, false);

        // At this point, return if not valid
        if (!valid || value == null) {
            return;
        }

        try {
            method.invoke(instance, value);
        }
        catch (Throwable e) {
            if (e instanceof InvocationTargetException && e.getCause() != null) {
                e = e.getCause();
            }
            errors.add(e, "Error invoking configuration method [%s]", method.toGenericString());
        }
    }

    private Object getPropertyValue(Method method, String prefix, Errors errors, boolean isLegacy)
    {
        Config annotation = method.getAnnotation(Config.class);

        // Get the property value
        String propertyName = prefix + annotation.value();
        String value = properties.get(propertyName);

        // For legacy configuration objects...
        // If no value specified, check for @Default
        if (isLegacy && value == null && method.isAnnotationPresent(Default.class)) {
            value = method.getAnnotation(Default.class).value();
        }

        // coerce the property value to the final type
        if (value != null) {
            Class<?> propertyType;
            if (isLegacy) {
                propertyType = method.getReturnType();
            }
            else {
                propertyType = method.getParameterTypes()[0];
            }

            Object finalValue = coerce(propertyType, value);
            if (finalValue == null) {
                errors.add("Could not coerce value '%s' to %s for property '%s' in [%s]",
                        value,
                        propertyType.getName(),
                        propertyName, method.toGenericString());                
            }
            return finalValue;
        }

        if (Modifier.isAbstract(method.getModifiers())) {
            // no default (via impl or @Default) and no configured value
            errors.add("No value present for '%s' in [%s]", propertyName, method.toGenericString());
        }

        return null;
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
            if (method.isAnnotationPresent(Config.class)) {

                final Object finalValue = getPropertyValue(method, "", errors, true);

                if (finalValue != null) {
                    slots.put(method, count++);
                    callbacks.add(new FixedValue()
                    {
                        public Object loadObject() throws Exception
                        {
                            return finalValue;
                        }
                    });
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

}
