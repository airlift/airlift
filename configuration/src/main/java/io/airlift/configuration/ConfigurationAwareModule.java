package io.airlift.configuration;

import com.google.common.annotations.Beta;
import com.google.inject.Module;

@Beta
public interface ConfigurationAwareModule extends Module
{
    void setConfigurationFactory(ConfigurationFactory configurationFactory);
}
