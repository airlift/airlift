package io.airlift.configuration;

import static java.util.Objects.requireNonNull;

record ConfigurationBindingListenerHolder(ConfigurationBindingListener configurationBindingListener)
{
    ConfigurationBindingListenerHolder
    {
        requireNonNull(configurationBindingListener, "configurationBindingListener is null");
    }
}
