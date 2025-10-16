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
class LoggingOutputStream extends ByteArrayOutputStream {
    private final Logger logger;

    public LoggingOutputStream(Logger logger) {
        this.logger = logger;
    }

    /**
     * write the current buffer contents to the underlying logger.
     */
    @Override
    public synchronized void flush() throws IOException {
        super.flush();
        String record = this.toString();
        reset();

        // Strip trailing new line typically added when this class is used via System.out.println
        record = record.stripTrailing();

        if (record.isEmpty()) {
            // avoid empty records
            return;
        }

        logger.info(record);
    }
}
