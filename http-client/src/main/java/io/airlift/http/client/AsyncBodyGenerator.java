/*
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
package io.airlift.http.client;

import com.google.common.collect.ImmutableList;
import com.google.common.net.MediaType;
import com.google.common.util.concurrent.ForwardingListenableFuture.SimpleForwardingListenableFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.errorprone.annotations.ThreadSafe;
import com.google.errorprone.annotations.concurrent.GuardedBy;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.net.MediaType.APPLICATION_BINARY;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateVoidFuture;
import static java.util.Objects.requireNonNull;

/**
 * A request body that the caller writes while the request is in flight. The body stays open until it is
 * {@linkplain #close() closed} or {@linkplain #fail(Throwable) failed}, so a single request can carry content
 * that is produced over a long time, without a thread that waits for the next piece of it.
 * <p>
 * Content written before the request is executed is kept and sent first. A generator serves one request.
 * <p>
 * The body is taken over by the request when it is about to be sent. A request that is rejected before that,
 * for example because a {@link HttpRequestFilter} throws or replaces the body, leaves the body as it was, and
 * the caller {@linkplain #fail(Throwable) fails} or {@linkplain #close() closes} it.
 * <p>
 * A request with an open body is subject to the request and idle timeouts like any other request. A body
 * that stays open for long needs a request timeout that covers it, set with
 * {@link Request.Builder#setRequestTimeout}, and either content written at least once per idle timeout or
 * a longer idle timeout, set with {@link Request.Builder#setIdleTimeout}. The server usually responds only
 * after the body is closed, so such a request is executed with {@link HttpClient#executeAsync}.
 */
@ThreadSafe
public final class AsyncBodyGenerator
        implements BodyGenerator, Closeable
{
    private final String contentType;

    // content that was accepted, and not handed to the sink yet
    @GuardedBy("this")
    private final Queue<PendingWrite> pendingWrites = new ArrayDeque<>();
    @GuardedBy("this")
    private Sink sink;
    // a thread is handing content to the sink, and also takes what other threads add in the meantime
    @GuardedBy("this")
    private boolean delivering;
    @GuardedBy("this")
    private boolean closed;
    @GuardedBy("this")
    private boolean closeDelivered;
    @GuardedBy("this")
    private Throwable failure;
    @GuardedBy("this")
    private boolean failureDelivered;

    public static AsyncBodyGenerator asyncBodyGenerator()
    {
        return new AsyncBodyGenerator(APPLICATION_BINARY);
    }

    public static AsyncBodyGenerator asyncBodyGenerator(MediaType contentType)
    {
        return new AsyncBodyGenerator(contentType);
    }

    private AsyncBodyGenerator(MediaType contentType)
    {
        this.contentType = requireNonNull(contentType, "contentType is null").toString();
    }

    public String contentType()
    {
        return contentType;
    }

    /**
     * Adds content to the body, without blocking. Content is sent in the order it was accepted,
     * and content accepted before the body is closed is sent before the body ends.
     * <p>
     * The returned future completes when the content was sent, and fails when the body or the request failed
     * before that. A caller that produces content faster than the connection takes it can wait for the future
     * to bound the content that is queued. The content must not be read or modified until the future
     * completes, and its position is undefined afterwards.
     * <p>
     * The future cannot be cancelled, because the content cannot be withdrawn once it is accepted.
     */
    public ListenableFuture<Void> write(ByteBuffer content)
    {
        requireNonNull(content, "content is null");

        PendingWrite pendingWrite;
        synchronized (this) {
            if (failure != null) {
                return immediateFailedFuture(failure);
            }
            if (closed) {
                return immediateFailedFuture(new IllegalStateException("Body is closed"));
            }
            if (!content.hasRemaining()) {
                return immediateVoidFuture();
            }
            pendingWrite = new PendingWrite(content, SettableFuture.create());
            pendingWrites.add(pendingWrite);
        }
        deliver();
        return new NonCancellableFuture(pendingWrite.future());
    }

    /**
     * Ends the body. Content written before is still sent.
     */
    @Override
    public void close()
    {
        synchronized (this) {
            if (closed || failure != null) {
                return;
            }
            closed = true;
        }
        deliver();
    }

    /**
     * Fails the body, which fails the content that was not sent yet, and the request unless its response
     * was already received. A closed body can still be failed, also after all of its content was sent.
     * Does nothing when the body is already failed.
     */
    public void fail(Throwable failure)
    {
        requireNonNull(failure, "failure is null");

        List<PendingWrite> failedWrites;
        synchronized (this) {
            if (this.failure != null) {
                return;
            }
            this.failure = failure;
            failedWrites = ImmutableList.copyOf(pendingWrites);
            pendingWrites.clear();
        }
        for (PendingWrite failedWrite : failedWrites) {
            failedWrite.future().setException(failure);
        }
        deliver();
    }

    /**
     * Starts delivering the body to the sink. Meant for {@link HttpClient} implementations that send the request,
     * and for code that handles requests in place of a server in tests; other callers would take the content
     * away from the request.
     * Content written so far is delivered first, followed by the end or the failure of the body if it
     * already happened.
     */
    public void attach(Sink sink)
    {
        requireNonNull(sink, "sink is null");
        synchronized (this) {
            checkState(this.sink == null, "Body is already attached to a request");
            this.sink = sink;
        }
        deliver();
    }

    /**
     * Hands what is pending to the sink, in the order it was accepted. One thread does this at a time,
     * and it also takes what other threads add in the meantime. The sink therefore sees a single
     * sequence of calls, and none of them is made while holding the lock.
     */
    private void deliver()
    {
        Sink sink;
        synchronized (this) {
            if (this.sink == null || delivering) {
                return;
            }
            delivering = true;
            sink = this.sink;
        }

        while (true) {
            PendingWrite pendingWrite;
            Throwable failure = null;
            synchronized (this) {
                pendingWrite = pendingWrites.poll();
                if (pendingWrite == null) {
                    if (this.failure != null && !failureDelivered) {
                        failureDelivered = true;
                        failure = this.failure;
                    }
                    else if (closed && !closeDelivered && this.failure == null) {
                        closeDelivered = true;
                    }
                    else {
                        delivering = false;
                        return;
                    }
                }
            }

            if (failure != null) {
                sink.fail(failure);
                continue;
            }
            try {
                if (pendingWrite != null) {
                    pendingWrite.future().setFuture(sink.write(pendingWrite.content()));
                }
                else {
                    sink.close();
                }
            }
            catch (RuntimeException e) {
                // content can no longer be delivered in order, so the body fails
                if (pendingWrite != null) {
                    pendingWrite.future().setException(e);
                }
                fail(e);
            }
        }
    }

    /**
     * Receives the body. Implemented only by {@link HttpClient} implementations, and by code that
     * handles requests in place of a server in tests.
     * <p>
     * The methods are called one at a time, content in the order it was written. A sink that
     * throws fails the body.
     */
    public interface Sink
    {
        /**
         * Accepts content without blocking. The returned future completes when the content was sent,
         * and fails when it cannot be sent.
         */
        ListenableFuture<Void> write(ByteBuffer content);

        /**
         * Ends the body after the content accepted so far.
         */
        void close();

        /**
         * Fails the content that was not sent yet, and the request. It is the last call, and can follow
         * {@link #close()} for as long as the request is in flight.
         */
        void fail(Throwable failure);
    }

    // A cancelled future would report the write as done while the content is still queued or being sent
    private static class NonCancellableFuture
            extends SimpleForwardingListenableFuture<Void>
    {
        private NonCancellableFuture(ListenableFuture<Void> delegate)
        {
            super(delegate);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning)
        {
            return false;
        }
    }

    private record PendingWrite(ByteBuffer content, SettableFuture<Void> future)
    {
        private PendingWrite
        {
            requireNonNull(content, "content is null");
            requireNonNull(future, "future is null");
        }
    }
}
