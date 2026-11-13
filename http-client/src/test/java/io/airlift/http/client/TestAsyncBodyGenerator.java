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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateVoidFuture;
import static io.airlift.http.client.AsyncBodyGenerator.asyncBodyGenerator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestAsyncBodyGenerator
{
    @Test
    public void testContentType()
    {
        assertThat(asyncBodyGenerator().contentType()).isEqualTo("application/binary");
        assertThat(asyncBodyGenerator(JSON_UTF_8).contentType()).isEqualTo("application/json; charset=utf-8");
    }

    @Test
    public void testWriteCannotBeCancelled()
    {
        AsyncBodyGenerator body = asyncBodyGenerator();
        RecordingSink sink = new RecordingSink();
        body.attach(sink);

        ListenableFuture<Void> write = body.write(content("a"));
        assertThat(write.cancel(true)).isFalse();
        assertThat(write).isNotDone();

        sink.writes().get(0).set(null);
        assertThat(write).isDone();
        assertThat(write).isNotCancelled();
    }

    @Test
    public void testContentWrittenBeforeAttachIsDeliveredFirst()
    {
        AsyncBodyGenerator body = asyncBodyGenerator();
        ListenableFuture<Void> first = body.write(content("a"));
        ListenableFuture<Void> second = body.write(content("b"));
        assertThat(first).isNotDone();
        assertThat(second).isNotDone();

        RecordingSink sink = new RecordingSink();
        body.attach(sink);
        assertThat(sink.events()).containsExactly("write a", "write b");

        ListenableFuture<Void> third = body.write(content("c"));
        assertThat(sink.events()).containsExactly("write a", "write b", "write c");

        // every write completes with its own content, whether it was written before or after the attach
        IOException failure = new IOException("not sent");
        sink.writes().get(1).setException(failure);
        sink.writes().get(2).set(null);
        assertThat(first).isNotDone();
        assertThatThrownBy(second::get)
                .isInstanceOf(ExecutionException.class)
                .hasCause(failure);
        assertThat(third).succeedsWithin(Duration.ZERO);

        sink.writes().get(0).set(null);
        assertThat(first).succeedsWithin(Duration.ZERO);

        body.close();
        assertThat(sink.events()).containsExactly("write a", "write b", "write c", "close");
    }

    @Test
    public void testCloseBeforeAttach()
    {
        AsyncBodyGenerator body = asyncBodyGenerator();
        body.write(content("a"));
        body.close();

        RecordingSink sink = new RecordingSink();
        body.attach(sink);
        assertThat(sink.events()).containsExactly("write a", "close");
    }

    @Test
    public void testFailBeforeAttach()
    {
        AsyncBodyGenerator body = asyncBodyGenerator();
        ListenableFuture<Void> write = body.write(content("a"));
        IOException failure = new IOException("failed");
        body.fail(failure);

        assertThatThrownBy(write::get)
                .isInstanceOf(ExecutionException.class)
                .hasCause(failure);

        // the request must fail too
        RecordingSink sink = new RecordingSink();
        body.attach(sink);
        assertThat(sink.events()).containsExactly("fail failed");
    }

    @Test
    public void testFailAfterAttach()
    {
        AsyncBodyGenerator body = asyncBodyGenerator();
        RecordingSink sink = new RecordingSink();
        body.attach(sink);
        body.write(content("a"));

        IOException failure = new IOException("failed");
        body.fail(failure);
        assertThat(sink.events()).containsExactly("write a", "fail failed");

        assertThatThrownBy(() -> body.write(content("b")).get())
                .isInstanceOf(ExecutionException.class)
                .hasCause(failure);

        // a failed body stays failed
        body.close();
        body.fail(new IOException("failed again"));
        assertThat(sink.events()).containsExactly("write a", "fail failed");
    }

    @Test
    public void testWriteAfterClose()
    {
        AsyncBodyGenerator body = asyncBodyGenerator();
        RecordingSink sink = new RecordingSink();
        body.attach(sink);
        body.close();

        assertThatThrownBy(() -> body.write(content("a")).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Body is closed");

        body.close();
        assertThat(sink.events()).containsExactly("close");

        // the request may still be in flight, so a closed body can be failed
        body.fail(new IOException("failed"));
        assertThat(sink.events()).containsExactly("close", "fail failed");
    }

    @Test
    public void testFailAfterCloseFailsContentThatWasNotSent()
    {
        AsyncBodyGenerator body = asyncBodyGenerator();
        ListenableFuture<Void> write = body.write(content("a"));
        body.close();

        IOException failure = new IOException("failed");
        body.fail(failure);
        assertThatThrownBy(write::get)
                .isInstanceOf(ExecutionException.class)
                .hasCause(failure);

        RecordingSink sink = new RecordingSink();
        body.attach(sink);
        assertThat(sink.events()).containsExactly("fail failed");
    }

    @Test
    public void testContentAndCloseAddedWhileDelivering()
    {
        AsyncBodyGenerator body = asyncBodyGenerator();
        RecordingSink sink = new RecordingSink()
        {
            @Override
            public ListenableFuture<Void> write(ByteBuffer content)
            {
                ListenableFuture<Void> future = super.write(content);
                if (events().size() == 1) {
                    // added in the middle of a delivery, and still delivered after it, in order
                    body.write(content("b"));
                    body.close();
                    assertThat(events()).containsExactly("write a");
                }
                return future;
            }
        };
        body.attach(sink);

        body.write(content("a"));
        assertThat(sink.events()).containsExactly("write a", "write b", "close");
    }

    @Test
    public void testSinkFailure()
    {
        RuntimeException failure = new IllegalStateException("broken");
        AsyncBodyGenerator body = asyncBodyGenerator();
        ListenableFuture<Void> first = body.write(content("a"));
        ListenableFuture<Void> second = body.write(content("b"));
        ListenableFuture<Void> third = body.write(content("c"));

        RecordingSink sink = new RecordingSink()
        {
            @Override
            public ListenableFuture<Void> write(ByteBuffer content)
            {
                if (events().size() == 1) {
                    throw failure;
                }
                return super.write(content);
            }
        };
        body.attach(sink);

        // content can no longer be delivered in order, so everything after the failed write fails
        assertThat(sink.events()).containsExactly("write a", "fail broken");
        assertThat(first).isNotDone();
        for (ListenableFuture<Void> write : ImmutableList.of(second, third, body.write(content("d")))) {
            assertThatThrownBy(write::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCause(failure);
        }
    }

    @Test
    @Timeout(60)
    public void testContentAcceptedBeforeCloseIsDeliveredBeforeIt()
            throws Exception
    {
        int writers = 4;
        try (ExecutorService executor = newFixedThreadPool(writers + 1)) {
            for (int iteration = 0; iteration < 200; iteration++) {
                AsyncBodyGenerator body = asyncBodyGenerator();
                ClosingSink sink = new ClosingSink();
                body.attach(sink);

                CountDownLatch start = new CountDownLatch(1);
                List<Future<List<ListenableFuture<Void>>>> results = new ArrayList<>();
                for (int writer = 0; writer < writers; writer++) {
                    results.add(executor.submit(() -> {
                        start.await();
                        List<ListenableFuture<Void>> writes = new ArrayList<>();
                        // writes until the body rejects content
                        while (true) {
                            ListenableFuture<Void> write = body.write(content("a"));
                            writes.add(write);
                            if (isRejected(write)) {
                                return writes;
                            }
                        }
                    }));
                }
                Future<?> closer = executor.submit(() -> {
                    start.await();
                    body.close();
                    return null;
                });
                start.countDown();
                closer.get();
                // a writer may still be delivering content of another one, so all of them finish first
                List<ListenableFuture<Void>> writes = new ArrayList<>();
                for (Future<List<ListenableFuture<Void>>> result : results) {
                    writes.addAll(result.get());
                }

                // Every write was either rejected by the body, or delivered before the close.
                // None of them reached the sink after it, where it would have failed.
                int delivered = 0;
                for (ListenableFuture<Void> write : writes) {
                    assertThat(write).isDone();
                    if (!isRejected(write)) {
                        assertThat(write).succeedsWithin(Duration.ZERO);
                        delivered++;
                    }
                }
                assertThat(sink.writesBeforeClose()).isEqualTo(delivered);
                assertThat(sink.writesAfterClose()).isZero();
                assertThat(sink.isClosed()).isTrue();
            }
        }
    }

    private static boolean isRejected(ListenableFuture<Void> write)
            throws InterruptedException
    {
        if (!write.isDone()) {
            return false;
        }
        try {
            write.get();
            return false;
        }
        catch (ExecutionException e) {
            return e.getCause() instanceof IllegalStateException && e.getCause().getMessage().equals("Body is closed");
        }
    }

    @Test
    public void testEmptyContent()
    {
        AsyncBodyGenerator body = asyncBodyGenerator();
        RecordingSink sink = new RecordingSink();
        body.attach(sink);

        assertThat(body.write(ByteBuffer.allocate(0))).succeedsWithin(Duration.ZERO);
        assertThat(sink.events()).isEmpty();
    }

    @Test
    public void testAttachTwice()
    {
        AsyncBodyGenerator body = asyncBodyGenerator();
        body.attach(new RecordingSink());

        assertThatThrownBy(() -> body.attach(new RecordingSink()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Body is already attached to a request");
    }

    private static ByteBuffer content(String value)
    {
        return ByteBuffer.wrap(value.getBytes(UTF_8));
    }

    /**
     * Fails content that arrives after the end of the body, like a connection would.
     */
    private static class ClosingSink
            implements AsyncBodyGenerator.Sink
    {
        private final AtomicInteger writesBeforeClose = new AtomicInteger();
        private final AtomicInteger writesAfterClose = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public ListenableFuture<Void> write(ByteBuffer content)
        {
            if (closed.get()) {
                writesAfterClose.incrementAndGet();
                return immediateFailedFuture(new IOException("Sink is closed"));
            }
            writesBeforeClose.incrementAndGet();
            return immediateVoidFuture();
        }

        @Override
        public void close()
        {
            closed.set(true);
        }

        @Override
        public void fail(Throwable failure)
        {
            throw new UnsupportedOperationException();
        }

        public int writesBeforeClose()
        {
            return writesBeforeClose.get();
        }

        public int writesAfterClose()
        {
            return writesAfterClose.get();
        }

        public boolean isClosed()
        {
            return closed.get();
        }
    }

    private static class RecordingSink
            implements AsyncBodyGenerator.Sink
    {
        private final List<String> events = new ArrayList<>();
        private final List<SettableFuture<Void>> writes = new ArrayList<>();

        @Override
        public ListenableFuture<Void> write(ByteBuffer content)
        {
            events.add("write " + UTF_8.decode(content.duplicate()));
            SettableFuture<Void> future = SettableFuture.create();
            writes.add(future);
            return future;
        }

        @Override
        public void close()
        {
            events.add("close");
        }

        @Override
        public void fail(Throwable failure)
        {
            events.add("fail " + failure.getMessage());
        }

        public List<String> events()
        {
            return events;
        }

        public List<SettableFuture<Void>> writes()
        {
            return writes;
        }
    }
}
