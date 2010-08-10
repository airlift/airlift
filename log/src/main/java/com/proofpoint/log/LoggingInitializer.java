package com.proofpoint.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.Properties;
import java.util.logging.*;
import java.util.logging.Logger;

public class LoggingInitializer
{
    private final String PATTERN = "%d{yyyy-MM-dd'T'HH:mm:ss.SSSZ}\\t%5p\\t%t\\t%c\\t%m%n";

    private final String logPath;
    private final int maxHistory;
    private final String maxSize;
    private final String levelsFile;

    public LoggingInitializer(String logPath, int maxHistory, String maxSize, String levelsFile)
    {
        this.logPath = logPath;
        this.maxHistory = maxHistory;
        this.maxSize = maxSize;
        this.levelsFile = levelsFile;
    }

    public void initialize()
    {
        redirectJULToSLF4j();

        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(
                org.slf4j.Logger.ROOT_LOGGER_NAME);

        // assume SLF4J is bound to logback in the current environment
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();
        root.setLevel(Level.INFO);

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern(PATTERN);
        encoder.setContext(context);
        encoder.start();

        if (logPath.isEmpty()) {
            OutputStreamAppender<ILoggingEvent> appender = new OutputStreamAppender<ILoggingEvent>();
            appender.setContext(context);
            appender.setEncoder(encoder);
            appender.setOutputStream(System.out); // needs to happen after setEncoder()
            appender.start();

            root.addAppender(appender);
        }
        else {
            RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<ILoggingEvent>();
            TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<ILoggingEvent>();
            SizeAndTimeBasedFNATP<ILoggingEvent> triggeringPolicy = new SizeAndTimeBasedFNATP<ILoggingEvent>();

            rollingPolicy.setContext(context);
            rollingPolicy.setFileNamePattern(logPath + "-%d{yyyy-MM-dd}.%i.log.gz");
            rollingPolicy.setMaxHistory(maxHistory);
            rollingPolicy.setTimeBasedFileNamingAndTriggeringPolicy(triggeringPolicy);
            rollingPolicy.setParent(appender);
            rollingPolicy.start();

            triggeringPolicy.setMaxFileSize(maxSize);
            triggeringPolicy.setContext(context);
            triggeringPolicy.setTimeBasedRollingPolicy(rollingPolicy);
            triggeringPolicy.start();

            appender.setFile(logPath);
            appender.setAppend(true);
            appender.setEncoder(encoder);
            appender.setRollingPolicy(rollingPolicy);
            appender.setContext(context);
            appender.start();

            root.addAppender(appender);
        }

        try {
            if (!levelsFile.isEmpty()) {
                processLevels(levelsFile);
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void redirectJULToSLF4j()
    {
        Logger javaRootLogger = LogManager.getLogManager().getLogger("");
        for (Handler handler : javaRootLogger.getHandlers()) {
            javaRootLogger.removeHandler(handler);
        }
        javaRootLogger.addHandler(new JavaLoggingToSlf4JHandler());
    }

    private void processLevels(String levelsFile)
            throws IOException
    {
        File file = new File(levelsFile);
        Properties properties = new Properties();
        Reader reader = new FileReader(file);
        try {
            properties.load(reader);
        }
        finally {
            reader.close();
        }

        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String name = entry.getKey().toString();

            ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(name);
            logger.setLevel(Level.toLevel(entry.getValue().toString()));
        }
    }

}
