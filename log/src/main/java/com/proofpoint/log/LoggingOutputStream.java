package com.proofpoint.log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * An OutputStream that writes contents to a Logger upon each call to flush()
 */
class LoggingOutputStream
        extends ByteArrayOutputStream
{
    private String lineSeparator;

    private org.slf4j.Logger logger;

    public LoggingOutputStream(org.slf4j.Logger logger)
    {
        super();
        this.logger = logger;
        lineSeparator = System.getProperty("line.separator");
    }

    /**
     * write the current buffer contents to the underlying logger.
     */
    public synchronized void flush()
            throws IOException
    {
        super.flush();
        String record = this.toString();
        super.reset();

        if (record.isEmpty() || record.equals(lineSeparator)) {
            // avoid empty records
            return;
        }

        logger.info(record);
    }
}