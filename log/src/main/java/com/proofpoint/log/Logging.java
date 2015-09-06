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
package com.proofpoint.log;

import ch.qos.logback.core.Appender;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Multimap;

import javax.annotation.concurrent.GuardedBy;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Throwables.propagate;
import static com.google.common.collect.Multimaps.synchronizedMultimap;
import static com.proofpoint.log.Level.fromJulLevel;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

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

    private static final String TEMP_FILE_EXTENSION = ".tmp";
    private static final String LOG_FILE_EXTENSION = ".log";

    private static Logging instance;

    // hard reference to loggers for which we set the level
    private final Map<String, java.util.logging.Logger> loggers = new ConcurrentHashMap<>();
    private final Multimap<java.util.logging.Logger, Handler> testingHandlers = synchronizedMultimap(ArrayListMultimap.create());

    @GuardedBy("this")
    private OutputStreamHandler consoleHandler;

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

        rewireStdStreams();
    }

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    private void rewireStdStreams()
    {
        logConsole(new NonCloseableOutputStream(System.err));
        log.info("Logging to stderr");

        redirectStdStreams();
    }

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    private static void redirectStdStreams()
    {
        try {
            System.setOut(new PrintStream(new LoggingOutputStream(Logger.get("stdout")), true, "UTF-8"));
            System.setErr(new PrintStream(new LoggingOutputStream(Logger.get("stderr")), true, "UTF-8"));
        }
        catch (UnsupportedEncodingException ignored) {
        }
    }

    private synchronized void logConsole(OutputStream stream)
    {
        consoleHandler = new OutputStreamHandler(stream);
        ROOT.addHandler(consoleHandler);
    }

    public synchronized void disableConsole()
    {
        log.info("Disabling stderr output");
        ROOT.removeHandler(consoleHandler);
        consoleHandler = null;
    }

    @SuppressWarnings("MethodMayBeStatic")
    public void logToFile(String logPath, int maxHistory, long maxSizeInBytes)
    {
        log.info("Logging to %s", logPath);

        RollingFileHandler rollingFileHandler = new RollingFileHandler(logPath, maxHistory, maxSizeInBytes);
        ROOT.addHandler(rollingFileHandler);
    }

    /**
     * Add a {@link LogTester} to the logger named after a class' fully qualified name.
     *
     * @param clazz the class
     * @param logTester the LogTester
     */
    @VisibleForTesting
    public static void addLogTester(Class<?> clazz, LogTester logTester)
    {
        addLogTester(clazz.getName(), logTester);
    }

    /**
     * Add a {@link LogTester} to a named logger. Intended for writing unit tests that verify logging.
     *
     * @param name the name of the logger
     * @param logTester the LogTester
     */
    @VisibleForTesting
    public static void addLogTester(String name, LogTester logTester)
    {
        checkState(instance != null, "Logging is not initialized");
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(name);
        Handler handler = new Handler()
        {
            @Override
            public void publish(LogRecord record)
            {
                logTester.log(fromJulLevel(record.getLevel()), record.getMessage(), Optional.ofNullable(record.getThrown()));
            }

            @Override
            public void flush()
            {
            }

            @Override
            public void close()
            {
            }
        };
        instance.testingHandlers.put(logger, handler);
        logger.addHandler(handler);
    }

    /**
     * Remove all installed {@link LogTester}s
     */
    @VisibleForTesting
    public static void resetLogTesters()
    {
        if (instance == null) {
            return;
        }

        for (Entry<java.util.logging.Logger, Handler> entry : instance.testingHandlers.entries()) {
            entry.getKey().removeHandler(entry.getValue());
        }
        instance.testingHandlers.clear();
    }

    public static <T> Appender<T> createFileAppender(String logPath, int maxHistory, long maxSizeInBytes, Encoder<T> encoder, Context context)
    {
        recoverTempFiles(logPath);

        RollingFileAppender<T> fileAppender = new RollingFileAppender<>();
        TimeBasedRollingPolicy<T> rollingPolicy = new TimeBasedRollingPolicy<>();
        SizeAndTimeBasedFNATP<T> triggeringPolicy = new SizeAndTimeBasedFNATP<>();

        rollingPolicy.setContext(context);
        rollingPolicy.setFileNamePattern(logPath + "-%d{yyyy-MM-dd}.%i.log.gz");
        rollingPolicy.setMaxHistory(maxHistory);
        rollingPolicy.setTimeBasedFileNamingAndTriggeringPolicy(triggeringPolicy);
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.start();

        triggeringPolicy.setContext(context);
        triggeringPolicy.setTimeBasedRollingPolicy(rollingPolicy);
        triggeringPolicy.setMaxFileSize(Long.toString(maxSizeInBytes));
        triggeringPolicy.start();

        fileAppender.setContext(context);
        fileAppender.setFile(logPath);
        fileAppender.setAppend(true);
        fileAppender.setEncoder(encoder);
        fileAppender.setRollingPolicy(rollingPolicy);
        fileAppender.start();
        return fileAppender;
    }

    public Level getRootLevel()
    {
        return getLevel(ROOT_LOGGER_NAME);
    }

    public void setRootLevel(Level newLevel)
    {
        setLevel(ROOT_LOGGER_NAME, newLevel);
    }

    public void setLevels(File file)
            throws IOException
    {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(new FileInputStream(file), UTF_8)) {
            properties.load(reader);
        }

        processLevels(properties);
    }

    @SuppressWarnings("MethodMayBeStatic")
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
        return fromJulLevel(level);
    }

    @SuppressWarnings("MethodMayBeStatic")
    public void setLevel(String loggerName, Level level)
    {
        loggers.computeIfAbsent(loggerName, java.util.logging.Logger::getLogger)
                .setLevel(level.toJulLevel());
    }

    @SuppressWarnings("MethodMayBeStatic")
    public Map<String, Level> getAllLevels()
    {
        ImmutableSortedMap.Builder<String, Level> levels = ImmutableSortedMap.naturalOrder();
        for (String loggerName : Collections.list(LogManager.getLogManager().getLoggerNames())) {
            java.util.logging.Level level = java.util.logging.Logger.getLogger(loggerName).getLevel();
            if (level != null) {
                levels.put(loggerName, fromJulLevel(level));
            }
        }
        return levels.build();
    }

    private void processLevels(Properties properties)
    {
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String loggerName = entry.getKey().toString();
            Level level = Level.valueOf(entry.getValue().toString().toUpperCase(Locale.US));

            setLevel(loggerName, level);
        }
    }

    private static void recoverTempFiles(String logPath)
    {
        // Logback has a tendency to leave around temp files if it is interrupted.
        // These .tmp files are log files that are about to be compressed.
        // This method recovers them so that they aren't orphaned.

        File logPathFile = new File(logPath).getParentFile();
        File[] tempFiles = logPathFile.listFiles((dir, name) -> name.endsWith(TEMP_FILE_EXTENSION));

        if (tempFiles == null) {
            return;
        }

        for (File tempFile : tempFiles) {
            String newName = tempFile.getName().substring(0, tempFile.getName().length() - TEMP_FILE_EXTENSION.length());
            File newFile = new File(tempFile.getParent(), newName + LOG_FILE_EXTENSION);

            if (!tempFile.renameTo(newFile)) {
                log.warn("Could not rename temp file [%s] to [%s]", tempFile, newFile);
            }
        }
    }

    public void configure(LoggingConfiguration config)
            throws IOException
    {
        if (config.getLogPath() == null && !config.isConsoleEnabled()) {
            throw new IllegalArgumentException("No log file is configured (log.output-file) and logging to console is disabled (log.enable-console)");
        }

        if (config.getLogPath() != null) {
            logToFile(config.getLogPath(), config.getMaxHistory(), config.getMaxSegmentSize().toBytes());
            setupBootstrapLog(config);
        }

        if (!config.isConsoleEnabled()) {
            disableConsole();
        }

        if (config.getLevelsFile() != null) {
            setLevels(new File(config.getLevelsFile()));
        }
    }

    private void setupBootstrapLog(LoggingConfiguration config)
            throws IOException
    {
        Path logPath = Paths.get(config.getLogPath())
                .getParent()
                .resolve("bootstrap.log");
        BufferedWriter writer = Files.newBufferedWriter(logPath, WRITE, CREATE, TRUNCATE_EXISTING);
        java.util.logging.Logger.getLogger("Bootstrap").addHandler(new Handler()
        {
            @Override
            public void publish(LogRecord record)
            {
                try {
                    writer.write(record.getMessage());
                    writer.newLine();
                    writer.flush();
                }
                catch (IOException e) {
                    throw propagate(e);
                }
            }

            @Override
            public void flush()
            {
                try {
                    writer.flush();
                }
                catch (IOException e) {
                    throw propagate(e);
                }
            }

            @Override
            public void close()
            {
                try {
                    writer.close();
                }
                catch (IOException ignored) {
                }
            }
        });
    }
}
