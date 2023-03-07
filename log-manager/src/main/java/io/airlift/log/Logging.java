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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.net.HostAndPort;
import io.airlift.log.RollingFileMessageOutput.CompressionType;
import io.airlift.units.DataSize;

import javax.annotation.concurrent.GuardedBy;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogManager;

import static io.airlift.configuration.ConfigurationLoader.loadPropertiesFrom;
import static io.airlift.configuration.ConfigurationUtils.replaceEnvironmentVariables;
import static io.airlift.log.RollingFileMessageOutput.createRollingFileHandler;
import static io.airlift.log.SocketMessageOutput.createSocketHandler;

/**
 * Initializes the logging subsystem.
 * <p>
 * java.util.Logging, System.out and System.err are tunneled through the logging system.
 * <p>
 * System.out and System.err are assigned to loggers named "stdout" and "stderr", respectively.
 */
public class Logging
{
    private static final Logger log = Logger.get(Logging.class);
    private static final String ROOT_LOGGER_NAME = "";
    private static final java.util.logging.Logger ROOT = java.util.logging.Logger.getLogger("");

    private static Logging instance;
    private static final PrintStream stdErr = System.err;

    // hard reference to loggers for which we set the level
    @GuardedBy("this")
    private final Map<String, java.util.logging.Logger> loggers = new HashMap<>();

    @GuardedBy("this")
    private OutputStreamHandler consoleHandler;

    @GuardedBy("this")
    private boolean configured;

    /**
     * Sets up default logging:
     * <p>
     * - INFO level
     * - Log entries are written to stderr
     *
     * @return the logging system singleton
     */
    public static synchronized Logging initialize()
    {
        if (instance == null) {
            instance = new Logging();
        }

        return instance;
    }

    private Logging()
    {
        ROOT.setLevel(Level.INFO.toJulLevel());
        for (Handler handler : ROOT.getHandlers()) {
            ROOT.removeHandler(handler);
        }

        enableConsole();
        log.info("Logging to stderr");

        redirectStdStreams();
    }

    private static void redirectStdStreams()
    {
        System.setOut(new PrintStream(new LoggingOutputStream(Logger.get("stdout")), true));
        System.setErr(new PrintStream(new LoggingOutputStream(Logger.get("stderr")), true));
    }

    private synchronized void enableConsole()
    {
        consoleHandler = new OutputStreamHandler(System.err);
        ROOT.addHandler(consoleHandler);
    }

    public synchronized void disableConsole()
    {
        log.info("Disabling stderr output");
        ROOT.removeHandler(consoleHandler);
        consoleHandler = null;
    }

    public void logToFile(String logPath, DataSize maxFileSize, DataSize maxTotalSize, CompressionType compressionType, Formatter formatter)
    {
        log.info("Logging to %s", logPath);
        ROOT.addHandler(createRollingFileHandler(logPath, maxFileSize, maxTotalSize, compressionType, formatter, new BufferedHandlerErrorManager(stdErr)));
    }

    private void logToSocket(String logPath, Formatter formatter)
    {
        if (!logPath.startsWith("tcp://") || logPath.lastIndexOf("/") > 6) {
            throw new IllegalArgumentException("LogPath for sockets must begin with tcp:// and not contain any path component.");
        }
        HostAndPort hostAndPort = HostAndPort.fromString(logPath.replace("tcp://", ""));
        Handler handler = createSocketHandler(hostAndPort, formatter, new BufferedHandlerErrorManager(stdErr));
        ROOT.addHandler(handler);
    }

    public Level getRootLevel()
    {
        return getLevel(ROOT_LOGGER_NAME);
    }

    public void setRootLevel(Level newLevel)
    {
        setLevel(ROOT_LOGGER_NAME, newLevel);
    }

    public void setLevels(String file)
            throws IOException
    {
        loadPropertiesFrom(file).forEach((loggerName, value) ->
                setLevel(loggerName, Level.valueOf(value.toUpperCase(Locale.US))));
    }

    public Level getLevel(String loggerName)
    {
        return getEffectiveLevel(java.util.logging.Logger.getLogger(loggerName));
    }

    private static Level getEffectiveLevel(java.util.logging.Logger logger)
    {
        java.util.logging.Level level = logger.getLevel();
        if (level == null) {
            java.util.logging.Logger parent = logger.getParent();
            if (parent != null) {
                return getEffectiveLevel(parent);
            }
        }
        if (level == null) {
            return Level.OFF;
        }
        return Level.fromJulLevel(level);
    }

    public synchronized void clearLevel(String loggerName)
    {
        java.util.logging.Logger logger = loggers.remove(loggerName);
        if (logger != null) {
            logger.setLevel(null);
        }
    }

    public synchronized void setLevel(String loggerName, Level level)
    {
        loggers.computeIfAbsent(loggerName, java.util.logging.Logger::getLogger)
                .setLevel(level.toJulLevel());
    }

    public Map<String, Level> getAllLevels()
    {
        ImmutableSortedMap.Builder<String, Level> levels = ImmutableSortedMap.naturalOrder();
        for (String loggerName : Collections.list(LogManager.getLogManager().getLoggerNames())) {
            java.util.logging.Level level = java.util.logging.Logger.getLogger(loggerName).getLevel();
            if (level != null) {
                levels.put(loggerName, Level.fromJulLevel(level));
            }
        }
        return levels.build();
    }

    public synchronized void configure(LoggingConfiguration config)
    {
        if (configured) {
            log.warn("Logging already configured; ignoring new configuration.");
            return;
        }
        configured = true;

        Map<String, String> logAnnotations = ImmutableMap.of();
        if (config.getLogAnnotationFile() != null) {
            try {
                logAnnotations = replaceEnvironmentVariables(loadPropertiesFrom(config.getLogAnnotationFile()));
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        if (config.getLogPath() != null) {
            if (config.getLogPath().startsWith("tcp://")) {
                logToSocket(config.getLogPath(), config.getFormat().createFormatter(logAnnotations));
            }
            else {
                logToFile(
                        config.getLogPath(),
                        config.getMaxSize(),
                        config.getMaxTotalSize(),
                        config.getCompression(),
                        config.getFormat().createFormatter(logAnnotations));
            }
        }

        if (!config.isConsoleEnabled()) {
            disableConsole();
        }

        if (config.getLevelsFile() != null) {
            try {
                setLevels(config.getLevelsFile());
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
