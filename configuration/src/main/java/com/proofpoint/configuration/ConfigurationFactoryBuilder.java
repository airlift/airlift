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

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.Problems.Monitor;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static java.lang.String.format;

public final class ConfigurationFactoryBuilder
{
    private final Map<String, String> properties = new HashMap<>();
    private final Set<String> expectToUse = new HashSet<>();
    private Monitor monitor = Problems.NULL_MONITOR;
    private final List<String> errors = new ArrayList<>();
    private Map<String,String> applicationDefaults = ImmutableMap.of();

    /**
     * Loads properties from the given file
     *
     * @param path file path
     * @return self
     * @throws java.io.IOException errors
     */
    public ConfigurationFactoryBuilder withFile(@Nullable final String path)
            throws IOException
    {
        if (path == null) {
            return this;
        }

        final Properties properties = new Properties() {
            @SuppressWarnings("UseOfPropertiesAsHashtable")
            @Override
            public synchronized Object put(Object key, Object value) {
                final Object old = super.put(key, value);
                if (old != null) {
                    errors.add(format("Duplicate configuration property '%s' in file %s", key, path));
                }
                return old;
            }
        };
        try (Reader reader = new FileReader(new File(path))) {
            properties.load(reader);
        }

        mergeProperties(properties);
        expectToUse.addAll(properties.stringPropertyNames());
        return this;
    }

    public ConfigurationFactoryBuilder withSystemProperties()
    {
        mergeProperties(System.getProperties());
        return this;
    }

    public ConfigurationFactoryBuilder withRequiredProperties(Map<String, String> requiredConfigurationProperties)
    {
        properties.putAll(requiredConfigurationProperties);
        expectToUse.addAll(requiredConfigurationProperties.keySet());
        return this;
    }

    public ConfigurationFactoryBuilder withApplicationDefaults(Map<String, String> applicationDefaults)
    {
        this.applicationDefaults = ImmutableMap.copyOf(applicationDefaults);
        expectToUse.addAll(applicationDefaults.keySet());
        return this;
    }

    public ConfigurationFactoryBuilder withMonitor(Monitor monitor)
    {
        this.monitor = monitor;
        return this;
    }

    public ConfigurationFactory build()
    {
        return new ConfigurationFactory(properties, applicationDefaults, expectToUse, errors, monitor);
    }

    private void mergeProperties(Properties properties)
    {
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            this.properties.put(entry.getKey().toString(), entry.getValue().toString());
        }
    }
}
