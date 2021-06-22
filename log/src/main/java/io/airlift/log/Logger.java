/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.log;

import com.google.errorprone.annotations.FormatMethod;

import java.util.IllegalFormatException;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

public class Logger
{
    private final java.util.logging.Logger logger;

    Logger(java.util.logging.Logger logger)
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
        return get(clazz.getName());
    }

    /**
     * Gets a named logger
     *
     * @param name the name of the logger
     * @return the named logger
     */
    public static Logger get(String name)
    {
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(name);
        return new Logger(logger);
    }

    /**
     * Logs a message at DEBUG level.
     *
     * @param exception an exception associated with the debug message being logged
     * @param message a literal message to log
     */
    public void debug(Throwable exception, String message)
    {
        logger.log(FINE, message, exception);
    }

    /**
     * Logs a message at DEBUG level.
     *
     * @param message a literal message to log
     */
    public void debug(String message)
    {
        logger.fine(message);
    }

    /**
     * Logs a message at DEBUG level.
     * <p>
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
    @FormatMethod
    public void debug(String format, Object... args)
    {
        if (logger.isLoggable(FINE)) {
            String message;
            try {
                message = format(format, args);
            }
            catch (IllegalFormatException e) {
                logger.log(SEVERE, illegalFormatMessageFor("DEBUG", format, args), e);
                message = rawMessageFor(format, args);
            }
            logger.fine(message);
        }
    }

    /**
     * Logs a message at DEBUG level.
     * <p>
     * Usage example:
     * <pre>
     *    logger.debug(e, "value is %s (%d ms)", value, time);
     * </pre>
     * If the format string is invalid or the arguments are insufficient, an error will be logged and execution
     * will continue.
     *
     * @param exception an exception associated with the debug message being logged
     * @param format a format string compatible with String.format()
     * @param args arguments for the format string
     */
    @FormatMethod
    public void debug(Throwable exception, String format, Object... args)
    {
        if (logger.isLoggable(FINE)) {
            String message;
            try {
                message = format(format, args);
            }
            catch (IllegalFormatException e) {
                logger.log(SEVERE, illegalFormatMessageFor("DEBUG", format, args), e);
                message = rawMessageFor(format, args);
            }
            logger.log(FINE, message, exception);
        }
    }

    /**
     * Logs a message at INFO level.
     *
     * @param message a literal message to log
     */
    public void info(String message)
    {
        logger.info(message);
    }

    /**
     * Logs a message at INFO level.
     * <p>
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
    @FormatMethod
    public void info(String format, Object... args)
    {
        if (logger.isLoggable(INFO)) {
            String message;
            try {
                message = format(format, args);
            }
            catch (IllegalFormatException e) {
                logger.log(SEVERE, illegalFormatMessageFor("INFO", format, args), e);
                message = rawMessageFor(format, args);
            }
            logger.info(message);
        }
    }

    /**
     * Logs a message at WARN level.
     *
     * @param exception an exception associated with the warning being logged
     * @param message a literal message to log
     */
    public void warn(Throwable exception, String message)
    {
        logger.log(WARNING, message, exception);
    }

    /**
     * Logs a message at WARN level.
     *
     * @param message a literal message to log
     */
    public void warn(String message)
    {
        logger.warning(message);
    }

    /**
     * Logs a message at WARN level.
     * <p>
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
    @FormatMethod
    public void warn(Throwable exception, String format, Object... args)
    {
        if (logger.isLoggable(WARNING)) {
            String message;
            try {
                message = format(format, args);
            }
            catch (IllegalFormatException e) {
                logger.log(SEVERE, illegalFormatMessageFor("WARN", format, args), e);
                message = rawMessageFor(format, args);
            }
            logger.log(WARNING, message, exception);
        }
    }

    /**
     * Logs a message at WARN level.
     * <p>
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
    @FormatMethod
    public void warn(String format, Object... args)
    {
        warn(null, format, args);
    }

    /**
     * Logs a message at ERROR level.
     *
     * @param exception an exception associated with the error being logged
     * @param message a literal message to log
     */
    public void error(Throwable exception, String message)
    {
        logger.log(SEVERE, message, exception);
    }

    /**
     * Logs a message at ERROR level.
     *
     * @param message a literal message to log
     */
    public void error(String message)
    {
        logger.severe(message);
    }

    /**
     * Logs a message at ERROR level.
     * <p>
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
    @FormatMethod
    public void error(Throwable exception, String format, Object... args)
    {
        if (logger.isLoggable(SEVERE)) {
            String message;
            try {
                message = format(format, args);
            }
            catch (IllegalFormatException e) {
                logger.log(SEVERE, illegalFormatMessageFor("ERROR", format, args), e);
                message = rawMessageFor(format, args);
            }
            logger.log(SEVERE, message, exception);
        }
    }

    /**
     * Logs a message at ERROR level. The value of {@code exception.getMessage()} will be used as the log message.
     * <p>
     * Usage example:
     * <pre>
     *    logger.error(e);
     * </pre>
     *
     * @param exception an exception associated with the error being logged
     */
    public void error(Throwable exception)
    {
        if (logger.isLoggable(SEVERE)) {
            logger.log(SEVERE, exception.getMessage(), exception);
        }
    }

    /**
     * Logs a message at ERROR level.
     * <p>
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
    @FormatMethod
    public void error(String format, Object... args)
    {
        error(null, format, args);
    }

    public boolean isDebugEnabled()
    {
        return logger.isLoggable(FINE);
    }

    public boolean isInfoEnabled()
    {
        return logger.isLoggable(INFO);
    }

    private String illegalFormatMessageFor(String level, String message, Object... args)
    {
        return format("Invalid format string while trying to log: %s '%s' %s", level, message, asList(args));
    }

    private String rawMessageFor(String format, Object... args)
    {
        return format("'%s' %s", format, asList(args));
    }
}
