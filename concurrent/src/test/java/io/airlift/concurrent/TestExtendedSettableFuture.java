package io.airlift.concurrent;

import org.testng.annotations.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class TestExtendedSettableFuture
{
    @Test
    public void testSet()
            throws Exception
    {
        ExtendedSettableFuture<String> future = ExtendedSettableFuture.create();
        future.set("abc");
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertFalse(future.checkWasInterrupted());
        assertEquals(future.get(), "abc");
    }

    @Test
    public void testSetException()
            throws Exception
    {
        ExtendedSettableFuture<String> future = ExtendedSettableFuture.create();
        future.setException(new Exception(""));
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertFalse(future.checkWasInterrupted());
        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    public void testCancelWithoutInterrupt()
            throws Exception
    {
        ExtendedSettableFuture<String> future = ExtendedSettableFuture.create();
        future.cancel(false);
        assertTrue(future.isDone());
        assertTrue(future.isCancelled());
        assertFalse(future.checkWasInterrupted());
        assertThrows(CancellationException.class, future::get);
    }

    @Test
    public void testCancelWithInterrupt()
            throws Exception
    {
        ExtendedSettableFuture<String> future = ExtendedSettableFuture.create();
        future.cancel(true);
        assertTrue(future.isDone());
        assertTrue(future.isCancelled());
        assertTrue(future.checkWasInterrupted());
        assertThrows(CancellationException.class, future::get);
    }

    @Test
    public void testSetAsync()
            throws Exception
    {
        // Test return value
        ExtendedSettableFuture<String> fromFuture = ExtendedSettableFuture.create();
        ExtendedSettableFuture<String> toFuture = ExtendedSettableFuture.create();
        toFuture.setAsync(fromFuture);
        fromFuture.set("abc");
        assertEquals(toFuture.get(), "abc");

        // Test exception
        fromFuture = ExtendedSettableFuture.create();
        toFuture = ExtendedSettableFuture.create();
        toFuture.setAsync(fromFuture);
        fromFuture.setException(new RuntimeException());
        assertThrows(ExecutionException.class, toFuture::get);

        // Test cancellation without interrupt
        fromFuture = ExtendedSettableFuture.create();
        toFuture = ExtendedSettableFuture.create();
        toFuture.setAsync(fromFuture);
        toFuture.cancel(false);
        // Parent Future should receive the cancellation
        assertTrue(fromFuture.isCancelled());
        assertFalse(fromFuture.checkWasInterrupted());

        // Test cancellation with interrupt
        fromFuture = ExtendedSettableFuture.create();
        toFuture = ExtendedSettableFuture.create();
        toFuture.setAsync(fromFuture);
        toFuture.cancel(true);
        // Parent Future should receive the cancellation
        assertTrue(fromFuture.isCancelled());
        assertTrue(fromFuture.checkWasInterrupted());
    }
}
