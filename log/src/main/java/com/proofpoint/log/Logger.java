package com.proofpoint.log;

import ch.qos.logback.classic.Level;
import com.google.inject.Inject;
import org.slf4j.LoggerFactory;

import java.util.IllegalFormatException;

import static java.lang.String.format;
import static java.util.Arrays.asList;

public class Logger
{
    private final org.slf4j.Logger logger;

    @Inject
    Logger(org.slf4j.Logger logger)
    {
        this.logger = logger;
    }

    /**
     * Gets a logger named after a class' fully qualified name.
     *
     * @param clazz the class
     * @return the named logger
     */
    public static Logger get(Class<?> clazz)
    {
        org.slf4j.Logger logger = LoggerFactory.getLogger(clazz);
        return new Logger(logger);
    }

    /**
     * Gets a named logger
     *
     * @param name the name of the logger
     * @return the named logger
     */
    public static Logger get(String name)
    {
        org.slf4j.Logger logger = LoggerFactory.getLogger(name);
        return new Logger(logger);
    }

    /**
     * Logs a message at DEBUG level.
     * <br/>
     * Usage example:
     * <pre>
     *    logger.debug("value is %s (%d ms)", value, time);
     * </pre>
     * If the format string is invalid or the arguments are insufficient, an error will be logged and execution
     * will continue.
     *
     * @param format a format string compatible with String.format()
     * @param args arguments for the format string
     */
    public void debug(String format, Object... args)
    {
        if (logger.isDebugEnabled()) {
            String message;
            try {
                message = format(format, args);
            }
            catch (IllegalFormatException e) {
                logger.error(illegalFormatMessageFor(Level.DEBUG, format, args), e);
                message = rawMessageFor(format, args);
            }
            logger.debug(message);
        }
    }

    /**
     * Logs a message at INFO level.
     * <br/>
     * Usage example:
     * <pre>
     *    logger.info("value is %s (%d ms)", value, time);
     * </pre>
     * If the format string is invalid or the arguments are insufficient, an error will be logged and execution
     * will continue.
     *
     * @param format a format string compatible with String.format()
     * @param args arguments for the format string
     */
    public void info(String format, Object... args)
    {
        if (logger.isInfoEnabled()) {
            String message;
            try {
                message = format(format, args);
            }
            catch (IllegalFormatException e) {
                logger.error(illegalFormatMessageFor(Level.INFO, format, args), e);
                message = rawMessageFor(format, args);
            }
            logger.info(message);
        }
    }

    /**
     * Logs a message at WARN level.
     * <br/>
     * Usage example:
     * <pre>
     *    logger.warn(e, "something bad happened when connecting to %s:%d", host, port);
     * </pre>
     * If the format string is invalid or the arguments are insufficient, an error will be logged and execution
     * will continue.
     *
     * @param exception an exception associated with the warning being logged
     * @param format a format string compatible with String.format()
     * @param args arguments for the format string
     */
    public void warn(Throwable exception, String format, Object... args)
    {
        if (logger.isWarnEnabled()) {
            String message;
            try {
                message = format(format, args);
            }
            catch (IllegalFormatException e) {
                logger.error(illegalFormatMessageFor(Level.WARN, format, args), e);
                message = rawMessageFor(format, args);
            }
            logger.warn(message, exception);
        }
    }

    /**
     * Logs a message at WARN level.
     * <br/>
     * Usage example:
     * <pre>
     *    logger.warn("something bad happened when connecting to %s:%d", host, port);
     * </pre>
     * If the format string is invalid or the arguments are insufficient, an error will be logged and execution
     * will continue.
     *
     * @param format a format string compatible with String.format()
     * @param args arguments for the format string
     */
    public void warn(String format, Object... args)
    {
        warn(null, format, args);
    }

    /**
     * Logs a message at ERROR level.
     * <br/>
     * Usage example:
     * <pre>
     *    logger.error(e, "something really bad happened when connecting to %s:%d", host, port);
     * </pre>
     * If the format string is invalid or the arguments are insufficient, an error will be logged and execution
     * will continue.
     *
     * @param exception an exception associated with the error being logged
     * @param format a format string compatible with String.format()
     * @param args arguments for the format string
     */
    public void error(Throwable exception, String format, Object... args)
    {
        if (logger.isErrorEnabled()) {
            String message;
            try {
                message = format(format, args);
            }
            catch (IllegalFormatException e) {
                logger.error(illegalFormatMessageFor(Level.ERROR, format, args), e);
                message = rawMessageFor(format, args);
            }
            logger.error(message, exception);
        }
    }

    /**
     * Logs a message at ERROR level. The value of {@code exception.getMessage()} will be used as the log message.
     * <br/>
     * Usage example:
     * <pre>
     *    logger.error(e);
     * </pre>
     *
     * @param exception an exception associated with the error being logged
     */
    public void error(Throwable exception)
    {
        if (logger.isErrorEnabled()) {
            logger.error(exception.getMessage(), exception);
        }
    }

    /**
     * Logs a message at ERROR level.
     * <br/>
     * Usage example:
     * <pre>
     *    logger.error("something really bad happened when connecting to %s:%d", host, port);
     * </pre>
     * If the format string is invalid or the arguments are insufficient, an error will be logged and execution
     * will continue.
     *
     * @param format a format string compatible with String.format()
     * @param args arguments for the format string
     */
    public void error(String format, Object... args)
    {
        error(null, format, args);
    }

    public boolean isDebugEnabled()
    {
        return logger.isDebugEnabled();
    }

    public boolean isInfoEnabled()
    {
        return logger.isInfoEnabled();
    }

    private String illegalFormatMessageFor(Level level, String message, Object... args)
    {
        return format("Invalid format string while trying to log: %s '%s' %s", level, message, asList(args));
    }

    private String rawMessageFor(String format, Object... args)
    {
        return format("'%s' %s", format, asList(args));
    }
}
