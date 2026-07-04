package io.airlift.bootstrap;

import io.airlift.configuration.ConfigurationFactory;

import java.util.Map;
import java.util.logging.ErrorManager;
import java.util.logging.Handler;

public interface LoggingBootstrapContext
{
    ConfigurationFactory getConfigurationFactory();

    Map<String, String> getLogAnnotations();

    ErrorManager createErrorManager();

    void addRootHandler(Handler handler);
}
