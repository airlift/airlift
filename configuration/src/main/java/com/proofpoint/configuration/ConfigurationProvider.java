package com.proofpoint.configuration;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provider;

public class ConfigurationProvider<T> implements Provider<T>
{
    private final Key<T> key;
    private final Class<T> configClass;
    private final String prefix;
    private ConfigurationFactory configurationFactory;
    private WarningsMonitor warningsMonitor;

    public ConfigurationProvider(Key<T> key, Class<T> configClass, String prefix)
    {
        Preconditions.checkNotNull(key, "key");
        Preconditions.checkNotNull(configClass, "configClass");

        this.key = key;
        this.configClass = configClass;
        this.prefix = prefix;
    }

    @Inject
    public void setConfigurationFactory(ConfigurationFactory configurationFactory)
    {
        this.configurationFactory = configurationFactory;
    }

    @Inject(optional = true)
    public void setWarningsMonitor(WarningsMonitor warningsMonitor)
    {
        this.warningsMonitor = warningsMonitor;
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

    public ConfigurationMetadata<T> getConfigurationMetadata() {
        return ConfigurationMetadata.getConfigurationMetadata(configClass);
    }

    @Override
    public T get()
    {
        Preconditions.checkNotNull(configurationFactory, "configurationFactory");

        return configurationFactory.build(this, warningsMonitor);
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

        ConfigurationProvider<?> that = (ConfigurationProvider<?>) o;

        if (!key.equals(that.key)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return key.hashCode();
    }
}
