package io.airlift.configuration;

import static java.util.Objects.requireNonNull;

class ConfigurationBindingListenerHolder
{
    private final ConfigurationBindingListener configurationBindingListener;

    ConfigurationBindingListenerHolder(ConfigurationBindingListener configurationBindingListener)
    {
        this.configurationBindingListener = requireNonNull(configurationBindingListener, "configurationBindingListener is null");
    }

    public ConfigurationBindingListener getConfigurationBindingListener()
    {
        return configurationBindingListener;
    }
}
