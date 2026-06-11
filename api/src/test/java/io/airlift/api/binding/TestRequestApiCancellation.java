package io.airlift.api.binding;

import io.airlift.api.ApiCancellation;
import io.airlift.http.server.RequestCancellationServletFilter;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

public class TestRequestApiCancellation
{
    @Test
    public void testRequestCancellationAttributeMatchesHttpServer()
    {
        assertThat(ApiCancellationValueParamProvider.REQUEST_CANCELLATION_ATTRIBUTE)
                .isEqualTo(RequestCancellationServletFilter.REQUEST_CANCELLATION_ATTRIBUTE);
    }

    @Test
    public void testCancellationRunsListenersOnce()
    {
        CompletableFuture<Void> cancellationFuture = new CompletableFuture<>();
        RequestApiCancellation cancellation = new RequestApiCancellation(cancellationFuture);
        AtomicInteger listenerCalls = new AtomicInteger();

        cancellation.onCancel(Runnable::run, listenerCalls::incrementAndGet);

        cancellationFuture.cancel(false);
        cancellationFuture.cancel(false);

        assertThat(cancellation.isCancelled()).isTrue();
        assertThat(listenerCalls).hasValue(1);
    }

    @Test
    public void testClosedRegistrationDoesNotRun()
    {
        CompletableFuture<Void> cancellationFuture = new CompletableFuture<>();
        RequestApiCancellation cancellation = new RequestApiCancellation(cancellationFuture);
        AtomicInteger listenerCalls = new AtomicInteger();

        ApiCancellation.ListenerRegistration registration = cancellation.onCancel(Runnable::run, listenerCalls::incrementAndGet);
        registration.close();

        cancellationFuture.cancel(false);

        assertThat(cancellation.isCancelled()).isTrue();
        assertThat(listenerCalls).hasValue(0);
    }

    @Test
    public void testLateRegistrationRunsImmediatelyAfterCancellation()
    {
        CompletableFuture<Void> cancellationFuture = new CompletableFuture<>();
        RequestApiCancellation cancellation = new RequestApiCancellation(cancellationFuture);
        AtomicInteger listenerCalls = new AtomicInteger();

        cancellationFuture.cancel(false);
        cancellation.onCancel(Runnable::run, listenerCalls::incrementAndGet);

        assertThat(listenerCalls).hasValue(1);
    }

    @Test
    public void testCompletionClearsListenersWithoutCancellation()
    {
        CompletableFuture<Void> cancellationFuture = new CompletableFuture<>();
        RequestApiCancellation cancellation = new RequestApiCancellation(cancellationFuture);
        AtomicInteger listenerCalls = new AtomicInteger();

        cancellation.onCancel(Runnable::run, listenerCalls::incrementAndGet);
        cancellationFuture.complete(null);
        cancellationFuture.cancel(false);

        assertThat(cancellation.isCancelled()).isFalse();
        assertThat(listenerCalls).hasValue(0);
    }

    @Test
    public void testCancellationUsesRegisteredExecutor()
    {
        CompletableFuture<Void> cancellationFuture = new CompletableFuture<>();
        RequestApiCancellation cancellation = new RequestApiCancellation(cancellationFuture);
        AtomicReference<Runnable> submittedTask = new AtomicReference<>();
        AtomicInteger listenerCalls = new AtomicInteger();
        Executor executor = submittedTask::set;

        cancellation.onCancel(executor, listenerCalls::incrementAndGet);
        cancellationFuture.cancel(false);

        assertThat(listenerCalls).hasValue(0);
        assertThat(submittedTask).hasValueSatisfying(Runnable::run);
        assertThat(listenerCalls).hasValue(1);
    }

    @Test
    public void testCloseAfterCancellationStartedDoesNotPreventListener()
    {
        CompletableFuture<Void> cancellationFuture = new CompletableFuture<>();
        RequestApiCancellation cancellation = new RequestApiCancellation(cancellationFuture);
        AtomicReference<Runnable> submittedTask = new AtomicReference<>();
        AtomicInteger listenerCalls = new AtomicInteger();
        Executor executor = submittedTask::set;

        ApiCancellation.ListenerRegistration registration = cancellation.onCancel(executor, listenerCalls::incrementAndGet);
        cancellationFuture.cancel(false);
        registration.close();

        assertThat(listenerCalls).hasValue(0);
        assertThat(submittedTask).hasValueSatisfying(Runnable::run);
        assertThat(listenerCalls).hasValue(1);
    }

    @Test
    public void testRejectedExecutorDoesNotEscapeFromCancellation()
    {
        CompletableFuture<Void> cancellationFuture = new CompletableFuture<>();
        RequestApiCancellation cancellation = new RequestApiCancellation(cancellationFuture);
        AtomicInteger listenerCalls = new AtomicInteger();
        Executor rejectingExecutor = _ -> {
            throw new RejectedExecutionException("rejected");
        };

        cancellation.onCancel(rejectingExecutor, listenerCalls::incrementAndGet);

        assertThatNoException().isThrownBy(() -> cancellationFuture.cancel(false));
        assertThat(listenerCalls).hasValue(0);

        assertThatNoException().isThrownBy(() -> cancellation.onCancel(rejectingExecutor, listenerCalls::incrementAndGet));
        assertThat(listenerCalls).hasValue(0);
    }
}
