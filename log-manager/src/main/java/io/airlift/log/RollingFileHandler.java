package io.airlift.log;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.encoder.EncoderBase;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;

import java.io.File;
import java.io.IOException;
import java.util.logging.ErrorManager;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.logging.ErrorManager.CLOSE_FAILURE;
import static java.util.logging.ErrorManager.FORMAT_FAILURE;
import static java.util.logging.ErrorManager.WRITE_FAILURE;

final class RollingFileHandler
        extends Handler
{
    private static final String TEMP_FILE_EXTENSION = ".tmp";
    private static final String LOG_FILE_EXTENSION = ".log";

    private final RollingFileAppender<String> fileAppender;

    public RollingFileHandler(String filename, int maxHistory, long maxSizeInBytes)
    {
        setFormatter(new StaticFormatter());

        ContextBase context = new ContextBase();

        recoverTempFiles(filename);

        fileAppender = new RollingFileAppender<>();
        TimeBasedRollingPolicy<String> rollingPolicy = new TimeBasedRollingPolicy<>();
        SizeAndTimeBasedFNATP<String> triggeringPolicy = new SizeAndTimeBasedFNATP<>();

        rollingPolicy.setContext(context);
        rollingPolicy.setFileNamePattern(filename + "-%d{yyyy-MM-dd}.%i.log.gz");
        rollingPolicy.setMaxHistory(maxHistory);
        rollingPolicy.setTimeBasedFileNamingAndTriggeringPolicy(triggeringPolicy);
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.start();

        triggeringPolicy.setContext(context);
        triggeringPolicy.setTimeBasedRollingPolicy(rollingPolicy);
        triggeringPolicy.setMaxFileSize(Long.toString(maxSizeInBytes));
        triggeringPolicy.start();

        fileAppender.setContext(context);
        fileAppender.setFile(filename);
        fileAppender.setAppend(true);
        fileAppender.setEncoder(new StringEncoder());
        fileAppender.setRollingPolicy(rollingPolicy);
        fileAppender.start();
    }

    @Override
    public void publish(LogRecord record)
    {
        if (!isLoggable(record)) {
            return;
        }

        String message;
        try {
            message = getFormatter().format(record);
        }
        catch (Exception e) {
            // catch any exception to assure logging always works
            reportError(null, e, FORMAT_FAILURE);
            return;
        }

        try {
            fileAppender.doAppend(message);
        }
        catch (Exception e) {
            // catch any exception to assure logging always works
            reportError(null, e, WRITE_FAILURE);
        }
    }

    @Override
    public void flush()
    {
    }

    @Override
    public void close()
    {
        try {
            fileAppender.stop();
        }
        catch (Exception e) {
            // catch any exception to assure logging always works
            reportError(null, e, CLOSE_FAILURE);
        }
    }

    private final class StringEncoder
            extends EncoderBase<String>
    {
        @Override
        public void doEncode(String event)
                throws IOException
        {
            outputStream.write(event.getBytes(UTF_8));
            // necessary if output stream is buffered
            outputStream.flush();
        }

        @Override
        public void close()
                throws IOException
        {
            outputStream.flush();
        }
    }

    private void recoverTempFiles(String logPath)
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
                reportError(format("Could not rename temp file [%s] to [%s]", tempFile, newFile), null, ErrorManager.OPEN_FAILURE);
            }
        }
    }
}
