package io.airlift.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

public class TestExtendedSettableFuture {
    @Test
    public void testSet() throws Exception {
        ExtendedSettableFuture<String> future = ExtendedSettableFuture.create();
        future.set("abc");
        assertThat(future).isDone();
        assertThat(future).isNotCancelled();
        assertThat(future.checkWasInterrupted()).isFalse();
        assertThat(future.get()).isEqualTo("abc");
    }

    @Test
    public void testSetException() {
        ExtendedSettableFuture<String> future = ExtendedSettableFuture.create();
        future.setException(new Exception(""));
        assertThat(future).isDone();
        assertThat(future).isNotCancelled();
        assertThat(future.checkWasInterrupted()).isFalse();
        assertThatThrownBy(future::get).isInstanceOf(ExecutionException.class);
    }

    @Test
    public void testCancelWithoutInterrupt() {
        ExtendedSettableFuture<String> future = ExtendedSettableFuture.create();
        future.cancel(false);
        assertThat(future).isDone();
        assertThat(future).isCancelled();
        assertThat(future.checkWasInterrupted()).isFalse();
        assertThatThrownBy(future::get).isInstanceOf(CancellationException.class);
    }

    @Test
    public void testCancelWithInterrupt() {
        ExtendedSettableFuture<String> future = ExtendedSettableFuture.create();
        future.cancel(true);
        assertThat(future).isDone();
        assertThat(future).isCancelled();
        assertThat(future.checkWasInterrupted()).isTrue();
        assertThatThrownBy(future::get).isInstanceOf(CancellationException.class);
    }

    @Test
    public void testSetAsync() throws Exception {
        // Test return value
        ExtendedSettableFuture<String> fromFuture = ExtendedSettableFuture.create();
        ExtendedSettableFuture<String> toFuture = ExtendedSettableFuture.create();
        toFuture.setAsync(fromFuture);
        fromFuture.set("abc");
        assertThat(toFuture.get()).isEqualTo("abc");

        // Test exception
        fromFuture = ExtendedSettableFuture.create();
        toFuture = ExtendedSettableFuture.create();
        toFuture.setAsync(fromFuture);
        fromFuture.setException(new RuntimeException());
        assertThatThrownBy(toFuture::get).isInstanceOf(ExecutionException.class);

        // Test cancellation without interrupt
        fromFuture = ExtendedSettableFuture.create();
        toFuture = ExtendedSettableFuture.create();
        toFuture.setAsync(fromFuture);
        toFuture.cancel(false);
        // Parent Future should receive the cancellation
        assertThat(fromFuture).isCancelled();
        assertThat(fromFuture.checkWasInterrupted()).isFalse();

        // Test cancellation with interrupt
        fromFuture = ExtendedSettableFuture.create();
        toFuture = ExtendedSettableFuture.create();
        toFuture.setAsync(fromFuture);
        toFuture.cancel(true);
        // Parent Future should receive the cancellation
        assertThat(fromFuture).isCancelled();
        assertThat(fromFuture.checkWasInterrupted()).isTrue();
    }
}
