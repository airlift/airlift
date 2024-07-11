package io.airlift.log;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static java.nio.charset.StandardCharsets.UTF_8;

final class OutputStreamHandler
        extends Handler
{
    private final AtomicBoolean reported = new AtomicBoolean();
    private final Writer writer;
    private final Formatter formatter;

    public OutputStreamHandler(OutputStream out)
    {
        this(out, new StaticFormatter());
    }

    public OutputStreamHandler(OutputStream out, Formatter formatter)
    {
        writer = new OutputStreamWriter(out, UTF_8);
        this.formatter = formatter;
    }

    @Override
    public void publish(LogRecord record)
    {
        if (!isLoggable(record)) {
            return;
        }

        try {
            writer.write(formatter.format(record));
            writer.flush();
        }
        catch (Exception e) {
            // try to report the first error
            if (!reported.getAndSet(true)) {
                PrintWriter error = new PrintWriter(writer);
                error.print("LOGGING FAILED: ");
                e.printStackTrace(error);
                error.flush();
            }
        }
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
}
