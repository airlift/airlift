package io.airlift.bootstrap;

public interface LoggingBootstrap
{
    void initializeLogging(LoggingBootstrapContext context)
            throws Exception;
}
