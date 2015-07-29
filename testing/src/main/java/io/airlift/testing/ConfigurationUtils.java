package io.airlift.testing;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static com.google.common.collect.Maps.fromProperties;
import static com.google.common.io.Resources.getResource;

public final class ConfigurationUtils
{
    private ConfigurationUtils()
    {
    }

    /**
     * Load a properties file from the class path and return as a Map suitable for a configuration factory.
     */
    public static Map<String, String> loadPropertiesFromClasspath(String path) throws IOException
    {
        Properties properties = new Properties();
        properties.load(getResource(ConfigurationUtils.class, path).openStream());
        return fromProperties(properties);
    }
}
