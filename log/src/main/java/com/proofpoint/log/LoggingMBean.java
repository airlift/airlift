package com.proofpoint.log;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;
import org.weakref.jmx.Managed;

import java.util.Map;

import static ch.qos.logback.classic.Level.toLevel;
import static com.google.common.collect.Maps.newTreeMap;
import static org.slf4j.Logger.ROOT_LOGGER_NAME;

public class LoggingMBean
{
    @Managed
    public String getLevel(String loggerName)
    {
        return getLogger(loggerName).getEffectiveLevel().toString();
    }

    @Managed
    public void setLevel(String loggerName, String newLevel)
    {
        getLogger(loggerName).setLevel(toLevel(newLevel));
    }

    @Managed
    public String getRootLevel()
    {
        return getLogger(ROOT_LOGGER_NAME).getEffectiveLevel().toString();
    }

    @Managed
    public void setRootLevel(String newLevel)
    {
        getLogger(ROOT_LOGGER_NAME).setLevel(toLevel(newLevel));
    }

    @Managed
    public Map<String, String> getAllLevels()
    {
        Map<String, String> levels = newTreeMap();
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (Logger logger : context.getLoggerList()) {
            if (logger.getLevel() != null) {
                levels.put(logger.getName(), logger.getLevel().toString());
            }
        }
        return levels;
    }

    private static Logger getLogger(String name)
    {
        return (Logger) LoggerFactory.getLogger(name);
    }
}
