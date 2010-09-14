package com.proofpoint.log;

import org.testng.annotations.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;

/**
 * TODO: test Throwable variations
 */
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

        Logger logger = new Logger(mockLogger);
        logger.warn("hello, %s", "you");

        verify(mockLogger).warn("hello, you");
    }

    @Test
    public void testErrorFormat()
    {
        org.slf4j.Logger mockLogger = mock(org.slf4j.Logger.class);
        when(mockLogger.isErrorEnabled()).thenReturn(true);

        Logger logger = new Logger(mockLogger);
        logger.error("hello, %s", "you");

        verify(mockLogger).error("hello, you");
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
    public void testErrorShortCircuit()
    {
        org.slf4j.Logger mockLogger = mock(org.slf4j.Logger.class);

        when(mockLogger.isErrorEnabled()).thenReturn(false);

        Logger logger = new Logger(mockLogger);
        logger.warn("hello");

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
