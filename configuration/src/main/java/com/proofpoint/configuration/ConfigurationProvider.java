package com.proofpoint.configuration;

import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Map;

public class ConfigurationProvider<T> implements Provider<T>
{
    private final Class<T> configClass;
    private final String prefix;
    private T instance;
    private ConfigurationFactory configurationFactory;

    public ConfigurationProvider(Class<T> configClass) {

        this(configClass, null, null);
    }

    public ConfigurationProvider(Class<T> configClass, String prefix)
    {
        this(configClass, prefix, null);
    }

    public ConfigurationProvider(Class<T> configClass, Map<String, String> properties)
    {
        this(configClass, null, properties);
    }

    public ConfigurationProvider(Class<T> configClass, String prefix, Map<String, String> properties)
    {
        if (configClass == null) {
            throw new NullPointerException("configClass is null");
        }
        this.configClass = configClass;
        this.prefix = prefix;
        if (properties != null) {
            configurationFactory = new ConfigurationFactory(properties);
        }
    }

    @Inject
    public void setConfigurationFactory(ConfigurationFactory configurationFactory)
    {
        this.configurationFactory = configurationFactory;
    }

    public Class<T> getConfigClass()
    {
        return configClass;
    }

    public String getPrefix()
    {
        return prefix;
    }

    public ConfigurationMetadata<T> getConfigurationMetadata() {
        return ConfigurationMetadata.getConfigurationMetadata(configClass);
    }

    @Override
    public T get()
    {
        if (configurationFactory == null) {
            throw new NullPointerException("configurationFactory is null");
        }
        
        if (instance == null) {
            instance = configurationFactory.build(configClass, prefix);
        }
        return instance;
    }

}
