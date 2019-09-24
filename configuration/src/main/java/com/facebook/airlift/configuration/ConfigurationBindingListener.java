package com.facebook.airlift.configuration;

public interface ConfigurationBindingListener
{
    void configurationBound(ConfigurationBinding<?> configurationBinding, ConfigBinder configBinder);
}
