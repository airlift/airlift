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
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.LogManager;

/**
 * Initializes the logging subsystem.
 *
 * java.util.Logging, System.out & System.err are tunneled through the logging system.
 *
 * System.out and System.err are assigned to loggers named "stdout" and "stderr", respectively.  
 */
public class Logging
{
    private final String PATTERN = "%d{yyyy-MM-dd'T'HH:mm:ss.SSSZ}\\t%5p\\t%t\\t%c\\t%m%n";
    private LoggerContext context;
    private ch.qos.logback.classic.Logger root;
    private Logger log = Logger.get(Logging.class);
    private OutputStreamAppender<ILoggingEvent> consoleAppender;
    private RollingFileAppender<ILoggingEvent> fileAppender;

    /**
     * Sets up default logging:
     *
     * - INFO level
     * - Log entries are written to stderr
     */
    public Logging()
    {
        // initialize root logger
        root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

        // assume SLF4J is bound to logback in the current environment
        context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();
        root.setLevel(Level.INFO);

        rewireStdStreams();
        redirectJULToSLF4j();
    }

    private void rewireStdStreams()
    {
        OutputStream out = System.err;

        redirectSlf4jTo(out);
        log.info("Logging to stderr");

        redirectStdStreamsToSlf4j();
    }

    private void redirectStdStreamsToSlf4j()
    {
        System.setOut(new PrintStream(new LoggingOutputStream(LoggerFactory.getLogger("stdout"))));
        System.setErr(new PrintStream(new LoggingOutputStream(LoggerFactory.getLogger("stderr"))));
    }

    private void redirectSlf4jTo(OutputStream stream)
    {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern(PATTERN);
        encoder.setContext(context);
        encoder.start();

        consoleAppender = new OutputStreamAppender<ILoggingEvent>();
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

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern(PATTERN);
        encoder.setContext(context);
        encoder.start();
        
        fileAppender = new RollingFileAppender<ILoggingEvent>();
        TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<ILoggingEvent>();
        SizeAndTimeBasedFNATP<ILoggingEvent> triggeringPolicy = new SizeAndTimeBasedFNATP<ILoggingEvent>();

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
        javaRootLogger.addHandler(new JavaLoggingToSlf4JHandler());
    }

    public void setLevels(File file)
            throws IOException
    {
        Properties properties = new Properties();
        Reader reader = new FileReader(file);
        try {
            properties.load(reader);
        }
        finally {
            reader.close();
        }
        
        processLevels(properties);
    }

    private void processLevels(Properties properties)
            throws IOException
    {
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String name = entry.getKey().toString();

            ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(name);
            logger.setLevel(Level.toLevel(entry.getValue().toString()));
        }
    }


    public void initialize(LoggingConfiguration config)
            throws IOException
    {
        if (config.getLogPath() != null) {
            logToFile(config.getLogPath(), config.getMaxHistory(), config.getMaxSegmentSizeInBytes());
        }
        if (!config.isConsoleEnabled()) {
            disableConsole();
        }

        if (config.getLevelsFile() != null) {
            setLevels(new File(config.getLevelsFile()));
        }
    }
}
