package io.airlift.concurrent;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ForwardingListenableFuture.SimpleForwardingListenableFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.airlift.units.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static io.airlift.concurrent.MoreFutures.addExceptionCallback;
import static io.airlift.concurrent.MoreFutures.addSuccessCallback;
import static io.airlift.concurrent.MoreFutures.addTimeout;
import static io.airlift.concurrent.MoreFutures.allAsList;
import static io.airlift.concurrent.MoreFutures.allAsListWithCancellationOnFailure;
import static io.airlift.concurrent.MoreFutures.checkSuccess;
import static io.airlift.concurrent.MoreFutures.failedFuture;
import static io.airlift.concurrent.MoreFutures.firstCompletedFuture;
import static io.airlift.concurrent.MoreFutures.getDone;
import static io.airlift.concurrent.MoreFutures.getFutureValue;
import static io.airlift.concurrent.MoreFutures.mirror;
import static io.airlift.concurrent.MoreFutures.propagateCancellation;
import static io.airlift.concurrent.MoreFutures.toCompletableFuture;
import static io.airlift.concurrent.MoreFutures.toListenableFuture;
import static io.airlift.concurrent.MoreFutures.tryGetFutureValue;
import static io.airlift.concurrent.MoreFutures.unmodifiableFuture;
import static io.airlift.concurrent.MoreFutures.unwrapCompletionException;
import static io.airlift.concurrent.MoreFutures.whenAnyComplete;
import static io.airlift.concurrent.MoreFutures.whenAnyCompleteCancelOthers;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@SuppressWarnings("deprecation")
@TestInstance(PER_CLASS)
public class TestMoreFutures
{
    private static final IOException CALLBACK_TEST_FAILURE = new IOException("future failure");

    private final ScheduledExecutorService executorService = newSingleThreadScheduledExecutor(daemonThreadsNamed("test-%s"));

    @AfterAll
    public void tearDown()
    {
        executorService.shutdownNow();
    }

    @Test
    public void propagateCancellationWithoutInterrupt()
    {
        // Test interrupt override
        ExtendedSettableFuture<Object> fromFuture = ExtendedSettableFuture.create();
        ExtendedSettableFuture<Object> toFuture = ExtendedSettableFuture.create();
        propagateCancellation(fromFuture, toFuture, false);
        fromFuture.cancel(true);
        assertThat(toFuture).isCancelled();
        assertThat(toFuture.checkWasInterrupted()).isFalse();

        fromFuture = ExtendedSettableFuture.create();
        toFuture = ExtendedSettableFuture.create();
        propagateCancellation(fromFuture, toFuture, false);
        fromFuture.cancel(false);
        assertThat(toFuture).isCancelled();
        assertThat(toFuture.checkWasInterrupted()).isFalse();
    }

    @Test
    public void propagateCancellationWithInterrupt()
    {
        ExtendedSettableFuture<Object> fromFuture = ExtendedSettableFuture.create();
        ExtendedSettableFuture<Object> toFuture = ExtendedSettableFuture.create();
        propagateCancellation(fromFuture, toFuture, true);
        fromFuture.cancel(true);
        assertThat(toFuture).isCancelled();
        assertThat(toFuture.checkWasInterrupted()).isTrue();

        // Test interrupt override
        fromFuture = ExtendedSettableFuture.create();
        toFuture = ExtendedSettableFuture.create();
        propagateCancellation(fromFuture, toFuture, true);
        fromFuture.cancel(false);
        assertThat(toFuture).isCancelled();
        assertThat(toFuture.checkWasInterrupted()).isTrue();
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
        assertThat(toFuture.get()).isEqualTo("abc");

        // Test exception
        fromFuture = ExtendedSettableFuture.create();
        toFuture = SettableFuture.create();
        mirror(fromFuture, toFuture, true);
        fromFuture.setException(new RuntimeException());
        assertThatThrownBy(toFuture::get)
                .isInstanceOf(ExecutionException.class);

        // Test cancellation without interrupt
        fromFuture = ExtendedSettableFuture.create();
        toFuture = SettableFuture.create();
        mirror(fromFuture, toFuture, false);
        toFuture.cancel(true);
        // Parent Future should receive the cancellation
        assertThat(fromFuture).isCancelled();
        assertThat(fromFuture.checkWasInterrupted()).isFalse();

        // Test cancellation with interrupt
        fromFuture = ExtendedSettableFuture.create();
        toFuture = SettableFuture.create();
        mirror(fromFuture, toFuture, true);
        toFuture.cancel(false);
        // Parent Future should receive the cancellation
        assertThat(fromFuture).isCancelled();
        assertThat(fromFuture.checkWasInterrupted()).isTrue();
    }

    @Test
    public void testUnwrapCompletionException()
    {
        RuntimeException original = new RuntimeException();
        assertThat(unwrapCompletionException(original)).isSameAs(original);
        assertThat(unwrapCompletionException(new CompletionException(original))).isSameAs(original);

        CompletionException completion = new CompletionException(null);
        assertThat(unwrapCompletionException(completion)).isSameAs(completion);
    }

    @Test
    public void testModifyUnmodifiableFuture()
    {
        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture<String> unmodifiableFuture = unmodifiableFuture(future);

        // completion results in an UnsupportedOperationException
        assertFailure(() -> unmodifiableFuture.complete("fail"), e -> assertThat(e).isInstanceOf(UnsupportedOperationException.class));
        assertThat(future.isDone()).isFalse();
        assertThat(unmodifiableFuture.isDone()).isFalse();

        assertFailure(() -> unmodifiableFuture.completeExceptionally(new IOException()), e -> assertThat(e).isInstanceOf(UnsupportedOperationException.class));
        assertThat(future.isDone()).isFalse();
        assertThat(unmodifiableFuture.isDone()).isFalse();

        assertFailure(() -> unmodifiableFuture.obtrudeValue("fail"), e -> assertThat(e).isInstanceOf(UnsupportedOperationException.class));
        assertThat(future.isDone()).isFalse();
        assertThat(unmodifiableFuture.isDone()).isFalse();

        assertFailure(() -> unmodifiableFuture.obtrudeException(new IOException()), e -> assertThat(e).isInstanceOf(UnsupportedOperationException.class));
        assertThat(future.isDone()).isFalse();
        assertThat(unmodifiableFuture.isDone()).isFalse();

        // cancel is ignored
        assertThat(unmodifiableFuture.cancel(false)).isFalse();
        assertThat(future.isDone()).isFalse();
        assertThat(unmodifiableFuture.isDone()).isFalse();

        assertThat(unmodifiableFuture.cancel(true)).isFalse();
        assertThat(future.isDone()).isFalse();
        assertThat(unmodifiableFuture.isDone()).isFalse();

        assertThat(unmodifiableFuture.completeExceptionally(new CancellationException())).isFalse();
        assertThat(future.isDone()).isFalse();
        assertThat(unmodifiableFuture.isDone()).isFalse();
    }

    @Test
    public void testModifyCancelableUnmodifiableFuture()
    {
        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture<String> unmodifiableFuture = unmodifiableFuture(future, true);

        // completion results in an UnsupportedOperationException
        assertFailure(() -> unmodifiableFuture.complete("fail"), e -> assertThat(e).isInstanceOf(UnsupportedOperationException.class));
        assertThat(future.isDone()).isFalse();
        assertThat(unmodifiableFuture.isDone()).isFalse();

        assertFailure(() -> unmodifiableFuture.completeExceptionally(new IOException()), e -> assertThat(e).isInstanceOf(UnsupportedOperationException.class));
        assertThat(future.isDone()).isFalse();
        assertThat(unmodifiableFuture.isDone()).isFalse();

        assertFailure(() -> unmodifiableFuture.obtrudeValue("fail"), e -> assertThat(e).isInstanceOf(UnsupportedOperationException.class));
        assertThat(future.isDone()).isFalse();
        assertThat(unmodifiableFuture.isDone()).isFalse();

        assertFailure(() -> unmodifiableFuture.obtrudeException(new IOException()), e -> assertThat(e).isInstanceOf(UnsupportedOperationException.class));
        assertThat(future.isDone()).isFalse();
        assertThat(unmodifiableFuture.isDone()).isFalse();

        // cancel is propagated so test separately
    }

    @Test
    public void testUnmodifiableFutureCancelPropagation()
    {
        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture<String> unmodifiableFuture = unmodifiableFuture(future, true);
        assertThat(unmodifiableFuture.cancel(false)).isTrue();
        assertThat(future).isDone();
        assertThat(future).isCancelled();
        assertThat(unmodifiableFuture).isDone();
        assertThat(unmodifiableFuture).isCancelled();

        future = new CompletableFuture<>();
        unmodifiableFuture = unmodifiableFuture(future, true);
        assertThat(unmodifiableFuture.cancel(true)).isTrue();
        assertThat(future).isDone();
        assertThat(future).isCancelled();
        assertThat(unmodifiableFuture).isDone();
        assertThat(unmodifiableFuture).isCancelled();

        future = new CompletableFuture<>();
        unmodifiableFuture = unmodifiableFuture(future, true);
        assertThat(unmodifiableFuture.completeExceptionally(new CancellationException())).isTrue();
        assertThat(future).isDone();
        assertThat(future).isCancelled();
        assertThat(unmodifiableFuture).isDone();
        assertThat(unmodifiableFuture).isCancelled();
    }

    @Test
    public void testCompleteUnmodifiableFuture()
    {
        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture<String> unmodifiableFuture = unmodifiableFuture(future);

        assertThat(future.complete("done")).isTrue();
        assertThat(future.getNow(null)).isEqualTo("done");
        assertThat(unmodifiableFuture).isDone();
        assertThat(unmodifiableFuture.getNow(null)).isEqualTo("done");
    }

    @Test
    public void testCompleteExceptionallyUnmodifiableFuture()
    {
        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture<String> unmodifiableFuture = unmodifiableFuture(future);

        assertThat(future.completeExceptionally(new SQLException("foo"))).isTrue();
        assertFailure(() -> getFutureValue(future, SQLException.class), e -> {
            assertThat(e)
                    .isInstanceOf(SQLException.class)
                    .hasMessage("foo");
        });

        assertThat(unmodifiableFuture).isDone();
        assertFailure(() -> getFutureValue(unmodifiableFuture, SQLException.class), e -> {
            assertThat(e)
                    .isInstanceOf(SQLException.class)
                    .hasMessage("foo");
        });
    }

    @Test
    public void testAlreadyCompleteUnmodifiableFuture()
    {
        CompletableFuture<String> future = completedFuture("done");
        CompletableFuture<String> unmodifiableFuture = unmodifiableFuture(future);

        assertThat(future.getNow(null)).isEqualTo("done");
        assertThat(unmodifiableFuture).isDone();
        assertThat(unmodifiableFuture.getNow(null)).isEqualTo("done");
    }

    @Test
    public void testAlreadyCompleteExceptionallyUnmodifiableFuture()
    {
        CompletableFuture<String> future = failedFuture(new SQLException("foo"));
        CompletableFuture<String> unmodifiableFuture = unmodifiableFuture(future);

        assertFailure(() -> getFutureValue(future, SQLException.class), e -> {
            assertThat(e)
                    .isInstanceOf(SQLException.class)
                    .hasMessage("foo");
        });

        assertThat(unmodifiableFuture).isDone();
        assertFailure(() -> getFutureValue(unmodifiableFuture, SQLException.class), e -> {
            assertThat(e)
                    .isInstanceOf(SQLException.class)
                    .hasMessage("foo");
        });
    }

    @Test
    public void testFailedFuture()
    {
        CompletableFuture<Object> future = failedFuture(new SQLException("foo"));

        assertThat(future).isCompletedExceptionally();

        assertFailure(future::get, e -> {
            assertThat(e)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(SQLException.class)
                    .hasRootCauseMessage("foo");
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
            assertThat(e)
                    .isInstanceOf(SQLException.class)
                    .hasMessage("foo");
        });
    }

    @Test
    public void testTryGetFutureValue()
            throws Exception
    {
        assertGetUnchecked(future -> {
            Optional<?> optional = tryGetFutureValue(future);
            if (optional.isPresent()) {
                return optional.orElseThrow();
            }

            // null value is also absent
            assertThat(getFutureValue(future)).isNull();
            return null;
        });

        assertThat(tryGetFutureValue(new CompletableFuture<>())).isEqualTo(Optional.empty());
    }

    @Test
    public void testTryGetFutureValueWithWait()
            throws Exception
    {
        assertGetUnchecked(future -> {
            Optional<?> optional = tryGetFutureValue(future, 100, MILLISECONDS);
            if (optional.isPresent()) {
                return optional.orElseThrow();
            }

            // null value is also absent
            assertThat(getFutureValue(future)).isNull();
            return null;
        });

        assertThat(tryGetFutureValue(new CompletableFuture<>(), 10, MILLISECONDS)).isEqualTo(Optional.empty());
    }

    @Test
    public void testTryGetFutureValueWithExceptionType()
            throws Exception
    {
        assertGetUnchecked(future -> {
            Optional<?> optional = tryGetFutureValue(future, 100, MILLISECONDS, IOException.class);
            if (optional.isPresent()) {
                return optional.orElseThrow();
            }

            // null value is also absent
            assertThat(getFutureValue(future, IOException.class)).isNull();
            return null;
        });

        assertThat(tryGetFutureValue(new CompletableFuture<>(), 10, MILLISECONDS)).isEqualTo(Optional.empty());

        assertFailure(() -> tryGetFutureValue(failedFuture(new SQLException("foo")), 10, MILLISECONDS, SQLException.class), e -> {
            assertThat(e)
                    .isInstanceOf(SQLException.class)
                    .hasMessage("foo");
        });
    }

    @Test
    public void testWhenAnyComplete()
            throws Exception
    {
        assertGetUncheckedListenable(future -> getFutureValue(whenAnyComplete(ImmutableList.of(SettableFuture.create(), future, SettableFuture.create()))));

        assertFailure(() -> whenAnyComplete(null), e -> assertThat(e).isInstanceOf(NullPointerException.class));
        assertFailure(() -> whenAnyComplete(ImmutableList.of()), e -> assertThat(e).isInstanceOf(IllegalArgumentException.class));

        assertThat(tryGetFutureValue(whenAnyComplete(ImmutableList.of(SettableFuture.create(), SettableFuture.create())), 10, MILLISECONDS)).isEqualTo(Optional.empty());
    }

    @Test
    public void testWhenAnyCompleteCancelOthers()
            throws Exception
    {
        assertGetUncheckedListenable(future -> {
            SettableFuture<Object> future1 = SettableFuture.create();
            SettableFuture<Object> future3 = SettableFuture.create();
            Object result = getFutureValue(whenAnyCompleteCancelOthers(ImmutableList.of(future1, future, future3)));
            assertThat(future1).isCancelled();
            assertThat(future3).isCancelled();
            return result;
        });

        assertFailure(() -> whenAnyComplete(null), e -> assertThat(e).isInstanceOf(NullPointerException.class));
        assertFailure(() -> whenAnyComplete(ImmutableList.of()), e -> assertThat(e).isInstanceOf(IllegalArgumentException.class));

        assertThat(tryGetFutureValue(whenAnyComplete(ImmutableList.of(SettableFuture.create(), SettableFuture.create())), 10, MILLISECONDS)).isEqualTo(Optional.empty());
    }

    @Test
    public void testAnyOf()
            throws Exception
    {
        assertGetUnchecked(future -> getFutureValue(firstCompletedFuture(ImmutableList.of(new CompletableFuture<>(), future, new CompletableFuture<>()))));

        assertFailure(() -> firstCompletedFuture(null), e -> assertThat(e).isInstanceOf(NullPointerException.class));
        assertFailure(() -> firstCompletedFuture(ImmutableList.of()), e -> assertThat(e).isInstanceOf(IllegalArgumentException.class));

        assertThat(tryGetFutureValue(firstCompletedFuture(ImmutableList.of(new CompletableFuture<>(), new CompletableFuture<>())), 10, MILLISECONDS)).isEqualTo(Optional.empty());
    }

    @Test
    public void testToFromListenableFuture()
            throws Exception
    {
        assertGetUnchecked(future -> getFutureValue(toCompletableFuture(toListenableFuture(future))));

        SettableFuture<?> settableFuture = SettableFuture.create();
        toCompletableFuture(settableFuture).cancel(true);
        assertThat(settableFuture).isCancelled();

        CompletableFuture<Object> completableFuture = new CompletableFuture<>();
        toListenableFuture(completableFuture).cancel(true);
        assertThat(completableFuture).isCancelled();

        assertThat(tryGetFutureValue(toCompletableFuture(SettableFuture.create()), 10, MILLISECONDS)).isEqualTo(Optional.empty());
        assertThat(tryGetFutureValue(toListenableFuture(new CompletableFuture<>()), 10, MILLISECONDS)).isEqualTo(Optional.empty());
    }

    @Test
    public void testEmptyAllAsList()
    {
        CompletableFuture<List<Object>> future = allAsList(ImmutableList.of());
        assertThat(future).isDone();
        assertThat(future).isNotCompletedExceptionally();
        assertThat(future).isNotCancelled();
        assertThat(future.join()).isEqualTo(ImmutableList.of());
    }

    @Test
    public void testSingleElementAllAsList()
    {
        CompletableFuture<String> element1 = new CompletableFuture<>();

        CompletableFuture<List<Object>> future = allAsList(ImmutableList.of(element1));
        assertThat(future.isDone()).isFalse();
        assertThat(future.isCancelled()).isFalse();

        element1.complete("a");
        assertThat(future).isDone();
        assertThat(future).isNotCompletedExceptionally();
        assertThat(future).isNotCancelled();
        assertThat(future.join()).isEqualTo(ImmutableList.of("a"));
    }

    @Test
    public void testExceptionalSingleElementAllAsList()
    {
        CompletableFuture<String> element1 = new CompletableFuture<>();

        CompletableFuture<List<Object>> future = allAsList(ImmutableList.of(element1));
        assertThat(future.isDone()).isFalse();
        assertThat(future.isCancelled()).isFalse();

        element1.completeExceptionally(new RuntimeException());
        assertThat(future).isDone();
        assertThat(future).isCompletedExceptionally();
        assertThat(future).isNotCancelled();
    }

    @Test
    public void testMultipleElementAllAsList()
    {
        CompletableFuture<String> element1 = new CompletableFuture<>();
        CompletableFuture<String> element2 = new CompletableFuture<>();

        CompletableFuture<List<Object>> future = allAsList(ImmutableList.of(element1, element2));
        assertThat(future.isDone()).isFalse();
        assertThat(future.isCancelled()).isFalse();

        element1.complete("a");
        assertThat(future.isDone()).isFalse();
        assertThat(future.isCompletedExceptionally()).isFalse();
        assertThat(future.isCancelled()).isFalse();

        element2.complete("b");
        assertThat(future).isDone();
        assertThat(future).isNotCompletedExceptionally();
        assertThat(future).isNotCancelled();
        assertThat(future.join()).isEqualTo(ImmutableList.of("a", "b"));
    }

    @Test
    public void testExceptionalMultipleElementAllAsList()
    {
        CompletableFuture<String> element1 = new CompletableFuture<>();
        CompletableFuture<String> element2 = new CompletableFuture<>();

        CompletableFuture<List<Object>> future = allAsList(ImmutableList.of(element1, element2));
        assertThat(future).isNotDone();
        assertThat(future).isNotCompletedExceptionally();
        assertThat(future).isNotCancelled();

        element1.completeExceptionally(new RuntimeException());
        assertThat(future).isDone();
        assertThat(future).isCompletedExceptionally();
        assertThat(future).isNotCancelled();
    }

    @Test
    public void testUnmodifiableAllAsList()
    {
        CompletableFuture<List<Object>> future = allAsList(ImmutableList.of(new CompletableFuture<String>()));
        assertThatThrownBy(() -> future.complete(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testListenableTimeout()
    {
        SettableFuture<String> rootFuture = SettableFuture.create();
        ListenableFuture<String> timeoutFuture = addTimeout(rootFuture, () -> "timeout", new Duration(0, MILLISECONDS), executorService);

        assertThat(tryGetFutureValue(timeoutFuture, 10, SECONDS).orElse("failed")).isEqualTo("timeout");
        assertThat(timeoutFuture).isDone();
        assertThat(timeoutFuture).isNotCancelled();

        // root exception is cancelled on a timeout
        assertFailure(() -> rootFuture.get(10, SECONDS), e -> assertThat(e).isInstanceOf(CancellationException.class));
        assertThat(rootFuture).isDone();
        assertThat(rootFuture).isCancelled();
    }

    @Test
    public void testListenableTimeoutExceptionValue()
    {
        SettableFuture<String> rootFuture = SettableFuture.create();
        ListenableFuture<String> timeoutFuture = addTimeout(rootFuture, () -> { throw new SQLException("timeout"); }, new Duration(0, MILLISECONDS), executorService);

        assertFailure(() -> tryGetFutureValue(timeoutFuture, 10, SECONDS, SQLException.class), e -> {
            assertThat(e)
                    .isInstanceOf(SQLException.class)
                    .hasMessage("timeout");
        });
        assertThat(timeoutFuture).isDone();
        assertThat(timeoutFuture).isNotCancelled();

        // root exception is cancelled on a timeout
        assertFailure(() -> rootFuture.get(10, SECONDS), e -> assertThat(e).isInstanceOf(CancellationException.class));
        assertThat(rootFuture).isDone();
        assertThat(rootFuture).isCancelled();
    }

    @Test
    public void testListenableTimeoutCancel()
    {
        SettableFuture<String> rootFuture = SettableFuture.create();
        ListenableFuture<String> timeoutFuture = addTimeout(rootFuture, () -> "timeout", new Duration(10, SECONDS), executorService);

        // check timeout
        assertThat(tryGetFutureValue(timeoutFuture, 10, MILLISECONDS)).isEqualTo(Optional.<String>empty());

        assertThat(timeoutFuture.cancel(true)).isTrue();
        assertThat(timeoutFuture).isDone();
        assertThat(timeoutFuture).isCancelled();

        // root exception is cancelled on a timeout
        assertFailure(() -> rootFuture.get(10, SECONDS), e -> assertThat(e).isInstanceOf(CancellationException.class));
        assertThat(rootFuture).isDone();
        assertThat(rootFuture).isCancelled();
    }

    @Test
    public void testTimeout()
    {
        CompletableFuture<String> rootFuture = new CompletableFuture<>();
        CompletableFuture<String> timeoutFuture = addTimeout(rootFuture, () -> "timeout", new Duration(0, MILLISECONDS), executorService);

        assertThat(tryGetFutureValue(timeoutFuture, 10, SECONDS).orElse("failed")).isEqualTo("timeout");
        assertThat(timeoutFuture).isDone();
        assertThat(timeoutFuture).isNotCancelled();

        // root exception is cancelled on a timeout
        assertFailure(() -> rootFuture.get(10, SECONDS), e -> assertThat(e).isInstanceOf(CancellationException.class));
        assertThat(rootFuture).isDone();
        assertThat(rootFuture).isCancelled();
    }

    @Test
    public void testTimeoutExceptionValue()
    {
        CompletableFuture<String> rootFuture = new CompletableFuture<>();
        CompletableFuture<String> timeoutFuture = addTimeout(rootFuture, () -> { throw new SQLException("timeout"); }, new Duration(0, MILLISECONDS), executorService);

        assertFailure(() -> tryGetFutureValue(timeoutFuture, 10, SECONDS, SQLException.class), e -> {
            assertThat(e)
                    .isInstanceOf(SQLException.class)
                    .hasMessage("timeout");
        });
        assertThat(timeoutFuture).isDone();
        assertThat(timeoutFuture).isNotCancelled();

        // root exception is cancelled on a timeout
        assertFailure(() -> rootFuture.get(10, SECONDS), e -> assertThat(e).isInstanceOf(CancellationException.class));
        assertThat(rootFuture).isDone();
        assertThat(rootFuture).isCancelled();
    }

    @Test
    public void testTimeoutCancel()
    {
        CompletableFuture<String> rootFuture = new CompletableFuture<>();
        CompletableFuture<String> timeoutFuture = addTimeout(rootFuture, () -> "timeout", new Duration(10, SECONDS), executorService);

        // check timeout
        assertThat(tryGetFutureValue(timeoutFuture, 10, MILLISECONDS)).isEqualTo(Optional.<String>empty());

        assertThat(timeoutFuture.cancel(true)).isTrue();
        assertThat(timeoutFuture).isDone();
        assertThat(timeoutFuture).isCancelled();

        // root exception is cancelled on a timeout
        assertFailure(() -> rootFuture.get(10, SECONDS), e -> assertThat(e).isInstanceOf(CancellationException.class));
        assertThat(rootFuture).isDone();
        assertThat(rootFuture).isCancelled();
    }

    @Test
    public void testGetDone()
    {
        assertThat(getDone(immediateFuture("Alice"))).isEqualTo("Alice");

        assertFailure(() -> getDone(immediateFailedFuture(new IllegalStateException("some failure"))), expect(IllegalStateException.class, "some failure"));

        assertFailure(
                () -> getDone(immediateFailedFuture(new IOException("some failure"))),
                expect(RuntimeException.class, "java.io.IOException: some failure", expect(IOException.class, "some failure")));

        assertFailure(() -> getDone(SettableFuture.create()), expect(IllegalArgumentException.class, "future not done yet"));

        assertFailure(() -> getDone(null), expect(NullPointerException.class, "future is null"));
    }

    @Test
    public void testCheckSuccess()
    {
        checkSuccess(immediateFuture("Alice"), "this should not fail");

        assertFailure(
                () -> checkSuccess(immediateFailedFuture(new IllegalStateException("some failure")), "msg"),
                expect(IllegalArgumentException.class,
                        "msg",
                        expect(IllegalStateException.class, "some failure")));

        assertFailure(
                () -> checkSuccess(immediateFailedFuture(new IOException("some failure")), "msg"),
                expect(
                        IllegalArgumentException.class,
                        "msg",
                        expect(RuntimeException.class,
                                "java.io.IOException: some failure",
                                expect(IOException.class, "some failure"))));

        assertFailure(() -> checkSuccess(SettableFuture.create(), "msg"), expect(IllegalArgumentException.class, "future not done yet"));

        assertFailure(() -> checkSuccess(null, "msg"), expect(NullPointerException.class, "future is null"));
    }

    @Test
    public void testAllAsListWithCancellationOnFailure()
    {
        SettableFuture<Void> future1 = SettableFuture.create();
        SettableFuture<Void> future2 = SettableFuture.create();
        ListenableFuture<List<Void>> listFuture = allAsListWithCancellationOnFailure(ImmutableList.of(future1, future2));
        assertThat(listFuture).isNotDone();
        future1.setException(new RuntimeException("future failure"));
        assertThat(listFuture)
                .failsWithin(0, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .withMessageContaining("future failure");
        assertThat(future2).isCancelled();
    }

    @Test
    public void testAddSuccessCallbackWithConsumer()
    {
        assertCallback(
                (future, invocations, executor) -> addSuccessCallback(future, value -> invocations.add(value), executor),
                invocations -> assertThat(invocations).containsExactly("value"),
                invocations -> assertThat(invocations).isEmpty(),
                invocations -> assertThat(invocations).isEmpty());
    }

    @Test
    public void testAddSuccessCallbackWithRunnable()
    {
        assertCallback(
                (future, invocations, executor) -> addSuccessCallback(future, () -> invocations.add("called"), executor),
                invocations -> assertThat(invocations).containsExactly("called"),
                invocations -> assertThat(invocations).isEmpty(),
                invocations -> assertThat(invocations).isEmpty());
    }

    @Test
    public void testAddExceptionCallbackWithConsumer()
    {
        assertCallback(
                (future, invocations, executor) -> addExceptionCallback(future, throwable -> invocations.add(throwable), executor),
                invocations -> assertThat(invocations).isEmpty(),
                invocations -> assertThat(invocations).containsExactly(CALLBACK_TEST_FAILURE),
                invocations -> assertThat(invocations).singleElement().isInstanceOf(CancellationException.class));
    }

    @Test
    public void testAddExceptionCallbackWithRunnable()
    {
        assertCallback(
                (future, invocations, executor) -> addExceptionCallback(future, () -> invocations.add("called"), executor),
                invocations -> assertThat(invocations).isEmpty(),
                invocations -> assertThat(invocations).containsExactly("called"),
                invocations -> assertThat(invocations).containsExactly("called"));
    }

    @Test
    public void testCallbacksWithDirectExecutor()
    {
        // the overloads without an executor run the callback on the thread completing the future
        for (CallbackCompletion completion : CallbackCompletion.values()) {
            SettableFuture<String> future = SettableFuture.create();
            List<Object> invocations = new ArrayList<>();
            addSuccessCallback(future, value -> invocations.add("success consumer " + value));
            addSuccessCallback(future, () -> invocations.add("success runnable"));
            addExceptionCallback(future, throwable -> invocations.add("exception consumer " + throwable.getClass().getSimpleName()));
            addExceptionCallback(future, () -> invocations.add("exception runnable"));
            assertThat(invocations).isEmpty();

            completion.complete(future);
            List<Object> expected = switch (completion) {
                case SUCCESS -> ImmutableList.of("success consumer value", "success runnable");
                case FAILURE -> ImmutableList.of("exception consumer IOException", "exception runnable");
                case CANCEL, CANCEL_WITH_INTERRUPT -> ImmutableList.of("exception consumer CancellationException", "exception runnable");
            };
            assertThat(invocations).isEqualTo(expected);

            // or on the thread registering the callback when the future is already done
            invocations.clear();
            addSuccessCallback(future, value -> invocations.add("success consumer " + value));
            addSuccessCallback(future, () -> invocations.add("success runnable"));
            addExceptionCallback(future, throwable -> invocations.add("exception consumer " + throwable.getClass().getSimpleName()));
            addExceptionCallback(future, () -> invocations.add("exception runnable"));
            assertThat(invocations).isEqualTo(expected);
        }
    }

    @Test
    public void testCallbacksRunInRegistrationOrder()
    {
        for (CallbackCompletion completion : CallbackCompletion.values()) {
            SettableFuture<String> future = SettableFuture.create();
            List<Object> invocations = new ArrayList<>();
            future.addListener(() -> invocations.add("first"), directExecutor());
            addSuccessCallback(future, () -> invocations.add("success"));
            addExceptionCallback(future, () -> invocations.add("exception"));
            future.addListener(() -> invocations.add("second"), directExecutor());
            addExceptionCallback(future, () -> invocations.add("exception"));
            addSuccessCallback(future, () -> invocations.add("success"));
            future.addListener(() -> invocations.add("last"), directExecutor());

            completion.complete(future);
            String expected = completion == CallbackCompletion.SUCCESS ? "success" : "exception";
            assertThat(invocations).containsExactly("first", expected, "second", expected, "last");
        }
    }

    @Test
    public void testFailingCallbackWithDirectExecutor()
    {
        RuntimeException callbackFailure = new RuntimeException("callback failure");
        List<CallbackRegistration> registrations = ImmutableList.of(
                (future, invocations, _) -> addSuccessCallback(future, _ -> {
                    invocations.add("called");
                    throw callbackFailure;
                }),
                (future, invocations, _) -> addSuccessCallback(future, () -> {
                    invocations.add("called");
                    throw callbackFailure;
                }),
                (future, invocations, _) -> addExceptionCallback(future, _ -> {
                    invocations.add("called");
                    throw callbackFailure;
                }),
                (future, invocations, _) -> addExceptionCallback(future, () -> {
                    invocations.add("called");
                    throw callbackFailure;
                }));

        for (CallbackRegistration registration : registrations) {
            for (CallbackCompletion completion : CallbackCompletion.values()) {
                // the failure reaches neither the thread completing the future nor the listeners registered after the callback
                SettableFuture<String> future = SettableFuture.create();
                List<Object> invocations = new ArrayList<>();
                List<Object> listenerInvocations = new ArrayList<>();
                registration.register(future, invocations, directExecutor());
                future.addListener(() -> listenerInvocations.add("called"), directExecutor());
                assertThat(completion.complete(future)).isTrue();
                assertThat(listenerInvocations).containsExactly("called");

                // nor the thread registering the callback when the future is already done
                List<Object> doneInvocations = new ArrayList<>();
                registration.register(future, doneInvocations, directExecutor());
                assertThat(doneInvocations).isEqualTo(invocations);
            }
        }
    }

    @Test
    public void testFailingCallbackWithExecutor()
    {
        // the failure is thrown from the task handed to the executor
        RuntimeException callbackFailure = new RuntimeException("callback failure");
        for (boolean alreadyDone : new boolean[] {false, true}) {
            assertThatThrownBy(completeAndGetExecutor((future, _, executor) -> addSuccessCallback(future, _ -> { throw callbackFailure; }, executor), CallbackCompletion.SUCCESS, alreadyDone, false)::runAll)
                    .isSameAs(callbackFailure);
            assertThatThrownBy(completeAndGetExecutor((future, _, executor) -> addSuccessCallback(future, () -> { throw callbackFailure; }, executor), CallbackCompletion.SUCCESS, alreadyDone, false)::runAll)
                    .isSameAs(callbackFailure);
            for (CallbackCompletion completion : ImmutableList.of(CallbackCompletion.FAILURE, CallbackCompletion.CANCEL, CallbackCompletion.CANCEL_WITH_INTERRUPT)) {
                assertThatThrownBy(completeAndGetExecutor((future, _, executor) -> addExceptionCallback(future, _ -> { throw callbackFailure; }, executor), completion, alreadyDone, false)::runAll)
                        .isSameAs(callbackFailure);
                assertThatThrownBy(completeAndGetExecutor((future, _, executor) -> addExceptionCallback(future, () -> { throw callbackFailure; }, executor), completion, alreadyDone, false)::runAll)
                        .isSameAs(callbackFailure);
            }
        }
    }

    @Test
    public void testCallbacksWithRejectingExecutor()
    {
        Executor rejectingExecutor = _ -> {
            throw new RejectedExecutionException("rejected");
        };
        for (CallbackCompletion completion : CallbackCompletion.values()) {
            SettableFuture<String> future = SettableFuture.create();
            List<Object> invocations = new ArrayList<>();
            addSuccessCallback(future, value -> invocations.add(value), rejectingExecutor);
            addSuccessCallback(future, () -> invocations.add("called"), rejectingExecutor);
            addExceptionCallback(future, throwable -> invocations.add(throwable), rejectingExecutor);
            addExceptionCallback(future, () -> invocations.add("called"), rejectingExecutor);
            future.addListener(() -> invocations.add("listener"), directExecutor());

            // the rejection reaches neither the thread completing the future nor the other listeners
            assertThat(completion.complete(future)).isTrue();
            assertThat(invocations).containsExactly("listener");
        }
    }

    @Test
    public void testCallbacksRejectNullArguments()
    {
        SettableFuture<String> future = SettableFuture.create();
        assertThatThrownBy(() -> addSuccessCallback(null, _ -> {}, directExecutor())).isInstanceOf(NullPointerException.class).hasMessage("future is null");
        assertThatThrownBy(() -> addSuccessCallback(null, () -> {}, directExecutor())).isInstanceOf(NullPointerException.class).hasMessage("future is null");
        assertThatThrownBy(() -> addExceptionCallback(null, _ -> {}, directExecutor())).isInstanceOf(NullPointerException.class).hasMessage("future is null");
        assertThatThrownBy(() -> addExceptionCallback(null, () -> {}, directExecutor())).isInstanceOf(NullPointerException.class).hasMessage("future is null");

        assertThatThrownBy(() -> addSuccessCallback(future, (Consumer<String>) null, directExecutor())).isInstanceOf(NullPointerException.class).hasMessage("successCallback is null");
        assertThatThrownBy(() -> addSuccessCallback(future, (Runnable) null, directExecutor())).isInstanceOf(NullPointerException.class).hasMessage("successCallback is null");
        assertThatThrownBy(() -> addExceptionCallback(future, (Consumer<Throwable>) null, directExecutor())).isInstanceOf(NullPointerException.class).hasMessage("exceptionCallback is null");
        assertThatThrownBy(() -> addExceptionCallback(future, (Runnable) null, directExecutor())).isInstanceOf(NullPointerException.class).hasMessage("exceptionCallback is null");

        // a missing executor is reported on registration, not when the future completes
        assertThatThrownBy(() -> addSuccessCallback(future, _ -> {}, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> addSuccessCallback(future, () -> {}, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> addExceptionCallback(future, _ -> {}, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> addExceptionCallback(future, () -> {}, null)).isInstanceOf(NullPointerException.class);
    }

    private static void assertCallback(
            CallbackRegistration registration,
            Consumer<List<Object>> successVerifier,
            Consumer<List<Object>> failureVerifier,
            Consumer<List<Object>> cancellationVerifier)
    {
        // a forwarding future takes Guava's slow path, as it does not give access to its failure
        for (boolean forwarding : new boolean[] {false, true}) {
            for (boolean alreadyDone : new boolean[] {false, true}) {
                for (CallbackCompletion completion : CallbackCompletion.values()) {
                    List<Object> invocations = new ArrayList<>();
                    QueuingExecutor executor = completeAndGetExecutor(registration, invocations, completion, alreadyDone, forwarding);
                    // the callback runs on the executor, not on the thread registering it or completing the future
                    assertThat(invocations).isEmpty();
                    executor.runAll();
                    Consumer<List<Object>> verifier = switch (completion) {
                        case SUCCESS -> successVerifier;
                        case FAILURE -> failureVerifier;
                        case CANCEL, CANCEL_WITH_INTERRUPT -> cancellationVerifier;
                    };
                    verifier.accept(invocations);
                }
            }
        }
    }

    private static QueuingExecutor completeAndGetExecutor(CallbackRegistration registration, CallbackCompletion completion, boolean alreadyDone, boolean forwarding)
    {
        return completeAndGetExecutor(registration, new ArrayList<>(), completion, alreadyDone, forwarding);
    }

    private static QueuingExecutor completeAndGetExecutor(CallbackRegistration registration, List<Object> invocations, CallbackCompletion completion, boolean alreadyDone, boolean forwarding)
    {
        SettableFuture<String> future = SettableFuture.create();
        QueuingExecutor executor = new QueuingExecutor();
        if (alreadyDone) {
            completion.complete(future);
        }
        registration.register(forwarding ? new SimpleForwardingListenableFuture<>(future) {} : future, invocations, executor);
        if (!alreadyDone) {
            completion.complete(future);
        }
        return executor;
    }

    private static void assertGetUncheckedListenable(Function<ListenableFuture<Object>, Object> getter)
    {
        assertThat(getter.apply(immediateFuture("foo"))).isEqualTo("foo");

        assertFailure(() -> getter.apply(immediateFailedFuture(new IllegalArgumentException("foo"))), e -> {
            assertThat(e)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("foo");
        });

        assertFailure(() -> getter.apply(immediateFailedFuture(new SQLException("foo"))), e -> {
            assertThat(e)
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(SQLException.class)
                    .hasRootCauseMessage("foo");
        });

        Thread.currentThread().interrupt();
        assertFailure(() -> getter.apply(SettableFuture.create()), e -> {
            assertThat(e)
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.interrupted()).isTrue();
        });
        assertThat(Thread.currentThread().isInterrupted()).isFalse();

        SettableFuture<Object> canceledFuture = SettableFuture.create();
        canceledFuture.cancel(true);
        assertFailure(() -> getter.apply(canceledFuture), e -> assertThat(e).isInstanceOf(CancellationException.class));

        assertThat(getter.apply(immediateFuture(null))).isEqualTo(null);
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
        assertThat(getter.get(completedFuture("foo"))).isEqualTo("foo");

        assertFailure(() -> getter.get(failedFuture(new IllegalArgumentException("foo"))), e -> {
            assertThat(e)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("foo");
        });

        assertFailure(() -> getter.get(failedFuture(new SQLException("foo"))), e -> {
            assertThat(e)
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(SQLException.class)
                    .hasRootCauseMessage("foo");
        });

        Thread.currentThread().interrupt();
        assertFailure(() -> getter.get(new CompletableFuture<>()), e -> {
            assertThat(e)
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.interrupted()).isTrue();
        });
        assertThat(Thread.currentThread().isInterrupted()).isFalse();

        CompletableFuture<Object> canceledFuture = new CompletableFuture<>();
        canceledFuture.cancel(true);
        assertFailure(() -> getter.get(canceledFuture), e -> assertThat(e).isInstanceOf(CancellationException.class));

        assertThat(getter.get(completedFuture(null))).isEqualTo(null);
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

    private static Consumer<Throwable> expect(Class<? extends Throwable> expectedClass, String expectedMessagePattern)
    {
        return expect(expectedClass, expectedMessagePattern, _ -> {});
    }

    private static Consumer<Throwable> expect(Class<? extends Throwable> expectedClass, String expectedMessagePattern, Consumer<? super Throwable> causeVerifier)
    {
        return e -> {
            assertThat(e).as("exception is null").isNotNull();
            if (!expectedClass.isInstance(e) || !nullToEmpty(e.getMessage()).matches(expectedMessagePattern)) {
                fail("Expected %s with message '%s', got: %s".formatted(expectedClass, expectedMessagePattern, e), e);
            }
            causeVerifier.accept(e.getCause());
        };
    }

    private interface CallbackRegistration
    {
        void register(ListenableFuture<String> future, List<Object> invocations, Executor executor);
    }

    private enum CallbackCompletion
    {
        SUCCESS {
            @Override
            boolean complete(SettableFuture<String> future)
            {
                return future.set("value");
            }
        },
        FAILURE {
            @Override
            boolean complete(SettableFuture<String> future)
            {
                return future.setException(CALLBACK_TEST_FAILURE);
            }
        },
        CANCEL {
            @Override
            boolean complete(SettableFuture<String> future)
            {
                return future.cancel(false);
            }
        },
        CANCEL_WITH_INTERRUPT {
            @Override
            boolean complete(SettableFuture<String> future)
            {
                return future.cancel(true);
            }
        };

        abstract boolean complete(SettableFuture<String> future);
    }

    private static class QueuingExecutor
            implements Executor
    {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task)
        {
            tasks.add(task);
        }

        void runAll()
        {
            while (!tasks.isEmpty()) {
                tasks.remove().run();
            }
        }
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
