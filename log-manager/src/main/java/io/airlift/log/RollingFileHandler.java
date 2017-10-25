package io.airlift.log;

import ch.qos.logback.core.AsyncAppenderBase;
import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.encoder.EncoderBase;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;
import io.airlift.units.DataSize;

import java.io.File;
import java.util.logging.ErrorManager;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static io.airlift.units.DataSize.Unit.MEGABYTE;
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
    private static final FileSize BUFFER_SIZE_IN_BYTES = new FileSize(new DataSize(1, MEGABYTE).toBytes());

    private final AsyncAppenderBase<String> asyncAppender;

    public RollingFileHandler(String filename, int maxHistory, long maxSizeInBytes)
    {
        setFormatter(new StaticFormatter());

        ContextBase context = new ContextBase();

        recoverTempFiles(filename);

        RollingFileAppender<String> fileAppender = new RollingFileAppender<>();
        TimeBasedRollingPolicy<String> rollingPolicy = new TimeBasedRollingPolicy<>();
        SizeAndTimeBasedFNATP<String> triggeringPolicy = new SizeAndTimeBasedFNATP<>();

        rollingPolicy.setContext(context);
        rollingPolicy.setFileNamePattern(filename + "-%d{yyyy-MM-dd}.%i.log.gz");
        rollingPolicy.setMaxHistory(maxHistory);
        rollingPolicy.setTimeBasedFileNamingAndTriggeringPolicy(triggeringPolicy);
        rollingPolicy.setParent(fileAppender);

        triggeringPolicy.setContext(context);
        triggeringPolicy.setTimeBasedRollingPolicy(rollingPolicy);
        triggeringPolicy.setMaxFileSize(new FileSize(maxSizeInBytes));

        fileAppender.setContext(context);
        fileAppender.setFile(filename);
        fileAppender.setAppend(true);
        fileAppender.setBufferSize(BUFFER_SIZE_IN_BYTES);
        fileAppender.setEncoder(new StringEncoder());
        fileAppender.setRollingPolicy(rollingPolicy);

        asyncAppender = new AsyncAppenderBase<>();
        asyncAppender.setContext(context);
        asyncAppender.addAppender(fileAppender);

        rollingPolicy.start();
        triggeringPolicy.start();
        fileAppender.start();
        asyncAppender.start();
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
            asyncAppender.doAppend(message);
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
            asyncAppender.stop();
        }
        catch (Exception e) {
            // catch any exception to assure logging always works
            reportError(null, e, CLOSE_FAILURE);
        }
    }

    private static final class StringEncoder
            extends EncoderBase<String>
    {
        private static final byte[] EMPTY_BYTES = new byte[0];

        @Override
        public byte[] headerBytes()
        {
            return EMPTY_BYTES;
        }

        @Override
        public byte[] encode(String event)
        {
            return event.getBytes(UTF_8);
        }

        @Override
        public byte[] footerBytes()
        {
            return EMPTY_BYTES;
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
