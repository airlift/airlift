package com.proofpoint.log;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.logging.ErrorManager.CLOSE_FAILURE;
import static java.util.logging.ErrorManager.FLUSH_FAILURE;
import static java.util.logging.ErrorManager.FORMAT_FAILURE;
import static java.util.logging.ErrorManager.WRITE_FAILURE;

final class OutputStreamHandler
        extends Handler
{
    private final Writer writer;

    public OutputStreamHandler(OutputStream out)
    {
        writer = new OutputStreamWriter(out, UTF_8);
        setFormatter(new StaticFormatter());
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
            reportError(null, e, FORMAT_FAILURE);
            return;
        }

        try {
            writer.write(message);
        }
        catch (Exception e) {
            reportError(null, e, WRITE_FAILURE);
        }
    }

    @Override
    public void flush()
    {
        try {
            writer.flush();
        }
        catch (Exception e) {
            reportError(null, e, FLUSH_FAILURE);
        }
    }

    @Override
    public void close()
    {
        try {
            writer.flush();
        }
        catch (Exception e) {
            reportError(null, e, CLOSE_FAILURE);
        }
    }
}
