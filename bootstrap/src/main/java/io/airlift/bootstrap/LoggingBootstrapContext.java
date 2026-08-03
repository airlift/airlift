package io.airlift.bootstrap;

import io.airlift.configuration.ConfigurationFactory;
import io.airlift.log.LoggingConfiguration;

import java.util.logging.ErrorManager;
import java.util.logging.Handler;

public interface LoggingBootstrapContext
{
    ConfigurationFactory getConfigurationFactory();

    LoggingConfiguration getLoggingConfiguration();

    ErrorManager createErrorManager();

    void addRootHandler(Handler handler);

    void removeRootHandler(Handler handler);
}
