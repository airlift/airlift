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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * An OutputStream that writes contents to a Logger upon each call to flush()
 */
class LoggingOutputStream
        extends ByteArrayOutputStream
{
    private final String lineSeparator;
    private final org.slf4j.Logger logger;

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
