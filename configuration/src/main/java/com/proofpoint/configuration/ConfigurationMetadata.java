package com.proofpoint.configuration;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.inject.ConfigurationException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ConfigurationMetadata<T>
{

    public static <T> ConfigurationMetadata<T> getValidConfigurationMetadata(Class<T> configClass) throws ConfigurationException
    {
        ConfigurationMetadata<T> metadata = getConfigurationMetadata(configClass);
        metadata.getErrors().throwIfHasErrors();
        return metadata;
    }

    public static <T> ConfigurationMetadata<T> getConfigurationMetadata(Class<T> configClass)
    {
        ConfigurationMetadata<T> metadata = new ConfigurationMetadata<T>(configClass, false);
        return metadata;
    }

    public static <T> ConfigurationMetadata<T> getValidLegacyConfigurationMetadata(Class<T> configClass) throws ConfigurationException
    {
        ConfigurationMetadata<T> metadata = getLegacyConfigurationMetadata(configClass);
        metadata.getErrors().throwIfHasErrors();
        return metadata;
    }

    public static <T> ConfigurationMetadata<T> getLegacyConfigurationMetadata(Class<T> configClass)
    {
        ConfigurationMetadata<T> metadata = new ConfigurationMetadata<T>(configClass, true);
        return metadata;
    }

    private final Class<T> configClass;
    private final Errors errors = new Errors();
    private final Constructor<T> constructor;
    private final Map<String,AttributeMetadata> attributes;


    private ConfigurationMetadata(Class<T> configClass, boolean legacy)
    {
        if (configClass == null) {
            throw new NullPointerException("configClass is null");
        }

        this.configClass = configClass;
        if (!legacy && Modifier.isAbstract(configClass.getModifiers())) {
            errors.add("Config class [%s] is abstract", configClass.getName());
        }
        if (!Modifier.isPublic(configClass.getModifiers())) {
            errors.add("Config class [%s] is not public", configClass.getName());
        }

        // verify there is a public no-arg constructor
        Constructor<T> constructor = null;
        try {
            constructor = configClass.getDeclaredConstructor();
            if (!Modifier.isPublic(constructor.getModifiers())) {
                errors.add("Constructor [%s] is not public", constructor.toGenericString());
            }
        }
        catch (Exception e) {
            errors.add("Configuration class [%s] does not have a public no-arg constructor", configClass.getName());
        }
        this.constructor = constructor;

        // Create attribute metadata
        Map<String, AttributeMetadata> attributes = Maps.newTreeMap();
        for (Method configMethod : findConfigMethods(configClass)) {
            Config config = configMethod.getAnnotation(Config.class);
            AttributeMetadata attribute = buildAttributeMetadata(configClass, config, configMethod, legacy);
            if (attribute != null) {
                if (attributes.containsKey(attribute.getName())) {
                    errors.add("Configuration class [%s] Multiple methods are annotated for @Config attribute [%s]", configClass.getName(), attribute.getName());
                }
                attributes.put(attribute.getName(), attribute);
            }
        }
        this.attributes = ImmutableSortedMap.copyOf(attributes);

        // find invalid config methods not skipped by findConfigMethods()
        for (Class<?> clazz = configClass; !clazz.equals(Object.class); clazz = clazz.getSuperclass()) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Config.class)) {
                    if (!Modifier.isPublic(method.getModifiers())) {
                        errors.add("@Config method [%s] is not public", method.toGenericString());
                    }
                    if (Modifier.isStatic(method.getModifiers())) {
                        errors.add("@Config method [%s] is static", method.toGenericString());
                    }
                }
            }
        }

        if (errors.getErrors().isEmpty() && this.attributes.isEmpty()) {
            errors.add("Configuration class [%s] does not have any @Config annotations", configClass.getName());
        }
    }

    public Class<T> getConfigClass()
    {
        return configClass;
    }

    public Constructor<T> getConstructor()
    {
        return constructor;
    }

    public Map<String, AttributeMetadata> getAttributes()
    {
        return attributes;
    }

    public Errors getErrors()
    {
        return errors;
    }

    private AttributeMetadata buildAttributeMetadata(Class<?> configClass, Config config, Method configMethod, boolean legacy)
    {
        if (config.value() == null || config.value().isEmpty()) {
            errors.add("@Config method [%s] annotation has an empty value", configMethod.toGenericString());
            return null;
        }

        String description = null;
        if (configMethod.isAnnotationPresent(ConfigDescription.class)) {
            description = configMethod.getAnnotation(ConfigDescription.class).value();
        }


        // determine the attribute name
        String attributeName = configMethod.getName();
        if (attributeName.startsWith("set")) {
            // annotated setter
            attributeName = attributeName.substring(3);

            // verify parameters
            if (configMethod.getParameterTypes().length != 1) {
                errors.add("@Config setter [%s] does not have exactly one parameter", configMethod.toGenericString());
            }

            // find the getter
            Method getter = null;
            try {
                getter = configClass.getMethod("get" + attributeName);
            }
            catch (Exception ignored) {
                // it is ok to have a write only attribute
            }
            return new AttributeMetadata(configClass, attributeName, description, config.value(), getter, configMethod);
        } else if (attributeName.startsWith("get")) {
            // annotated getter
            attributeName = attributeName.substring(3);

            // verify parameters
            if (configMethod.getParameterTypes().length != 0) {
                errors.add("@Config getter [%s] is has parameters", configMethod.toGenericString());
            }

            // method must return something
            if (configMethod.getReturnType() == Void.TYPE) {
                errors.add("@Config getter [%s] does not return anything", configMethod.toGenericString());
            }

            // find the setter
            Method setter = null;
            if (!legacy) {
                setter = findSetter(configClass, configMethod, attributeName);
                if (setter == null) {
                    return null;
                }
            }
            return new AttributeMetadata(configClass, attributeName, description, config.value(), configMethod, setter);
        } else if (attributeName.startsWith("is")) {
            // annotated is method
            attributeName = attributeName.substring(2);

            // verify parameters
            if (configMethod.getParameterTypes().length != 0) {
                errors.add("@Config is method [%s] is has parameters", configMethod.toGenericString());
            }
            // is method must return boolean or Boolean
            if (!configMethod.getReturnType().equals(boolean.class) && !configMethod.getReturnType().equals(Boolean.class)) {
                errors.add("@Config is method [%s] does not return boolean or Boolean", configMethod.toGenericString());
            }

            // find the setter
            Method setter = findSetter(configClass, configMethod, attributeName);
            if (setter == null) {
                return null;
            }
            return new AttributeMetadata(configClass, attributeName, description, config.value(), configMethod, setter);
        } else {
            errors.add("@Config method [%s] is not a valid getter or setter", configMethod.toGenericString());
            return null;
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConfigurationMetadata<?> that = (ConfigurationMetadata<?>) o;

        if (!configClass.equals(that.configClass)) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        return configClass.hashCode();
    }

    @Override
    public String toString()
    {
        final StringBuffer sb = new StringBuffer();
        sb.append("ConfigurationMetadata");
        sb.append("{configClass=").append(configClass);
        sb.append('}');
        return sb.toString();
    }

    public static class AttributeMetadata {
        private final Class<?> configClass;
        private final String name;
        private final String description;
        private final Method getter;
        private final Method setter;
        private final String propertyName;

        public AttributeMetadata(Class<?> configClass, String name, String description, String propertyName, Method getter, Method setter)
        {
            if (configClass == null) {
                throw new NullPointerException("configClass is null");
            }
            if (name == null) {
                throw new NullPointerException("name is null");
            }
            if (propertyName == null) {
                throw new NullPointerException("propertyName is null");
            }
            // todo add back when legacy config support is removed
//            if (setter == null) {
//                throw new NullPointerException("setter is null");
//            }
            this.configClass = configClass;
            this.name = name;
            this.description = description;
            this.propertyName = propertyName;
            this.getter = getter;
            this.setter = setter;
        }

        public Class<?> getConfigClass()
        {
            return configClass;
        }

        public String getName()
        {
            return name;
        }

        public String getDescription()
        {
            return description;
        }

        public String getPropertyName()
        {
            return propertyName;
        }

        public Method getGetter()
        {
            return getter;
        }

        public Method getSetter()
        {
            return setter;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            AttributeMetadata that = (AttributeMetadata) o;

            if (!configClass.equals(that.configClass)) return false;
            if (!name.equals(that.name)) return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = configClass.hashCode();
            result = 31 * result + name.hashCode();
            return result;
        }

        @Override
        public String toString()
        {
            final StringBuffer sb = new StringBuffer();
            sb.append("AttributeMetadata");
            sb.append("{name='").append(name).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * Find methods that are tagged as managed somewhere in the hierarchy
     *
     * @param configClass the class to analyze
     * @return a map that associates a concrete method to the actual method tagged as managed
     *         (which may belong to a different class in class hierarchy)
     */
    private static Collection<Method> findConfigMethods(Class<?> configClass)
    {
        List<Method> result = new ArrayList<Method>();

        // gather all publicly available methods
        // this returns everything, even if it's declared in a parent
        for (Method method : configClass.getMethods()) {
            // skip methods that are used internally by the vm for implementing covariance, etc
            if (method.isSynthetic() || method.isBridge() || Modifier.isStatic(method.getModifiers())) {
                continue;
            }

            // look for annotations recursively in super-classes or interfaces
            Method managedMethod = findConfigMethod(configClass, method.getName(), method.getParameterTypes());
            if (managedMethod != null) {
                result.add(managedMethod);
            }
        }

        return result;
    }

    public static Method findConfigMethod(Class<?> configClass, String methodName, Class<?>... paramTypes)
    {
        try {
            Method method = configClass.getDeclaredMethod(methodName, paramTypes);
            if (method.isAnnotationPresent(Config.class)) {
                return method;
            }
        }
        catch (NoSuchMethodException e) {
            // ignore
        }

        if (configClass.getSuperclass() != null) {
            Method managedMethod = findConfigMethod(configClass.getSuperclass(), methodName, paramTypes);
            if (managedMethod != null) {
                return managedMethod;
            }
        }

        for (Class<?> iface : configClass.getInterfaces()) {
            Method managedMethod = findConfigMethod(iface, methodName, paramTypes);
            if (managedMethod != null) {
                return managedMethod;
            }
        }

        return null;
    }

    private Method findSetter(Class<?> configClass, Method configMethod, String attributeName)
    {
        // find the setter
        String setterName = "set" + attributeName;
        List<Method> setters = new ArrayList<Method>();
        for (Class<?> clazz = configClass; !clazz.equals(Object.class); clazz = clazz.getSuperclass()) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(setterName) && method.getParameterTypes().length == 1) {
                    if (!method.isSynthetic() && !method.isBridge() && !Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers())) {
                        setters.add(method);
                    }
                }
            }
        }

        // too small
        if (setters.isEmpty()) {
            // look for setter with no parameters
            errors.add("No setter for @Config method [%s]", configMethod.toGenericString());
            return null;
        }

        // too big
        if (setters.size() > 1) {
            // To many setters, annotate setter instead of getter
            errors.add("Multiple setters found for @Config getter [%s]; Move annotation to setter instead: %s", configMethod.toGenericString(), setters);
            return null;
        }

        // just right
        Method setter = setters.get(0);
        return setter;
    }
}
