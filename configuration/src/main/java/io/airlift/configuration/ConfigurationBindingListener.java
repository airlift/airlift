package io.airlift.configuration;

public interface ConfigurationBindingListener
{
    void configurationBound(ConfigurationBinding<?> configurationBinding, ConfigBinder configBinder);
}
