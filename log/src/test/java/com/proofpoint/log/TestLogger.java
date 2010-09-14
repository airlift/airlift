package com.proofpoint.log;

import org.testng.annotations.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;

public class TestLogger
{
    @Test
    public void testIsDebugEnabled()
    {
        org.slf4j.Logger mockLogger = mock(org.slf4j.Logger.class);
        when(mockLogger.isDebugEnabled()).thenReturn(true);

        Logger logger = new Logger(mockLogger);

        assertTrue(logger.isDebugEnabled());
    }

    @Test
    public void testDebugFormat()
    {
        org.slf4j.Logger mockLogger = mock(org.slf4j.Logger.class);
        when(mockLogger.isDebugEnabled()).thenReturn(true);

        Logger logger = new Logger(mockLogger);
        logger.debug("hello, %s", "you");

        verify(mockLogger).debug("hello, you");
    }

    @Test
    public void testInfoFormat()
    {
        org.slf4j.Logger mockLogger = mock(org.slf4j.Logger.class);
        when(mockLogger.isInfoEnabled()).thenReturn(true);

        Logger logger = new Logger(mockLogger);
        logger.info("hello, %s", "you");

        verify(mockLogger).info("hello, you");
    }

    @Test
    public void testWarnFormat()
    {
        org.slf4j.Logger mockLogger = mock(org.slf4j.Logger.class);
        when(mockLogger.isWarnEnabled()).thenReturn(true);

        // message-only version
        Logger logger = new Logger(mockLogger);
        logger.warn("hello, %s", "you");

        verify(mockLogger).warn("hello, you");

        // throwable with message
        @SuppressWarnings({ "ThrowableInstanceNeverThrown" })
        Throwable exception = new Throwable();

        logger.warn(exception, "got exception: %s", "foo");
        verify(mockLogger).warn("got exception: foo", exception);
    }

    @Test
    public void testErrorFormat()
    {
        org.slf4j.Logger mockLogger = mock(org.slf4j.Logger.class);
        when(mockLogger.isErrorEnabled()).thenReturn(true);

        // message-only version
        Logger logger = new Logger(mockLogger);
        logger.error("hello, %s", "you");

        verify(mockLogger).error("hello, you");

        // throwable with message
        @SuppressWarnings({ "ThrowableInstanceNeverThrown" })
        Throwable exception = new Throwable();

        logger.error(exception, "got exception: %s", "foo");
        verify(mockLogger).error("got exception: foo", exception);

        // throwable alone
        @SuppressWarnings({ "ThrowableInstanceNeverThrown" })
        Throwable exception2 = new Throwable("the message");
        logger.error(exception2);
        verify(mockLogger).error(exception2.getMessage(), exception2);
    }


    @Test
    public void testDebugShortCircuit()
    {
        org.slf4j.Logger mockLogger = mock(org.slf4j.Logger.class);

        when(mockLogger.isDebugEnabled()).thenReturn(false);

        Logger logger = new Logger(mockLogger);
        logger.debug("hello");

        verifyNoCalls(mockLogger);
    }

    @Test
    public void testInfoShortCircuit()
    {
        org.slf4j.Logger mockLogger = mock(org.slf4j.Logger.class);

        when(mockLogger.isInfoEnabled()).thenReturn(false);

        Logger logger = new Logger(mockLogger);
        logger.info("hello");

        verifyNoCalls(mockLogger);
    }

    @Test
    public void testWarnShortCircuit()
    {
        org.slf4j.Logger mockLogger = mock(org.slf4j.Logger.class);

        when(mockLogger.isWarnEnabled()).thenReturn(false);

        Logger logger = new Logger(mockLogger);
        logger.warn("hello");

        verifyNoCalls(mockLogger);
    }

    @Test
    public void testWarnWithThrowableShortCircuit()
    {
        org.slf4j.Logger mockLogger = mock(org.slf4j.Logger.class);

        when(mockLogger.isWarnEnabled()).thenReturn(false);

        Logger logger = new Logger(mockLogger);

        @SuppressWarnings({ "ThrowableInstanceNeverThrown" })
        Throwable e = new Throwable();
        logger.warn(e, "hello");

        verifyNoCalls(mockLogger);
    }

    @Test
    public void testErrorShortCircuit()
    {
        org.slf4j.Logger mockLogger = mock(org.slf4j.Logger.class);

        when(mockLogger.isErrorEnabled()).thenReturn(false);

        Logger logger = new Logger(mockLogger);
        logger.warn("hello");

        verifyNoCalls(mockLogger);
    }

    @Test
    public void testErrorWithThrowableShortCircuit()
    {
        org.slf4j.Logger mockLogger = mock(org.slf4j.Logger.class);

        when(mockLogger.isErrorEnabled()).thenReturn(false);

        Logger logger = new Logger(mockLogger);

        @SuppressWarnings({ "ThrowableInstanceNeverThrown" })
        Throwable e = new Throwable();
        logger.error(e, "hello");

        verifyNoCalls(mockLogger);
    }

    @Test
    public void testErrorWithThrowableNoMessageShortCircuit()
    {
        org.slf4j.Logger mockLogger = mock(org.slf4j.Logger.class);

        when(mockLogger.isErrorEnabled()).thenReturn(false);

        Logger logger = new Logger(mockLogger);

        @SuppressWarnings({ "ThrowableInstanceNeverThrown" })
        Throwable e = new Throwable();
        logger.error(e);

        verifyNoCalls(mockLogger);
    }


    private void verifyNoCalls(org.slf4j.Logger mockLogger)
    {
        verify(mockLogger, never()).debug(any(String.class));
        verify(mockLogger, never()).debug(any(String.class), any(Object.class));
        verify(mockLogger, never()).debug(any(String.class), any(Object.class), any(Object.class));
        verify(mockLogger, never()).debug(any(String.class), any(Object[].class));
        verify(mockLogger, never()).debug(any(String.class), any(Throwable.class));
        
        verify(mockLogger, never()).info(any(String.class));
        verify(mockLogger, never()).info(any(String.class), any(Object.class));
        verify(mockLogger, never()).info(any(String.class), any(Object.class), any(Object.class));
        verify(mockLogger, never()).info(any(String.class), any(Object[].class));
        verify(mockLogger, never()).info(any(String.class), any(Throwable.class));

        verify(mockLogger, never()).warn(any(String.class));
        verify(mockLogger, never()).warn(any(String.class), any(Object.class));
        verify(mockLogger, never()).warn(any(String.class), any(Object.class), any(Object.class));
        verify(mockLogger, never()).warn(any(String.class), any(Object[].class));
        verify(mockLogger, never()).warn(any(String.class), any(Throwable.class));

        verify(mockLogger, never()).error(any(String.class));
        verify(mockLogger, never()).error(any(String.class), any(Object.class));
        verify(mockLogger, never()).error(any(String.class), any(Object.class), any(Object.class));
        verify(mockLogger, never()).error(any(String.class), any(Object[].class));
        verify(mockLogger, never()).error(any(String.class), any(Throwable.class));
    }

}
