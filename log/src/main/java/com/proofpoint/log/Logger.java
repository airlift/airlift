package com.proofpoint.log;

import com.google.inject.Inject;
import org.slf4j.LoggerFactory;

public class Logger
{
    private final org.slf4j.Logger logger;

    @Inject
    Logger(org.slf4j.Logger logger)
    {
        this.logger = logger;
    }

    public static Logger get(Class<?> clazz)
    {
        org.slf4j.Logger logger = LoggerFactory.getLogger(clazz);
        return new Logger(logger);
    }

    public static Logger get(String name)
    {
        org.slf4j.Logger logger = LoggerFactory.getLogger(name);
        return new Logger(logger);
    }

    public void debug(String message, Object... args)
    {
        if (logger.isDebugEnabled()) {
            logger.debug(String.format(message, args));
        }
    }

    public void info(String message, Object... args)
    {
        if (logger.isInfoEnabled()) {
            logger.info(String.format(message, args));
        }
    }

    public void warn(Throwable t, String message, Object... args)
    {
        if (logger.isWarnEnabled()) {
            logger.warn(String.format(message, args), t);
        }
    }

    public void warn(String message, Object... args)
    {
        if (logger.isWarnEnabled()) {
            logger.warn(String.format(message, args));
        }
    }

    public void error(Throwable t, String message, Object... args)
    {
        if (logger.isErrorEnabled()) {
            logger.error(String.format(message, args), t);
        }
    }

    public void error(Throwable t)
    {
        if (logger.isErrorEnabled()) {
            logger.error(t.getMessage(), t);
        }
    }

    public void error(String message, Object... args)
    {
        if (logger.isErrorEnabled()) {
            logger.error(String.format(message, args));
        }
    }

    public boolean isDebugEnabled()
    {
        return logger.isDebugEnabled();
    }

    public boolean isInfoEnabled()
    {
        return logger.isInfoEnabled();
    }
}
