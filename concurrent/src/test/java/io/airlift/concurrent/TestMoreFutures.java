package io.airlift.concurrent;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.airlift.units.Duration;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static io.airlift.concurrent.MoreFutures.addTimeout;
import static io.airlift.concurrent.MoreFutures.allAsList;
import static io.airlift.concurrent.MoreFutures.failedFuture;
import static io.airlift.concurrent.MoreFutures.firstCompletedFuture;
import static io.airlift.concurrent.MoreFutures.getFutureValue;
import static io.airlift.concurrent.MoreFutures.mirror;
import static io.airlift.concurrent.MoreFutures.propagateCancellation;
import static io.airlift.concurrent.MoreFutures.toCompletableFuture;
import static io.airlift.concurrent.MoreFutures.toListenableFuture;
import static io.airlift.concurrent.MoreFutures.tryGetFutureValue;
import static io.airlift.concurrent.MoreFutures.unmodifiableFuture;
import static io.airlift.concurrent.MoreFutures.unwrapCompletionException;
import static io.airlift.concurrent.MoreFutures.whenAnyComplete;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static io.airlift.testing.Assertions.assertInstanceOf;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@SuppressWarnings("deprecation")
public class TestMoreFutures
{
    private final ScheduledExecutorService executorService = newSingleThreadScheduledExecutor(daemonThreadsNamed("test-%s"));

    @AfterClass
    public void tearDown()
    {
        executorService.shutdownNow();
    }

    @Test
    public void propagateCancellationWithoutInterrupt()
            throws Exception
    {
        // Test interrupt override
        ExtendedSettableFuture<Object> fromFuture = ExtendedSettableFuture.create();
        ExtendedSettableFuture<Object> toFuture = ExtendedSettableFuture.create();
        propagateCancellation(fromFuture, toFuture, false);
        fromFuture.cancel(true);
        assertTrue(toFuture.isCancelled());
        assertFalse(toFuture.checkWasInterrupted());

        fromFuture = ExtendedSettableFuture.create();
        toFuture = ExtendedSettableFuture.create();
        propagateCancellation(fromFuture, toFuture, false);
        fromFuture.cancel(false);
        assertTrue(toFuture.isCancelled());
        assertFalse(toFuture.checkWasInterrupted());
    }

    @Test
    public void propagateCancellationWithInterrupt()
            throws Exception
    {
        ExtendedSettableFuture<Object> fromFuture = ExtendedSettableFuture.create();
        ExtendedSettableFuture<Object> toFuture = ExtendedSettableFuture.create();
        propagateCancellation(fromFuture, toFuture, true);
        fromFuture.cancel(true);
        assertTrue(toFuture.isCancelled());
        assertTrue(toFuture.checkWasInterrupted());

        // Test interrupt override
        fromFuture = ExtendedSettableFuture.create();
        toFuture = ExtendedSettableFuture.create();
        propagateCancellation(fromFuture, toFuture, true);
        fromFuture.cancel(false);
        assertTrue(toFuture.isCancelled());
        assertTrue(toFuture.checkWasInterrupted());
    }

    @Test
    public void testMirror()
            throws Exception
    {
        // Test return value
        ExtendedSettableFuture<String> fromFuture = ExtendedSettableFuture.create();
        SettableFuture<String> toFuture = SettableFuture.create();
        mirror(fromFuture, toFuture, true);
        fromFuture.set("abc");
        assertEquals(toFuture.get(), "abc");

        // Test exception
        fromFuture = ExtendedSettableFuture.create();
        toFuture = SettableFuture.create();
        mirror(fromFuture, toFuture, true);
        fromFuture.setException(new RuntimeException());
        assertThrows(ExecutionException.class, toFuture::get);

        // Test cancellation without interrupt
        fromFuture = ExtendedSettableFuture.create();
        toFuture = SettableFuture.create();
        mirror(fromFuture, toFuture, false);
        toFuture.cancel(true);
        // Parent Future should receive the cancellation
        assertTrue(fromFuture.isCancelled());
        assertFalse(fromFuture.checkWasInterrupted());

        // Test cancellation with interrupt
        fromFuture = ExtendedSettableFuture.create();
        toFuture = SettableFuture.create();
        mirror(fromFuture, toFuture, true);
        toFuture.cancel(false);
        // Parent Future should receive the cancellation
        assertTrue(fromFuture.isCancelled());
        assertTrue(fromFuture.checkWasInterrupted());
    }

    @Test
    public void testUnwrapCompletionException()
    {
        RuntimeException original = new RuntimeException();
        assertSame(unwrapCompletionException(original), original);
        assertSame(unwrapCompletionException(new CompletionException(original)), original);

        CompletionException completion = new CompletionException(null);
        assertSame(unwrapCompletionException(completion), completion);
    }

    @Test
    public void testModifyUnmodifiableFuture()
            throws Exception
    {
        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture<String> unmodifiableFuture = unmodifiableFuture(future);

        // completion results in an UnsupportedOperationException
        assertFailure(() -> unmodifiableFuture.complete("fail"), UnsupportedOperationException.class::isInstance);
        assertFalse(future.isDone());
        assertFalse(unmodifiableFuture.isDone());

        assertFailure(() -> unmodifiableFuture.completeExceptionally(new IOException()), UnsupportedOperationException.class::isInstance);
        assertFalse(future.isDone());
        assertFalse(unmodifiableFuture.isDone());

        assertFailure(() -> unmodifiableFuture.obtrudeValue("fail"), UnsupportedOperationException.class::isInstance);
        assertFalse(future.isDone());
        assertFalse(unmodifiableFuture.isDone());

        assertFailure(() -> unmodifiableFuture.obtrudeException(new IOException()), UnsupportedOperationException.class::isInstance);
        assertFalse(future.isDone());
        assertFalse(unmodifiableFuture.isDone());

        // cancel is ignored
        assertFalse(unmodifiableFuture.cancel(false));
        assertFalse(future.isDone());
        assertFalse(unmodifiableFuture.isDone());

        assertFalse(unmodifiableFuture.cancel(true));
        assertFalse(future.isDone());
        assertFalse(unmodifiableFuture.isDone());

        assertFalse(unmodifiableFuture.completeExceptionally(new CancellationException()));
        assertFalse(future.isDone());
        assertFalse(unmodifiableFuture.isDone());
    }

    @Test
    public void testModifyCancelableUnmodifiableFuture()
            throws Exception
    {
        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture<String> unmodifiableFuture = unmodifiableFuture(future, true);

        // completion results in an UnsupportedOperationException
        assertFailure(() -> unmodifiableFuture.complete("fail"), UnsupportedOperationException.class::isInstance);
        assertFalse(future.isDone());
        assertFalse(unmodifiableFuture.isDone());

        assertFailure(() -> unmodifiableFuture.completeExceptionally(new IOException()), UnsupportedOperationException.class::isInstance);
        assertFalse(future.isDone());
        assertFalse(unmodifiableFuture.isDone());

        assertFailure(() -> unmodifiableFuture.obtrudeValue("fail"), UnsupportedOperationException.class::isInstance);
        assertFalse(future.isDone());
        assertFalse(unmodifiableFuture.isDone());

        assertFailure(() -> unmodifiableFuture.obtrudeException(new IOException()), UnsupportedOperationException.class::isInstance);
        assertFalse(future.isDone());
        assertFalse(unmodifiableFuture.isDone());

        // cancel is propagated so test separately
    }

    @Test
    public void testUnmodifiableFutureCancelPropagation()
            throws Exception
    {
        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture<String> unmodifiableFuture = unmodifiableFuture(future, true);
        assertTrue(unmodifiableFuture.cancel(false));
        assertTrue(future.isDone());
        assertTrue(future.isCancelled());
        assertTrue(unmodifiableFuture.isDone());
        assertTrue(unmodifiableFuture.isCancelled());

        future = new CompletableFuture<>();
        unmodifiableFuture = unmodifiableFuture(future, true);
        assertTrue(unmodifiableFuture.cancel(true));
        assertTrue(future.isDone());
        assertTrue(future.isCancelled());
        assertTrue(unmodifiableFuture.isDone());
        assertTrue(unmodifiableFuture.isCancelled());

        future = new CompletableFuture<>();
        unmodifiableFuture = unmodifiableFuture(future, true);
        assertTrue(unmodifiableFuture.completeExceptionally(new CancellationException()));
        assertTrue(future.isDone());
        assertTrue(future.isCancelled());
        assertTrue(unmodifiableFuture.isDone());
        assertTrue(unmodifiableFuture.isCancelled());
    }

    @Test
    public void testCompleteUnmodifiableFuture()
            throws Exception
    {
        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture<String> unmodifiableFuture = unmodifiableFuture(future);

        assertTrue(future.complete("done"));
        assertEquals(future.getNow(null), "done");
        assertTrue(unmodifiableFuture.isDone());
        assertEquals(unmodifiableFuture.getNow(null), "done");
    }

    @Test
    public void testCompleteExceptionallyUnmodifiableFuture()
            throws Exception
    {
        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture<String> unmodifiableFuture = unmodifiableFuture(future);

        assertTrue(future.completeExceptionally(new SQLException("foo")));
        assertFailure(() -> getFutureValue(future, SQLException.class), e -> {
            assertInstanceOf(e, SQLException.class);
            assertEquals(e.getMessage(), "foo");
        });

        assertTrue(unmodifiableFuture.isDone());
        assertFailure(() -> getFutureValue(unmodifiableFuture, SQLException.class), e -> {
            assertInstanceOf(e, SQLException.class);
            assertEquals(e.getMessage(), "foo");
        });
    }

    @Test
    public void testAlreadyCompleteUnmodifiableFuture()
            throws Exception
    {
        CompletableFuture<String> future = completedFuture("done");
        CompletableFuture<String> unmodifiableFuture = unmodifiableFuture(future);

        assertEquals(future.getNow(null), "done");
        assertTrue(unmodifiableFuture.isDone());
        assertEquals(unmodifiableFuture.getNow(null), "done");
    }

    @Test
    public void testAlreadyCompleteExceptionallyUnmodifiableFuture()
            throws Exception
    {
        CompletableFuture<String> future = failedFuture(new SQLException("foo"));
        CompletableFuture<String> unmodifiableFuture = unmodifiableFuture(future);

        assertFailure(() -> getFutureValue(future, SQLException.class), e -> {
            assertInstanceOf(e, SQLException.class);
            assertEquals(e.getMessage(), "foo");
        });

        assertTrue(unmodifiableFuture.isDone());
        assertFailure(() -> getFutureValue(unmodifiableFuture, SQLException.class), e -> {
            assertInstanceOf(e, SQLException.class);
            assertEquals(e.getMessage(), "foo");
        });
    }

    @Test
    public void testFailedFuture()
            throws Exception
    {
        CompletableFuture<Object> future = failedFuture(new SQLException("foo"));

        assertTrue(future.isCompletedExceptionally());

        assertFailure(future::get, e -> {
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

        assertFailure(() -> getFutureValue(failedFuture(new SQLException("foo")), SQLException.class), e -> {
            assertInstanceOf(e, SQLException.class);
            assertEquals(e.getMessage(), "foo");
        });
    }

    @Test
    public void testTryGetFutureValue()
            throws Exception
    {
        assertGetUnchecked(future -> {
            Optional<?> optional = tryGetFutureValue(future);
            if (optional.isPresent()) {
                return optional.get();
            }

            // null value is also absent
            assertNull(getFutureValue(future));
            return null;
        });

        assertEquals(tryGetFutureValue(new CompletableFuture<>()), Optional.empty());
    }

    @Test
    public void testTryGetFutureValueWithWait()
            throws Exception
    {
        assertGetUnchecked(future -> {
            Optional<?> optional = tryGetFutureValue(future, 100, MILLISECONDS);
            if (optional.isPresent()) {
                return optional.get();
            }

            // null value is also absent
            assertNull(getFutureValue(future));
            return null;
        });

        assertEquals(tryGetFutureValue(new CompletableFuture<>(), 10, MILLISECONDS), Optional.empty());
    }

    @Test
    public void testTryGetFutureValueWithExceptionType()
            throws Exception
    {
        assertGetUnchecked(future -> {
            Optional<?> optional = tryGetFutureValue(future, 100, MILLISECONDS, IOException.class);
            if (optional.isPresent()) {
                return optional.get();
            }

            // null value is also absent
            assertNull(getFutureValue(future, IOException.class));
            return null;
        });

        assertEquals(tryGetFutureValue(new CompletableFuture<>(), 10, MILLISECONDS), Optional.empty());

        assertFailure(() -> tryGetFutureValue(failedFuture(new SQLException("foo")), 10, MILLISECONDS, SQLException.class), e -> {
            assertInstanceOf(e, SQLException.class);
            assertEquals(e.getMessage(), "foo");
        });
    }

    @Test
    public void testWhenAnyComplete()
            throws Exception
    {
        assertGetUncheckedListenable(future -> getFutureValue(whenAnyComplete(ImmutableList.of(SettableFuture.create(), future, SettableFuture.create()))));

        assertFailure(() -> whenAnyComplete(null), e -> assertInstanceOf(e, NullPointerException.class));
        assertFailure(() -> whenAnyComplete(ImmutableList.of()), e -> assertInstanceOf(e, IllegalArgumentException.class));

        assertEquals(
                tryGetFutureValue(whenAnyComplete(ImmutableList.of(SettableFuture.create(), SettableFuture.create())), 10, MILLISECONDS),
                Optional.empty());
    }

    @Test
    public void testAnyOf()
            throws Exception
    {
        assertGetUnchecked(future -> getFutureValue(firstCompletedFuture(ImmutableList.of(new CompletableFuture<>(), future, new CompletableFuture<>()))));

        assertFailure(() -> firstCompletedFuture(null), e -> assertInstanceOf(e, NullPointerException.class));
        assertFailure(() -> firstCompletedFuture(ImmutableList.of()), e -> assertInstanceOf(e, IllegalArgumentException.class));

        assertEquals(
                tryGetFutureValue(firstCompletedFuture(ImmutableList.of(new CompletableFuture<>(), new CompletableFuture<>())), 10, MILLISECONDS),
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

    @Test
    public void testEmptyAllAsList()
            throws Exception
    {
        CompletableFuture<List<Object>> future = allAsList(ImmutableList.of());
        assertTrue(future.isDone());
        assertFalse(future.isCompletedExceptionally());
        assertFalse(future.isCancelled());
        assertEquals(future.join(), ImmutableList.of());
    }

    @Test
    public void testSingleElementAllAsList()
            throws Exception
    {
        CompletableFuture<String> element1 = new CompletableFuture<>();

        CompletableFuture<List<Object>> future = allAsList(ImmutableList.of(element1));
        assertFalse(future.isDone());
        assertFalse(future.isCancelled());

        element1.complete("a");
        assertTrue(future.isDone());
        assertFalse(future.isCompletedExceptionally());
        assertFalse(future.isCancelled());
        assertEquals(future.join(), ImmutableList.of("a"));
    }

    @Test
    public void testExceptionalSingleElementAllAsList()
            throws Exception
    {
        CompletableFuture<String> element1 = new CompletableFuture<>();

        CompletableFuture<List<Object>> future = allAsList(ImmutableList.of(element1));
        assertFalse(future.isDone());
        assertFalse(future.isCancelled());

        element1.completeExceptionally(new RuntimeException());
        assertTrue(future.isDone());
        assertTrue(future.isCompletedExceptionally());
        assertFalse(future.isCancelled());
    }

    @Test
    public void testMultipleElementAllAsList()
            throws Exception
    {
        CompletableFuture<String> element1 = new CompletableFuture<>();
        CompletableFuture<String> element2 = new CompletableFuture<>();

        CompletableFuture<List<Object>> future = allAsList(ImmutableList.of(element1, element2));
        assertFalse(future.isDone());
        assertFalse(future.isCancelled());

        element1.complete("a");
        assertFalse(future.isDone());
        assertFalse(future.isCompletedExceptionally());
        assertFalse(future.isCancelled());

        element2.complete("b");
        assertTrue(future.isDone());
        assertFalse(future.isCompletedExceptionally());
        assertFalse(future.isCancelled());
        assertEquals(future.join(), ImmutableList.of("a", "b"));
    }

    @Test
    public void testExceptionalMultipleElementAllAsList()
            throws Exception
    {
        CompletableFuture<String> element1 = new CompletableFuture<>();
        CompletableFuture<String> element2 = new CompletableFuture<>();

        CompletableFuture<List<Object>> future = allAsList(ImmutableList.of(element1, element2));
        assertFalse(future.isDone());
        assertFalse(future.isCompletedExceptionally());
        assertFalse(future.isCancelled());

        element1.completeExceptionally(new RuntimeException());
        assertTrue(future.isDone());
        assertTrue(future.isCompletedExceptionally());
        assertFalse(future.isCancelled());
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableAllAsList()
            throws Exception
    {
        CompletableFuture<List<Object>> future = allAsList(ImmutableList.of(new CompletableFuture<String>()));
        future.complete(null);
    }

    @Test
    public void testListenableTimeout()
            throws Exception
    {
        SettableFuture<String> rootFuture = SettableFuture.create();
        ListenableFuture<String> timeoutFuture = addTimeout(rootFuture, () -> "timeout", new Duration(0, MILLISECONDS), executorService);

        assertEquals(tryGetFutureValue(timeoutFuture, 10, SECONDS).orElse("failed"), "timeout");
        assertTrue(timeoutFuture.isDone());
        assertFalse(timeoutFuture.isCancelled());

        // root exception is cancelled on a timeout
        assertFailure(() -> rootFuture.get(10, SECONDS), e -> assertInstanceOf(e, CancellationException.class));
        assertTrue(rootFuture.isDone());
        assertTrue(rootFuture.isCancelled());
    }

    @Test
    public void testListenableTimeoutExceptionValue()
            throws Exception
    {
        SettableFuture<String> rootFuture = SettableFuture.create();
        ListenableFuture<String> timeoutFuture = addTimeout(rootFuture, () -> { throw new SQLException("timeout"); }, new Duration(0, MILLISECONDS), executorService);

        assertFailure(() -> tryGetFutureValue(timeoutFuture, 10, SECONDS, SQLException.class), e -> {
            assertInstanceOf(e, SQLException.class);
            assertEquals(e.getMessage(), "timeout");
        });
        assertTrue(timeoutFuture.isDone());
        assertFalse(timeoutFuture.isCancelled());

        // root exception is cancelled on a timeout
        assertFailure(() -> rootFuture.get(10, SECONDS), e -> assertInstanceOf(e, CancellationException.class));
        assertTrue(rootFuture.isDone());
        assertTrue(rootFuture.isCancelled());
    }

    @Test
    public void testListenableTimeoutCancel()
            throws Exception
    {
        SettableFuture<String> rootFuture = SettableFuture.create();
        ListenableFuture<String> timeoutFuture = addTimeout(rootFuture, () -> "timeout", new Duration(10, SECONDS), executorService);

        // check timeout
        assertEquals(tryGetFutureValue(timeoutFuture, 10, MILLISECONDS), Optional.<String>empty());

        assertTrue(timeoutFuture.cancel(true));
        assertTrue(timeoutFuture.isDone());
        assertTrue(timeoutFuture.isCancelled());

        // root exception is cancelled on a timeout
        assertFailure(() -> rootFuture.get(10, SECONDS), e -> assertInstanceOf(e, CancellationException.class));
        assertTrue(rootFuture.isDone());
        assertTrue(rootFuture.isCancelled());
    }

    @Test
    public void testTimeout()
            throws Exception
    {
        CompletableFuture<String> rootFuture = new CompletableFuture<>();
        CompletableFuture<String> timeoutFuture = addTimeout(rootFuture, () -> "timeout", new Duration(0, MILLISECONDS), executorService);

        assertEquals(tryGetFutureValue(timeoutFuture, 10, SECONDS).orElse("failed"), "timeout");
        assertTrue(timeoutFuture.isDone());
        assertFalse(timeoutFuture.isCancelled());

        // root exception is cancelled on a timeout
        assertFailure(() -> rootFuture.get(10, SECONDS), e -> assertInstanceOf(e, CancellationException.class));
        assertTrue(rootFuture.isDone());
        assertTrue(rootFuture.isCancelled());
    }

    @Test
    public void testTimeoutExceptionValue()
            throws Exception
    {
        CompletableFuture<String> rootFuture = new CompletableFuture<>();
        CompletableFuture<String> timeoutFuture = addTimeout(rootFuture, () -> { throw new SQLException("timeout"); }, new Duration(0, MILLISECONDS), executorService);

        assertFailure(() -> tryGetFutureValue(timeoutFuture, 10, SECONDS, SQLException.class), e -> {
            assertInstanceOf(e, SQLException.class);
            assertEquals(e.getMessage(), "timeout");
        });
        assertTrue(timeoutFuture.isDone());
        assertFalse(timeoutFuture.isCancelled());

        // root exception is cancelled on a timeout
        assertFailure(() -> rootFuture.get(10, SECONDS), e -> assertInstanceOf(e, CancellationException.class));
        assertTrue(rootFuture.isDone());
        assertTrue(rootFuture.isCancelled());
    }

    @Test
    public void testTimeoutCancel()
            throws Exception
    {
        CompletableFuture<String> rootFuture = new CompletableFuture<>();
        CompletableFuture<String> timeoutFuture = addTimeout(rootFuture, () -> "timeout", new Duration(10, SECONDS), executorService);

        // check timeout
        assertEquals(tryGetFutureValue(timeoutFuture, 10, MILLISECONDS), Optional.<String>empty());

        assertTrue(timeoutFuture.cancel(true));
        assertTrue(timeoutFuture.isDone());
        assertTrue(timeoutFuture.isCancelled());

        // root exception is cancelled on a timeout
        assertFailure(() -> rootFuture.get(10, SECONDS), e -> assertInstanceOf(e, CancellationException.class));
        assertTrue(rootFuture.isDone());
        assertTrue(rootFuture.isCancelled());
    }

    private static void assertGetUncheckedListenable(Function<ListenableFuture<Object>, Object> getter)
            throws Exception
    {
        assertEquals(getter.apply(immediateFuture("foo")), "foo");

        assertFailure(() -> getter.apply(immediateFailedFuture(new IllegalArgumentException("foo"))), e -> {
            assertInstanceOf(e, IllegalArgumentException.class);
            assertEquals(e.getMessage(), "foo");
        });

        assertFailure(() -> getter.apply(immediateFailedFuture(new SQLException("foo"))), e -> {
            assertInstanceOf(e, RuntimeException.class);
            assertInstanceOf(e.getCause(), SQLException.class);
            assertEquals(e.getCause().getMessage(), "foo");
        });

        Thread.currentThread().interrupt();
        assertFailure(() -> getter.apply(SettableFuture.create()), e -> {
            assertInstanceOf(e, RuntimeException.class);
            assertInstanceOf(e.getCause(), InterruptedException.class);
            assertTrue(Thread.interrupted());
        });
        assertFalse(Thread.currentThread().isInterrupted());

        SettableFuture<Object> canceledFuture = SettableFuture.create();
        canceledFuture.cancel(true);
        assertFailure(() -> getter.apply(canceledFuture), e -> assertInstanceOf(e, CancellationException.class));

        assertEquals(getter.apply(immediateFuture(null)), null);
    }

    private void assertGetUnchecked(UncheckedGetter getter)
            throws Exception
    {
        assertGetUncheckedInternal(getter);

        // run all test wrapped in a timeout future that does not timeout
        assertGetUncheckedInternal(future -> getter.get(addTimeout(future, () -> { throw new RuntimeException("timeout"); }, new Duration(10, SECONDS), executorService)));
    }

    private static void assertGetUncheckedInternal(UncheckedGetter getter)
            throws Exception
    {
        assertEquals(getter.get(completedFuture("foo")), "foo");

        assertFailure(() -> getter.get(failedFuture(new IllegalArgumentException("foo"))), e -> {
            assertInstanceOf(e, IllegalArgumentException.class);
            assertEquals(e.getMessage(), "foo");
        });

        assertFailure(() -> getter.get(failedFuture(new SQLException("foo"))), e -> {
            assertInstanceOf(e, RuntimeException.class);
            assertInstanceOf(e.getCause(), SQLException.class);
            assertEquals(e.getCause().getMessage(), "foo");
        });

        Thread.currentThread().interrupt();
        assertFailure(() -> getter.get(new CompletableFuture<>()), e -> {
            assertInstanceOf(e, RuntimeException.class);
            assertInstanceOf(e.getCause(), InterruptedException.class);
            assertTrue(Thread.interrupted());
        });
        assertFalse(Thread.currentThread().isInterrupted());

        CompletableFuture<Object> canceledFuture = new CompletableFuture<>();
        canceledFuture.cancel(true);
        assertFailure(() -> getter.get(canceledFuture), e -> assertInstanceOf(e, CancellationException.class));

        assertEquals(getter.get(completedFuture(null)), null);
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
        Object get(CompletableFuture<Object> future)
                throws Exception;
    }

    private interface Thrower
    {
        void execute()
                throws Throwable;
    }
}
