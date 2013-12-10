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

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.ImmutableSortedSet.Builder;
import com.google.inject.Key;
import com.proofpoint.configuration.ConfigurationMetadata.AttributeMetadata;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;

import static com.google.common.base.Objects.firstNonNull;
import static com.proofpoint.configuration.ConfigurationMetadata.isConfigClass;

public class ConfigurationInspector
{
    public SortedSet<ConfigRecord<?>> inspect(ConfigurationFactory configurationFactory)
    {
        ImmutableSortedSet.Builder<ConfigRecord<?>> builder = ImmutableSortedSet.naturalOrder();
        for (ConfigurationProvider<?> configurationProvider : configurationFactory.getConfigurationProviders()) {
            builder.add(ConfigRecord.createConfigRecord(configurationProvider));
        }

        return builder.build();
    }

    private static <T> T newDefaultInstance(ConfigurationMetadata<T> configurationMetadata)
    {
        try {
            return configurationMetadata.getConstructor().newInstance();
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private static String getValue(Method getter, Object instance, String defaultValue)
    {
        if (getter == null || instance == null) {
            return defaultValue;
        }

        try {
            Object value = getter.invoke(instance);
            if (value == null) {
                return "null";
            }
            return value.toString();
        }
        catch (Throwable e) {
            return "-- ERROR --";
        }
    }

    public static class ConfigRecord<T> implements Comparable<ConfigRecord<?>>
    {
        private final Key<T> key;
        private final Class<T> configClass;
        private final String prefix;
        private final SortedSet<ConfigAttribute> attributes;

        public static <T> ConfigRecord<T> createConfigRecord(ConfigurationProvider<T> configurationProvider)
        {
            return new ConfigRecord<>(configurationProvider);
        }

        private ConfigRecord(ConfigurationProvider<T> configurationProvider)
        {
            Preconditions.checkNotNull(configurationProvider, "configurationProvider");

            key = configurationProvider.getKey();
            configClass = configurationProvider.getConfigClass();
            prefix = configurationProvider.getPrefix();

            ConfigurationMetadata<T> metadata = configurationProvider.getConfigurationMetadata();

            T instance = null;
            try {
                instance = configurationProvider.get();
            }
            catch (Throwable ignored) {
                // provider could blow up for any reason, which is fine for this code
                // this is catch throwable because we may get an AssertionError
            }

            T defaults = null;
            try {
                defaults = configurationProvider.getDefaults();
            }
            catch (Throwable ignored) {
            }

            String prefix = configurationProvider.getPrefix();
            prefix = prefix == null ? "" : (prefix + ".");

            ImmutableSortedSet.Builder<ConfigAttribute> builder = ImmutableSortedSet.naturalOrder();
            enumerateConfig(metadata, instance, defaults, prefix, builder, "");
            attributes = builder.build();
        }

        private static <T> void enumerateConfig(ConfigurationMetadata<T> metadata, T instance, T defaults, String prefix, Builder<ConfigAttribute> builder, String attributePrefix)
        {
            for (AttributeMetadata attribute : metadata.getAttributes().values()) {
                String propertyName = prefix + attribute.getInjectionPoint().getProperty();
                Method getter = attribute.getGetter();

                String description = firstNonNull(attribute.getDescription(), "");

                final ConfigMap configMap = attribute.getConfigMap();
                if (getter != null && instance != null && !attribute.isSecuritySensitive() && configMap != null) {
                    final Class<?> valueClass = configMap.value();
                    Class<?> valueConfigClass = null;
                    if (isConfigClass(valueClass)) {
                        valueConfigClass = valueClass;
                    }

                    enumerateMap(instance, attributePrefix + attribute.getName(), propertyName, description, getter, valueConfigClass, builder);
                }
                else {
                    String defaultValue = getValue(getter, defaults, "-- none --");
                    String currentValue = getValue(getter, instance, "-- n/a --");

                    builder.add(new ConfigAttribute(attributePrefix + attribute.getName(), propertyName, defaultValue, currentValue, description, attribute.isSecuritySensitive()));
                }
            }
        }

        private static <T, K, V> void enumerateMap(T instance, String attributeName, String propertyName, String description, Method getter, Class<V> valueConfigClass, Builder<ConfigAttribute> builder)
        {
            Map<K, V> map;
            try {
                map = (Map<K, V>) getter.invoke(instance);
            }
            catch (Throwable e) {
                builder.add(new ConfigAttribute(attributeName, propertyName, "-- n/a --", "-- ERROR --", description, false));
                return;
            }

            if (map == null) {
                builder.add(new ConfigAttribute(attributeName, propertyName, "-- n/a --", "null", description, false));
                return;
            }
            if (map.isEmpty()) {
                builder.add(new ConfigAttribute(attributeName, propertyName, "-- n/a --", "-- empty --", description, false));
                return;
            }
            for (Entry<K, V> entry : map.entrySet()) {
                if (valueConfigClass != null) {
                    enumerateConfig(ConfigurationMetadata.getConfigurationMetadata(valueConfigClass),
                            entry.getValue(),
                            newDefaultInstance(ConfigurationMetadata.getConfigurationMetadata(valueConfigClass)),
                            propertyName + "." + entry.getKey().toString() + ".",
                            builder,
                            attributeName + "[" + entry.getKey().toString() + "]");
                }
                else {
                    builder.add(new ConfigAttribute(attributeName + "[" + entry.getKey().toString() + "]",
                            propertyName + "." + entry.getKey().toString(),
                            "-- n/a --", entry.getValue().toString(), description, false));
                }
            }
        }

        public String getComponentName()
        {
            Key<?> key = getKey();
            String componentName = "";
            if (key.getAnnotationType() != null) {
                componentName = "@" + key.getAnnotationType().getSimpleName() + " ";
            }
            componentName += key.getTypeLiteral();
            return componentName;
        }

        public Key<T> getKey()
        {
            return key;
        }

        public Class<T> getConfigClass()
        {
            return configClass;
        }

        public String getPrefix()
        {
            return prefix;
        }

        public SortedSet<ConfigAttribute> getAttributes()
        {
            return attributes;
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(configClass, prefix);
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final ConfigRecord other = (ConfigRecord) obj;
            return Objects.equal(this.configClass, other.configClass) && Objects.equal(this.prefix, other.prefix);
        }

        @Override
        public int compareTo(ConfigRecord<?> that)
        {
            return ComparisonChain.start()
                    .compare(this.configClass.getCanonicalName(), that.configClass.getCanonicalName())
                    .compare(this.prefix, that.prefix)
                    .result();
        }
    }

    public static class ConfigAttribute implements Comparable<ConfigAttribute>
    {
        private final String attributeName;
        private final String propertyName;
        private final String defaultValue;
        private final String currentValue;
        private final String description;

        // todo this class needs to be updated to include the concept of deprecated property names

        private ConfigAttribute(String attributeName, String propertyName, String defaultValue, String currentValue, String description, boolean securitySensitive)
        {
            Preconditions.checkNotNull(attributeName, "attributeName");
            Preconditions.checkNotNull(propertyName, "propertyName");
            Preconditions.checkNotNull(defaultValue, "defaultValue");
            Preconditions.checkNotNull(currentValue, "currentValue");
            Preconditions.checkNotNull(description, "description");

            this.attributeName = attributeName;
            this.propertyName = propertyName;
            if (securitySensitive && defaultValue != null) {
                this.defaultValue = "[REDACTED]";
            }
            else {
                this.defaultValue = defaultValue;
            }
            if (securitySensitive && currentValue != null) {
                this.currentValue = "[REDACTED]";
            }
            else {
                this.currentValue = currentValue;
            }
            this.description = description;
        }

        public String getAttributeName()
        {
            return attributeName;
        }

        public String getPropertyName()
        {
            return propertyName;
        }

        public String getDefaultValue()
        {
            return defaultValue;
        }

        public String getCurrentValue()
        {
            return currentValue;
        }

        public String getDescription()
        {
            return description;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ConfigAttribute that = (ConfigAttribute) o;

            return attributeName.equals(that.attributeName);
        }

        @Override
        public int hashCode()
        {
            return attributeName.hashCode();
        }

        @Override
        public int compareTo(ConfigAttribute that)
        {
            return this.attributeName.compareTo(that.attributeName);
        }

        @Override
        public String toString()
        {
            return Objects.toStringHelper(this)
                    .add("attributeName", attributeName)
                    .add("propertyName", propertyName)
                    .add("defaultValue", defaultValue)
                    .add("currentValue", currentValue)
                    .add("description", description)
                    .toString();
        }
    }
}
