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

import com.google.common.collect.Maps;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.Properties;

public class ConfigurationLoader
{
    public Map<String, String> loadProperties()
            throws IOException
    {
        Map<String, String> result = Maps.newHashMap();
        String configFile = System.getProperty("config");
        if (configFile != null) {
            internalLoadProperties(result, configFile);
        }

        result.putAll(toMap(System.getProperties()));

        return result;
    }

    /**
     * Loads properties from the given file
     *
     * @param path file path
     * @return properties
     * @throws IOException errors
     */
    public Map<String, String> loadPropertiesFrom(String path)
            throws IOException
    {
        Map<String, String> result = Maps.newHashMap();
        internalLoadProperties(result, path);
        return result;
    }

    private void internalLoadProperties(Map<String, String> result, String configFile)
            throws IOException
    {
        Reader reader = new FileReader(new File(configFile));
        Properties properties = new Properties();
        try {
            properties.load(reader);
        } finally {
            reader.close();
        }

        result.putAll(toMap(properties));
    }

    private static Map<String, String> toMap(Properties properties)
    {
        Map<String, String> result = Maps.newHashMap();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue().toString());
        }

        return result;
    }

}
