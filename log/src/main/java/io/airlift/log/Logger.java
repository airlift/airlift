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
import java.util.function.Supplier;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

public class Logger
{
    private final java.util.logging.Logger logger;

    Logger(java.util.logging.Logger logger)
    {
        this.logger = requireNonNull(logger, "logger is null");
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
        return new Logger(java.util.logging.Logger.getLogger(name));
    }

    /**
     * Logs a message, provided by the given supplier, at DEBUG level.
     *
     * @param exception an exception associated with the debug message being logged
     * @param messageSupplier a {@link Supplier} of the pre-formatted message
     */
    public void debug(Throwable exception, Supplier<String> messageSupplier)
    {
        logger.log(FINE, exception, messageSupplier);
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
     * Logs a message, provided by the given supplier, at DEBUG level.
     *
     * @param messageSupplier a {@link Supplier} of the pre-formatted message
     */
    public void debug(Supplier<String> messageSupplier)
    {
        debug(null, messageSupplier);
    }

    /**
     * Logs a message at DEBUG level.
     *
     * @param message a literal message to log
     */
    public void debug(String message)
    {
        logger.log(FINE, message);
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
        debug(null, format, args);
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
        debug(exception, () -> formatMessage(format, "DEBUG", args));
    }

    /**
     * Logs a message, provided by the given supplier, at INFO level.
     *
     * @param messageSupplier a {@link Supplier} of the pre-formatted message
     */
    public void info(Supplier<String> messageSupplier)
    {
        logger.log(INFO, messageSupplier);
    }

    /**
     * Logs a message at INFO level.
     *
     * @param message a literal message to log
     */
    public void info(String message)
    {
        logger.log(INFO, message);
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
        info(() -> formatMessage(format, "INFO", args));
    }

    /**
     * Logs a message, provided by the given supplier, at WARN level.
     *
     * @param exception an exception associated with the warning being logged
     * @param messageSupplier a {@link Supplier} of the pre-formatted message
     */
    public void warn(Throwable exception, Supplier<String> messageSupplier)
    {
        logger.log(WARNING, exception, messageSupplier);
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
     * Logs a message, provided by the given supplier, at WARN level.
     *
     * @param messageSupplier a {@link Supplier} of the pre-formatted message
     */
    public void warn(Supplier<String> messageSupplier)
    {
        warn(null, messageSupplier);
    }

    /**
     * Logs a message at WARN level.
     *
     * @param message a literal message to log
     */
    public void warn(String message)
    {
        logger.log(WARNING, message);
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
        warn(exception, () -> formatMessage(format, "WARN", args));
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
     * Logs a message, provided by the given supplier, at ERROR level.
     *
     * @param exception an exception associated with the error being logged
     * @param messageSupplier a {@link Supplier} of the pre-formatted message
     */
    public void error(Throwable exception, Supplier<String> messageSupplier)
    {
        logger.log(SEVERE, exception, messageSupplier);
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
     * Logs a message, provided by the given supplier, at ERROR level.
     *
     * @param messageSupplier a {@link Supplier} of the pre-formatted message
     */
    public void error(Supplier<String> messageSupplier)
    {
        error(null, messageSupplier);
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
        error(exception, () -> formatMessage(format, "ERROR", args));
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
        error(exception, exception::getMessage);
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

    private String formatMessage(String format, String level, Object[] args)
    {
        String message;
        try {
            message = format(format, args);
        }
        catch (IllegalFormatException e) {
            logger.log(SEVERE, format("Invalid format string while trying to log: %s '%s' %s", level, format, asList(args)), e);
            message = format("'%s' %s", format, asList(args));
        }
        return message;
    }
}
