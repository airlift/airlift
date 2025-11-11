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

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@TestInstance(PER_CLASS)
@Execution(SAME_THREAD)
public class TestLogger
{
    private MockHandler handler;
    private Logger logger;
    private java.util.logging.Logger inner;

    @BeforeEach
    public void setUp()
    {
        handler = new MockHandler();

        inner = java.util.logging.Logger.getAnonymousLogger();
        inner.setUseParentHandlers(false);
        inner.setLevel(Level.ALL);
        inner.addHandler(handler);

        logger = new Logger(inner);
    }

    @AfterEach
    public void teardown()
    {
        assertThat(handler.isEmpty())
                .as("Some log messages were not verified by test")
                .isTrue();
    }

    @Test
    public void testIsDebugEnabled()
    {
        inner.setLevel(Level.FINE);
        assertThat(logger.isDebugEnabled()).isTrue();

        inner.setLevel(Level.INFO);
        assertThat(logger.isDebugEnabled()).isFalse();

        inner.setLevel(Level.WARNING);
        assertThat(logger.isDebugEnabled()).isFalse();

        inner.setLevel(Level.SEVERE);
        assertThat(logger.isDebugEnabled()).isFalse();
    }

    @Test
    public void testDebugFormat()
    {
        inner.setLevel(Level.FINE);
        logger.debug("hello, %s", "you");

        assertLog(Level.FINE, "hello, you");
    }

    @Test
    public void testInfoFormat()
    {
        inner.setLevel(Level.INFO);
        logger.info("hello, %s", "you");

        assertLog(Level.INFO, "hello, you");
    }

    @Test
    public void testWarnFormat()
    {
        inner.setLevel(Level.WARNING);

        // message-only version
        logger.warn("hello, %s", "you");
        assertLog(Level.WARNING, "hello, you");

        // throwable with message
        @SuppressWarnings("ThrowableInstanceNeverThrown")
        Throwable exception = new Throwable();
        logger.warn(exception, "got exception: %s", "foo");
        assertLog(Level.WARNING, "got exception: foo", exception);
    }

    @Test
    public void testErrorFormat()
    {
        // message-only version
        logger.error("hello, %s", "you");
        assertLog(Level.SEVERE, "hello, you");

        // throwable with message
        @SuppressWarnings("ThrowableInstanceNeverThrown")
        Throwable exception = new Throwable();

        logger.error(exception, "got exception: %s", "foo");
        assertLog(Level.SEVERE, "got exception: foo", exception);

        // throwable alone
        @SuppressWarnings("ThrowableInstanceNeverThrown")
        Throwable exception2 = new Throwable("the message");
        logger.error(exception2);
        assertLog(Level.SEVERE, exception2.getMessage(), exception2);
    }

    @Test
    public void testDebugShortCircuit()
    {
        inner.setLevel(Level.OFF);
        logger.debug("hello");
        assertThat(handler.isEmpty()).isTrue();
    }

    @Test
    public void testInfoShortCircuit()
    {
        inner.setLevel(Level.OFF);
        logger.info("hello");
        assertThat(handler.isEmpty()).isTrue();
    }

    @Test
    public void testWarnShortCircuit()
    {
        inner.setLevel(Level.OFF);
        logger.warn("hello");
        assertThat(handler.isEmpty()).isTrue();
    }

    @Test
    public void testWarnWithThrowableShortCircuit()
    {
        inner.setLevel(Level.OFF);

        @SuppressWarnings("ThrowableInstanceNeverThrown")
        Throwable e = new Throwable();
        logger.warn(e, "hello");

        assertThat(handler.isEmpty()).isTrue();
    }

    @Test
    public void testErrorShortCircuit()
    {
        inner.setLevel(Level.OFF);
        logger.error("hello");
        assertThat(handler.isEmpty()).isTrue();
    }

    @Test
    public void testErrorWithThrowableShortCircuit()
    {
        inner.setLevel(Level.OFF);

        @SuppressWarnings("ThrowableInstanceNeverThrown")
        Throwable e = new Throwable();
        logger.error(e, "hello");

        assertThat(handler.isEmpty()).isTrue();
    }

    @Test
    public void testErrorWithThrowableNoMessageShortCircuit()
    {
        inner.setLevel(Level.OFF);

        @SuppressWarnings("ThrowableInstanceNeverThrown")
        Throwable e = new Throwable();
        logger.error(e);

        assertThat(handler.isEmpty()).isTrue();
    }

    @Test
    public void testInsufficientArgsLogsErrorForDebug()
    {
        String format = "some message: %s, %d";
        String param = "blah";
        logger.debug(format, param);

        assertLogLike(Level.SEVERE, ImmutableList.of("Invalid format", "DEBUG", format, param), IllegalArgumentException.class);
        assertLog(Level.FINE, String.format("'%s' [%s]", format, param));
    }

    @Test
    public void testInsufficientArgsLogsErrorForInfo()
    {
        String format = "some message: %s, %d";
        String param = "blah";
        logger.info(format, param);

        assertLogLike(Level.SEVERE, ImmutableList.of("Invalid format", "INFO", format, param), IllegalArgumentException.class);
        assertLog(Level.INFO, String.format("'%s' [%s]", format, param));
    }

    @Test
    public void testInsufficientArgsLogsErrorForWarn()
    {
        String format = "some message: %s, %d";
        String param = "blah";
        logger.warn(format, param);

        assertLogLike(Level.SEVERE, ImmutableList.of("Invalid format", "WARN", format, param), IllegalArgumentException.class);
        assertLog(Level.WARNING, String.format("'%s' [%s]", format, param));
    }

    @Test
    public void testInsufficientArgsLogsErrorForError()
    {
        String format = "some message: %s, %d";
        String param = "blah";
        logger.error(format, param);

        assertLogLike(Level.SEVERE, ImmutableList.of("Invalid format", "ERROR", format, param), IllegalArgumentException.class);
        assertLog(Level.SEVERE, String.format("'%s' [%s]", format, param));
    }

    @Test
    public void testInsufficientArgsLogsOriginalExceptionForWarn()
    {
        Throwable exception = new Throwable("foo");
        String format = "some message: %s, %d";
        String param = "blah";
        logger.warn(exception, format, param);

        assertLogLike(Level.SEVERE, ImmutableList.of("Invalid format", "WARN", format, param), IllegalArgumentException.class);
        assertLog(Level.WARNING, String.format("'%s' [%s]", format, param), exception);
    }

    @Test
    public void testInsufficientArgsLogsOriginalExceptionForError()
    {
        Throwable exception = new Throwable("foo");
        String format = "some message: %s, %d";
        String param = "blah";
        logger.error(exception, format, param);

        assertLogLike(Level.SEVERE, ImmutableList.of("Invalid format", "ERROR", format, param), IllegalArgumentException.class);
        assertLog(Level.SEVERE, String.format("'%s' [%s]", format, param), exception);
    }

    private void assertLog(Level level, String message, Throwable exception)
    {
        LogRecord record = handler.takeRecord();
        assertThat(record.getLevel()).isEqualTo(level);
        assertThat(record.getMessage()).isEqualTo(message);
        assertThat(record.getThrown()).isEqualTo(exception);
    }

    private void assertLog(Level level, String message)
    {
        LogRecord record = handler.takeRecord();
        assertThat(record.getLevel()).isEqualTo(level);
        assertThat(record.getMessage()).isEqualTo(message);
        assertThat(record.getThrown()).isNull();
    }

    private void assertLogLike(Level level, List<String> substrings, Class<? extends Throwable> exceptionClass)
    {
        LogRecord record = handler.takeRecord();
        assertThat(record.getLevel()).isEqualTo(level);
        assertThat(stringContains(record.getMessage(), substrings)).isTrue();
        assertThat(exceptionClass.isAssignableFrom(record.getThrown().getClass())).isTrue();
    }

    private boolean stringContains(String value, List<String> substrings)
    {
        for (String str : substrings) {
            if (!value.contains(str)) {
                return false;
            }
        }

        return true;
    }

    private static class MockHandler
            extends Handler
    {
        private final List<LogRecord> records = new ArrayList<>();

        private MockHandler()
        {
            setLevel(Level.ALL);
        }

        @Override
        public void publish(LogRecord record)
        {
            records.add(record);
        }

        @Override
        public void flush()
        {
        }

        @Override
        public void close()
                throws SecurityException
        {
        }

        public LogRecord takeRecord()
        {
            assertThat(!records.isEmpty()).as("No messages logged").isTrue();
            return records.removeFirst();
        }

        public boolean isEmpty()
        {
            return records.isEmpty();
        }
    }
}
