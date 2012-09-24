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

import com.proofpoint.configuration.Problems.Monitor;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class ConfigurationFactoryBuilder
{
    private final Map<String, String> properties = new HashMap<>();
    private final Set<String> expectToUse = new HashSet<>();
    private Monitor monitor = Problems.NULL_MONITOR;

    /**
     * Loads properties from the given file
     *
     * @param path file path
     * @return self
     * @throws java.io.IOException errors
     */
    public ConfigurationFactoryBuilder withFile(@Nullable String path)
            throws IOException
    {
        if (path == null) {
            return this;
        }

        Properties properties = new Properties();
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

    public ConfigurationFactoryBuilder withMonitor(Monitor monitor)
    {
        this.monitor = monitor;
        return this;
    }

    public ConfigurationFactory build()
    {
        return new ConfigurationFactory(properties, expectToUse, monitor);
    }

    private void mergeProperties(Properties properties)
    {
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            this.properties.put(entry.getKey().toString(), entry.getValue().toString());
        }
    }
}
