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
            Reader reader = new FileReader(new File(configFile));
            Properties properties = new Properties();
            try {
                properties.load(reader);
            }
            finally {
                reader.close();
            }

            result.putAll(toMap(properties));
        }
        
        result.putAll(toMap(System.getProperties()));
        
        return result;
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
