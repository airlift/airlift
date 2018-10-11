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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

@Test(singleThreaded = true)
public class TestLogger
{
    private MockHandler handler;
    private Logger logger;
    private java.util.logging.Logger inner;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        handler = new MockHandler();

        inner = java.util.logging.Logger.getAnonymousLogger();
        inner.setUseParentHandlers(false);
        inner.setLevel(Level.ALL);
        inner.addHandler(handler);

        logger = new Logger(inner);
    }

    @AfterMethod(alwaysRun = true)
    public void teardown()
    {
        assertTrue(handler.isEmpty(), "Some log messages were not verified by test");
    }

    @Test
    public void testIsDebugEnabled()
    {
        inner.setLevel(Level.FINE);
        assertTrue(logger.isDebugEnabled());

        inner.setLevel(Level.INFO);
        assertFalse(logger.isDebugEnabled());

        inner.setLevel(Level.WARNING);
        assertFalse(logger.isDebugEnabled());

        inner.setLevel(Level.SEVERE);
        assertFalse(logger.isDebugEnabled());
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
        assertTrue(handler.isEmpty());
    }

    @Test
    public void testInfoShortCircuit()
    {
        inner.setLevel(Level.OFF);
        logger.info("hello");
        assertTrue(handler.isEmpty());
    }

    @Test
    public void testWarnShortCircuit()
    {
        inner.setLevel(Level.OFF);
        logger.warn("hello");
        assertTrue(handler.isEmpty());
    }

    @Test
    public void testWarnWithThrowableShortCircuit()
    {
        inner.setLevel(Level.OFF);

        @SuppressWarnings("ThrowableInstanceNeverThrown")
        Throwable e = new Throwable();
        logger.warn(e, "hello");

        assertTrue(handler.isEmpty());
    }

    @Test
    public void testErrorShortCircuit()
    {
        inner.setLevel(Level.OFF);
        logger.error("hello");
        assertTrue(handler.isEmpty());
    }

    @Test
    public void testErrorWithThrowableShortCircuit()
    {
        inner.setLevel(Level.OFF);

        @SuppressWarnings("ThrowableInstanceNeverThrown")
        Throwable e = new Throwable();
        logger.error(e, "hello");

        assertTrue(handler.isEmpty());
    }

    @Test
    public void testErrorWithThrowableNoMessageShortCircuit()
    {
        inner.setLevel(Level.OFF);

        @SuppressWarnings("ThrowableInstanceNeverThrown")
        Throwable e = new Throwable();
        logger.error(e);

        assertTrue(handler.isEmpty());
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
        assertEquals(record.getLevel(), level);
        assertEquals(record.getMessage(), message);
        assertEquals(record.getThrown(), exception);
    }

    private void assertLog(Level level, String message)
    {
        LogRecord record = handler.takeRecord();
        assertEquals(record.getLevel(), level);
        assertEquals(record.getMessage(), message);
        assertNull(record.getThrown());
    }

    private void assertLogLike(Level level, List<String> substrings, Class<? extends Throwable> exceptionClass)
    {
        LogRecord record = handler.takeRecord();
        assertEquals(record.getLevel(), level);
        assertTrue(stringContains(record.getMessage(), substrings));
        assertTrue(exceptionClass.isAssignableFrom(record.getThrown().getClass()));
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
            assertTrue(!records.isEmpty(), "No messages logged");
            return records.remove(0);
        }

        public boolean isEmpty()
        {
            return records.isEmpty();
        }
    }
}
