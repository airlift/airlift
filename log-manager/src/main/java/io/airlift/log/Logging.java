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

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.jul.LevelChangePropagator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.status.Status;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.LogManager;

/**
 * Initializes the logging subsystem.
 * <p/>
 * java.util.Logging, System.out & System.err are tunneled through the logging system.
 * <p/>
 * System.out and System.err are assigned to loggers named "stdout" and "stderr", respectively.
 */
public class Logging
{
    private static final String PATTERN = "%d{yyyy-MM-dd'T'HH:mm:ss.SSSZ}\\t%5p\\t%t\\t%c\\t%m%n";
    private final LoggerContext context;
    private final ch.qos.logback.classic.Logger root;
    private final static Logger log = Logger.get(Logging.class);
    private OutputStreamAppender<ILoggingEvent> consoleAppender;

    private static final String TEMP_FILE_EXTENSION = ".tmp";
    private static final String LOG_FILE_EXTENSION = ".log";

    private static Logging instance;

    public enum Level
    {
        DEBUG, INFO, WARN, ERROR
    }

    /**
     * Sets up default logging:
     * <p/>
     * - INFO level
     * - Log entries are written to stderr
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
        // initialize root logger
        root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

        // assume SLF4J is bound to logback in the current environment
        context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        LevelChangePropagator levelPropagator = new LevelChangePropagator();
        levelPropagator.setContext(context);
        context.addListener(levelPropagator);

        root.setLevel(ch.qos.logback.classic.Level.INFO);

        redirectJULToSLF4j();
        rewireStdStreams();
    }

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    private void rewireStdStreams()
    {
        redirectSlf4jTo(new NonCloseableOutputStream(System.err));
        log.info("Logging to stderr");

        redirectStdStreams();
    }

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    private void redirectStdStreams()
    {
        System.setOut(new PrintStream(new LoggingOutputStream(Logger.get("stdout")), true));
        System.setErr(new PrintStream(new LoggingOutputStream(Logger.get("stderr")), true));
    }

    private void redirectSlf4jTo(OutputStream stream)
    {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern(PATTERN);
        encoder.setContext(context);
        encoder.start();

        consoleAppender = new OutputStreamAppender<>();
        consoleAppender.setContext(context);
        consoleAppender.setEncoder(encoder);
        consoleAppender.setOutputStream(stream); // needs to happen after setEncoder()
        consoleAppender.start();
        root.addAppender(consoleAppender);
    }

    public void disableConsole()
    {
        log.info("Disabling stderr output");
        root.detachAppender(consoleAppender);
        consoleAppender.stop();
    }

    public void logToFile(String logPath, int maxHistory, long maxSizeInBytes)
    {
        log.info("Logging to %s", logPath);

        recoverTempFiles(logPath);

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern(PATTERN);
        encoder.setContext(context);
        encoder.start();

        RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<>();
        TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<>();
        SizeAndTimeBasedFNATP<ILoggingEvent> triggeringPolicy = new SizeAndTimeBasedFNATP<>();

        rollingPolicy.setContext(context);
        rollingPolicy.setFileNamePattern(logPath + "-%d{yyyy-MM-dd}.%i.log.gz");
        rollingPolicy.setMaxHistory(maxHistory);
        rollingPolicy.setTimeBasedFileNamingAndTriggeringPolicy(triggeringPolicy);
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.start();

        triggeringPolicy.setMaxFileSize(Long.toString(maxSizeInBytes));
        triggeringPolicy.setContext(context);
        triggeringPolicy.setTimeBasedRollingPolicy(rollingPolicy);
        triggeringPolicy.start();

        fileAppender.setFile(logPath);
        fileAppender.setAppend(true);
        fileAppender.setEncoder(encoder);
        fileAppender.setRollingPolicy(rollingPolicy);
        fileAppender.setContext(context);
        fileAppender.start();
        root.addAppender(fileAppender);
    }

    private void redirectJULToSLF4j()
    {
        java.util.logging.Logger javaRootLogger = LogManager.getLogManager().getLogger("");
        for (Handler handler : javaRootLogger.getHandlers()) {
            javaRootLogger.removeHandler(handler);
        }
        javaRootLogger.addHandler(new SLF4JBridgeHandler());
    }

    public void setLevels(File file)
            throws IOException
    {
        Properties properties = new Properties();
        try (Reader reader = new FileReader(file)) {
            properties.load(reader);
        }

        processLevels(properties);
    }

    public void setLevel(String loggerName, Level level)
    {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(loggerName);
        logger.setLevel(ch.qos.logback.classic.Level.toLevel(level.toString()));
    }

    private void processLevels(Properties properties)
    {
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String name = entry.getKey().toString();
            String level = entry.getValue().toString();

            ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(name);
            logger.setLevel(ch.qos.logback.classic.Level.toLevel(level));
        }
    }

    private void recoverTempFiles(String logPath)
    {
        // logback has a tendency to leave around temp files if it is interrupted
        // these .tmp files are log files that are about to be compressed.
        // This method recovers them so that they aren't orphaned

        File logPathFile = new File(logPath).getParentFile();
        File[] tempFiles = logPathFile.listFiles(new FilenameFilter()
        {
            @Override
            public boolean accept(File dir, String name)
            {
                return name.endsWith(TEMP_FILE_EXTENSION);
            }
        });

        if (tempFiles != null) {
            for (File tempFile : tempFiles) {
                String newName = tempFile.getName().substring(0, tempFile.getName().length() - TEMP_FILE_EXTENSION.length());
                File newFile = new File(tempFile.getParent(), newName + LOG_FILE_EXTENSION);
                if (tempFile.renameTo(newFile)) {
                    log.info("Recovered temp file: %s", tempFile);
                }
                else {
                    log.warn("Could not rename temp file [%s] to [%s]", tempFile, newFile);
                }
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
            logToFile(config.getLogPath(), config.getMaxHistory(), config.getMaxSegmentSizeInBytes());
        }

        // logback keeps a internal list of errors that it encounters as the logging system is being
        // initialized. Here we check if any errors have happened so far -- especially important before we turn
        // off console logging, as file logging may be broken due to invalid paths or missing config.
        // If any errors have occurred, log them (to the console, which is guaranteed to be properly set up)
        // and bail out with an exception
        boolean error = false;
        for (Status status : root.getLoggerContext().getStatusManager().getCopyOfStatusList()) {
            if (status.getLevel() == Status.ERROR) {
                log.error(status.getMessage());
                error = true;
            }
        }

        if (error) {
            throw new RuntimeException("Error initializing logger, aborting");
        }

        if (!config.isConsoleEnabled()) {
            disableConsole();
        }

        if (config.getLevelsFile() != null) {
            setLevels(new File(config.getLevelsFile()));
        }
    }
}
