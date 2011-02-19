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
package com.proofpoint.log;

import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

class JavaLoggingToSlf4JHandler
        extends Handler
{
    public void publish(LogRecord record)
    {
        org.slf4j.Logger logger = LoggerFactory.getLogger(record.getLoggerName());

        Level level = record.getLevel();

        if (level == Level.SEVERE) {
            logger.error(format(record), record.getThrown());
        }
        else if (level == Level.WARNING) {
            logger.warn(format(record), record.getThrown());
        }
        else if (level == Level.INFO) {
            logger.info(format(record), record.getThrown());
        }
        else if (level == Level.FINE || level == Level.FINER || level == Level.FINEST) {
            logger.debug(format(record), record.getThrown());
        }
    }

    private String format(LogRecord record)
    {
        String message = record.getMessage();
        try {
            Object parameters[] = record.getParameters();
            if (parameters != null && parameters.length != 0) {
                message = MessageFormat.format(message, parameters);
            }
        }
        catch (Exception ex) {
            // ignore Exception
        }
        return message;
    }

    @Override
    public void flush()
    {
        // nothing to do
    }

    @Override
    public void close()
    {
        // nothing to do
    }
}
