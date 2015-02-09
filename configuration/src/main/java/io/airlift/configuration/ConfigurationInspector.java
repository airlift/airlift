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
package io.airlift.configuration;

import com.google.common.base.Preconditions;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import com.google.inject.Key;
import io.airlift.configuration.ConfigurationMetadata.AttributeMetadata;

import java.lang.reflect.Method;
import java.util.SortedSet;

import static com.google.common.base.MoreObjects.toStringHelper;

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

    public static class ConfigRecord<T>
            implements Comparable<ConfigRecord<?>>
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

            T defaults = configurationProvider.getDefaultConfig();

            T instance = null;
            try {
                instance = configurationProvider.get();
            }
            catch (Throwable ignored) {
                // provider could blow up for any reason, which is fine for this code
                // this is catch throwable because we may get an AssertionError
            }

            String prefix = configurationProvider.getPrefix();
            prefix = prefix == null ? "" : (prefix + ".");

            ImmutableSortedSet.Builder<ConfigAttribute> builder = ImmutableSortedSet.naturalOrder();
            for (AttributeMetadata attribute : metadata.getAttributes().values()) {
                String propertyName = prefix + attribute.getInjectionPoint().getProperty();
                Method getter = attribute.getGetter();

                String defaultValue = getValue(getter, defaults, "-- none --");
                String currentValue = getValue(getter, instance, "-- n/a --");
                String description = attribute.getDescription();
                if (description == null) {
                    description = "";
                }

                builder.add(new ConfigAttribute(attribute.getName(), propertyName, defaultValue, currentValue, description));
            }
            attributes = builder.build();
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
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ConfigRecord<?> that = (ConfigRecord<?>) o;

            return key.equals(that.key);
        }

        @Override
        public int hashCode()
        {
            return key.hashCode();
        }

        @Override
        public int compareTo(ConfigRecord<?> that)
        {
            return ComparisonChain.start()
                    .compare(String.valueOf(this.key.getTypeLiteral().getType()), String.valueOf(that.key.getTypeLiteral().getType()))
                    .compare(String.valueOf(this.key.getAnnotationType()), String.valueOf(that.key.getAnnotationType()))
                    .compare(this.key, that.key, Ordering.arbitrary())
                    .result();
        }
    }

    public static class ConfigAttribute
            implements Comparable<ConfigAttribute>
    {
        private final String attributeName;
        private final String propertyName;
        private final String defaultValue;
        private final String currentValue;
        private final String description;

        // todo this class needs to be updated to include the concept of deprecated property names

        private ConfigAttribute(String attributeName, String propertyName, String defaultValue, String currentValue, String description)
        {
            Preconditions.checkNotNull(attributeName, "attributeName");
            Preconditions.checkNotNull(propertyName, "propertyName");
            Preconditions.checkNotNull(defaultValue, "defaultValue");
            Preconditions.checkNotNull(currentValue, "currentValue");
            Preconditions.checkNotNull(description, "description");

            this.attributeName = attributeName;
            this.propertyName = propertyName;
            this.defaultValue = defaultValue;
            this.currentValue = currentValue;
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
            return toStringHelper(this)
                    .add("attributeName", attributeName)
                    .add("propertyName", propertyName)
                    .add("defaultValue", defaultValue)
                    .add("currentValue", currentValue)
                    .add("description", description)
                    .toString();
        }
    }
}
