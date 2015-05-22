package com.proofpoint.concurrent;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.SettableFuture;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static com.proofpoint.concurrent.MoreFutures.failedFuture;
import static com.proofpoint.concurrent.MoreFutures.getFutureValue;
import static com.proofpoint.concurrent.MoreFutures.toCompletableFuture;
import static com.proofpoint.concurrent.MoreFutures.toListenableFuture;
import static com.proofpoint.concurrent.MoreFutures.tryGetFutureValue;
import static com.proofpoint.testing.Assertions.assertInstanceOf;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestMoreFutures
{
    @Test
    public void testFailedFuture()
            throws Exception
    {
        CompletableFuture<Object> future = failedFuture(new SQLException("foo"));

        assertTrue(future.isCompletedExceptionally());

        assertFailure(future::get, (e) -> {
            assertInstanceOf(e, ExecutionException.class);
            assertTrue(e.getCause() instanceof SQLException);
            assertEquals(e.getCause().getMessage(), "foo");
        });
    }

    @Test
    public void testGetFutureValue()
            throws Exception
    {
        assertGetUnchecked(MoreFutures::getFutureValue);
    }

    @Test
    public void testGetFutureValueWithExceptionType()
            throws Exception
    {
        assertGetUnchecked(future -> getFutureValue(future, IOException.class));

        assertFailure(() -> getFutureValue(failedFuture(new SQLException("foo")), SQLException.class), (e) -> {
            assertInstanceOf(e, SQLException.class);
            assertEquals(e.getMessage(), "foo");
        });
    }

    @Test
    public void testTryGetFutureValue()
            throws Exception
    {
        assertGetUnchecked(future -> tryGetFutureValue(future, 100, MILLISECONDS).get());

        assertEquals(tryGetFutureValue(new CompletableFuture<>(), 10, MILLISECONDS), Optional.empty());
    }


    @Test
    public void testTryGetFutureValueWithExceptionType()
            throws Exception
    {
        assertGetUnchecked(future -> tryGetFutureValue(future, 100, MILLISECONDS, IOException.class).get());

        assertEquals(tryGetFutureValue(new CompletableFuture<>(), 10, MILLISECONDS), Optional.empty());

        assertFailure(() -> tryGetFutureValue(failedFuture(new SQLException("foo")), 10, MILLISECONDS, SQLException.class), (e) -> {
            assertInstanceOf(e, SQLException.class);
            assertEquals(e.getMessage(), "foo");
        });
    }

    @Test
    public void testAnyOf()
            throws Exception
    {
        assertGetUnchecked(future -> getFutureValue(MoreFutures.firstCompletedFuture(ImmutableList.of(new CompletableFuture<>(), future, new CompletableFuture<>()))));

        assertFailure(() -> MoreFutures.firstCompletedFuture(null), e -> assertInstanceOf(e, NullPointerException.class));
        assertFailure(() -> MoreFutures.firstCompletedFuture(ImmutableList.of()), e -> assertInstanceOf(e, IllegalArgumentException.class));

        assertEquals(tryGetFutureValue(MoreFutures.firstCompletedFuture(ImmutableList.of(new CompletableFuture<>(), new CompletableFuture<>())), 10, MILLISECONDS),
                Optional.empty());
    }

    @Test
    public void testToFromListenableFuture()
            throws Exception
    {
        assertGetUnchecked(future -> getFutureValue(toCompletableFuture(toListenableFuture(future))));

        SettableFuture<?> settableFuture = SettableFuture.create();
        toCompletableFuture(settableFuture).cancel(true);
        assertTrue(settableFuture.isCancelled());

        CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        toListenableFuture(completableFuture).cancel(true);
        assertTrue(completableFuture.isCancelled());

        assertEquals(tryGetFutureValue(toCompletableFuture(SettableFuture.create()), 10, MILLISECONDS), Optional.empty());
        assertEquals(tryGetFutureValue(toListenableFuture(new CompletableFuture<>()), 10, MILLISECONDS), Optional.empty());
    }

    public static void assertGetUnchecked(UncheckedGetter getter)
            throws Exception
    {
        assertEquals(getter.get(completedFuture("foo")), "foo");

        assertFailure(() -> getter.get(failedFuture(new IllegalArgumentException("foo"))), (e) -> {
            assertInstanceOf(e, IllegalArgumentException.class);
            assertEquals(e.getMessage(), "foo");
        });

        assertFailure(() -> getter.get(failedFuture(new SQLException("foo"))), (e) -> {
            assertInstanceOf(e, RuntimeException.class);
            assertInstanceOf(e.getCause(), SQLException.class);
            assertEquals(e.getCause().getMessage(), "foo");
        });

        Thread.currentThread().interrupt();
        assertFailure(() -> getter.get(new CompletableFuture<>()), (e) -> {
            assertInstanceOf(e, RuntimeException.class);
            assertInstanceOf(e.getCause(), InterruptedException.class);
            assertTrue(Thread.interrupted());
        });
        assertFalse(Thread.currentThread().isInterrupted());

        CompletableFuture<?> canceledFuture = new CompletableFuture<>();
        canceledFuture.cancel(true);
        assertFailure(() -> getter.get(canceledFuture), e -> assertInstanceOf(e, CancellationException.class));
    }

    private static void assertFailure(Thrower thrower, Consumer<Throwable> verifier)
    {
        try {
            thrower.execute();
        }
        catch (Throwable throwable) {
            verifier.accept(throwable);
            return;
        }
        fail("expected exception to be thrown");
    }

    private interface UncheckedGetter
    {
        Object get(CompletableFuture<?> future)
                throws Exception;
    }

    private interface Thrower
    {
        void execute()
                throws Throwable;
    }
}
