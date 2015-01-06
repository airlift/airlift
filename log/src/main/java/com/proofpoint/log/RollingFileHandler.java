package com.proofpoint.log;

import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.encoder.EncoderBase;

import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static com.proofpoint.log.Logging.createFileAppender;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.logging.ErrorManager.CLOSE_FAILURE;
import static java.util.logging.ErrorManager.FORMAT_FAILURE;
import static java.util.logging.ErrorManager.WRITE_FAILURE;

final class RollingFileHandler
        extends Handler
{
    private final Appender<String> fileAppender;

    RollingFileHandler(String filename, int retainDays, long maxSizeInBytes)
    {
        setFormatter(new StaticFormatter());

        fileAppender = createFileAppender(filename, retainDays, maxSizeInBytes, new StringEncoder(), new ContextBase());
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

    private static final class StringEncoder
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
}
