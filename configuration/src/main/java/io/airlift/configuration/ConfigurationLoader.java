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

import com.google.common.collect.ImmutableSortedMap;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import static com.google.common.collect.Maps.fromProperties;

public final class ConfigurationLoader
{
    private ConfigurationLoader() {}

    public static Map<String, String> loadProperties()
            throws IOException
    {
        Map<String, String> result = new TreeMap<>();
        String configFile = System.getProperty("config");
        if (configFile != null) {
            result.putAll(loadPropertiesFrom(configFile));
        }

        result.putAll(getSystemProperties());

        return ImmutableSortedMap.copyOf(result);
    }

    /**
     * Loads properties from the given file
     *
     * @param path file path
     * @return properties
     * @throws IOException errors
     */
    public static Map<String, String> loadPropertiesFrom(String path)
            throws IOException
    {
        Properties properties = new Properties();
        try (InputStream inputStream = new FileInputStream(path)) {
            properties.load(inputStream);
        }

        return fromProperties(properties);
    }

    public static Map<String, String> getSystemProperties()
    {
        return fromProperties(System.getProperties());
    }
}
