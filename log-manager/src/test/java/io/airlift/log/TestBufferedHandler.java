package io.airlift.log;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.Multisets.filter;
import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.INFO;
import static org.assertj.core.api.Assertions.assertThat;

public class TestBufferedHandler
{
    @Test
    public void testIdleFlush()
            throws Exception
    {
        TestingMessageOutput testingMessageOutput = new TestingMessageOutput();
        BufferedHandler bufferedHandler = new BufferedHandler(
                testingMessageOutput,
                testingFormatter(),
                TestBufferedHandler::serializeMultiset,
                new ErrorManager(),
                RateLimiter.create(10),
                Duration.ofSeconds(5),
                100,
                100);
        bufferedHandler.initialize();

        LogRecord record = logRecord(INFO, "TestLogger", "Test");
        bufferedHandler.publish(record);
        testingMessageOutput.awaitFirstFlushAttempt(5, TimeUnit.SECONDS);
        assertThat(testingMessageOutput.getFlushedMessages())
                .as("Buffer should flush when idle, even if still less than recordFlushCount")
                .containsExactly(testingFormatter().format(record));

        bufferedHandler.close();
    }

    @Test
    public void testLoggingSequence()
    {
        int maxBufferSize = 100;

        TestingMessageOutput testingMessageOutput = new TestingMessageOutput();
        BufferedHandler bufferedHandler = new BufferedHandler(
                testingMessageOutput,
                testingFormatter(),
                TestBufferedHandler::serializeMultiset,
                new ErrorManager(),
                RateLimiter.create(10),
                Duration.ofSeconds(5),
                100,
                maxBufferSize);
        bufferedHandler.initialize();

        List<LogRecord> logRecords = new ArrayList<>();
        // Add maxBufferSize - 1 log entries (save one space for the terminal signal)
        for (int i = 0; i < maxBufferSize - 1; i++) {
            LogRecord logRecord = logRecord(INFO, "TestLogger", String.valueOf(i));
            logRecords.add(logRecord);
            bufferedHandler.publish(logRecord);
        }
        bufferedHandler.close();

        assertThat(testingMessageOutput.getFlushedMessages())
                .as("Every record should be present if the buffer size is not exceeded")
                .containsExactlyElementsOf(logRecords.stream()
                        .map(record -> testingFormatter().format(record))
                        .collect(toImmutableList()));
    }

    @Test
    public void testLoggingOverloadSingleThread()
    {
        TestingMessageOutput testingMessageOutput = new TestingMessageOutput();
        BufferedHandler bufferedHandler = new BufferedHandler(
                testingMessageOutput,
                testingFormatter(),
                TestBufferedHandler::serializeMultiset,
                new ErrorManager(),
                RateLimiter.create(10),
                Duration.ofSeconds(5),
                2,
                2);
        bufferedHandler.initialize();

        for (int i = 0; i < 1000; i++) {
            // None of these calls should block due to lack of buffer space
            bufferedHandler.publish(logRecord(INFO, "TestLogger", String.valueOf(i)));
        }
        bufferedHandler.close();

        assertThat(bufferedHandler.getDroppedMessages())
                .as("Test control check to make sure that it is dropping some messages")
                .isGreaterThan(0);

        int actualDropCount = assertLogStreamContents(testingMessageOutput.getFlushedMessages(), "TestLogger", 1000, String::valueOf);

        assertThat(actualDropCount).isEqualTo(bufferedHandler.getDroppedMessages());
    }

    @Test
    public void testLoggingOverloadMultiThread()
    {
        TestingMessageOutput testingMessageOutput = new TestingMessageOutput();
        BufferedHandler bufferedHandler = new BufferedHandler(
                testingMessageOutput,
                testingFormatter(),
                TestBufferedHandler::serializeMultiset,
                new ErrorManager(),
                RateLimiter.create(10),
                Duration.ofSeconds(5),
                2,
                2);
        bufferedHandler.initialize();

        ExecutorService executor = Executors.newCachedThreadPool(daemonThreadsNamed("submitter-%s"));
        executor.execute(() -> {
            for (int i = 0; i < 1000; i++) {
                // None of these calls should block due to lack of buffer space
                bufferedHandler.publish(logRecord(INFO, "A-TestLogger", String.valueOf(i)));
            }
        });
        executor.execute(() -> {
            for (int i = 0; i < 1000; i++) {
                // None of these calls should block due to lack of buffer space
                bufferedHandler.publish(logRecord(INFO, "B-TestLogger", String.valueOf(i)));
            }
        });

        assertThat(shutdownAndAwaitTermination(executor, 10, TimeUnit.SECONDS)).isTrue();

        bufferedHandler.close();

        assertThat(bufferedHandler.getDroppedMessages())
                .as("Test control check to make sure that it is dropping some messages")
                .isGreaterThan(0);

        int actualDropCount = assertLogStreamContents(testingMessageOutput.getFlushedMessages(), "A-TestLogger", 1000, String::valueOf);
        actualDropCount += assertLogStreamContents(testingMessageOutput.getFlushedMessages(), "B-TestLogger", 1000, String::valueOf);

        assertThat(actualDropCount).isEqualTo(bufferedHandler.getDroppedMessages());
    }

    @Test
    public void testMultiThreadErrorRetry()
            throws InterruptedException, TimeoutException
    {
        TestingMessageOutput testingMessageOutput = new TestingMessageOutput()
                .setThrowOnWrite(true)
                .setThrowOnFlush(true);
        BufferedHandler bufferedHandler = new BufferedHandler(
                testingMessageOutput,
                testingFormatter(),
                TestBufferedHandler::serializeMultiset,
                new ErrorManager(),
                RateLimiter.create(10),
                Duration.ofSeconds(5),
                50,
                100);
        bufferedHandler.initialize();

        ExecutorService executor = Executors.newCachedThreadPool(daemonThreadsNamed("submitter-%s"));
        executor.execute(() -> {
            for (int i = 0; i < 1000; i++) {
                bufferedHandler.publish(logRecord(INFO, "A-TestLogger", String.valueOf(i)));
            }
        });
        executor.execute(() -> {
            for (int i = 0; i < 1000; i++) {
                bufferedHandler.publish(logRecord(INFO, "B-TestLogger", String.valueOf(i)));
            }
        });

        // Allow the system to recover after it has encountered some errors
        testingMessageOutput.awaitFirstWriteAttempt(5, TimeUnit.SECONDS);
        testingMessageOutput.setThrowOnWrite(false);
        testingMessageOutput.awaitFirstFlushAttempt(5, TimeUnit.SECONDS);
        testingMessageOutput.setThrowOnFlush(false);

        assertThat(shutdownAndAwaitTermination(executor, 10, TimeUnit.SECONDS)).isTrue();

        bufferedHandler.close();

        int actualDropCount = assertLogStreamContents(testingMessageOutput.getFlushedMessages(), "A-TestLogger", 1000, String::valueOf);
        actualDropCount += assertLogStreamContents(testingMessageOutput.getFlushedMessages(), "B-TestLogger", 1000, String::valueOf);

        assertThat(actualDropCount).isEqualTo(bufferedHandler.getDroppedMessages());
    }

    @Test
    public void testCapacityErrorRetryDuringClose()
            throws InterruptedException, TimeoutException, ExecutionException
    {
        TestingMessageOutput testingMessageOutput = new TestingMessageOutput()
                .setThrowOnWrite(true);
        BufferedHandler bufferedHandler = new BufferedHandler(
                testingMessageOutput,
                testingFormatter(),
                TestBufferedHandler::serializeMultiset,
                new ErrorManager(),
                RateLimiter.create(10),
                Duration.ofSeconds(5),
                10,
                1);

        // Publish 2 records before initializing the handler (capacity=1) to force the existence of a drop summary at start
        bufferedHandler.publish(logRecord(INFO, "A-TestLogger", "1"));
        bufferedHandler.publish(logRecord(INFO, "B-TestLogger", "1"));

        bufferedHandler.initialize();

        ExecutorService executor = Executors.newCachedThreadPool(daemonThreadsNamed("submitter-%s"));

        // Throw in some async spam that races with close() and may or may not be processed
        Future<?> spamFutureA = executor.submit(() -> {
            for (int i = 0; i < 100; i++) {
                bufferedHandler.publish(logRecord(INFO, "Spam-TestLogger", String.valueOf(i)));
            }
        });
        Future<?> spamFutureB = executor.submit(() -> {
            for (int i = 0; i < 100; i++) {
                bufferedHandler.publish(logRecord(INFO, "Spam-TestLogger", String.valueOf(i)));
            }
        });

        // Submit the close asynchronously
        Future<?> closeFuture = executor.submit(bufferedHandler::close);

        // Wait for the buffered handler terminal message to be processed, and then for a subsequent write attempt, before allowing writes to proceed
        while (!bufferedHandler.isTerminalMessageDequeued()) {
            sleepUninterruptibly(20, TimeUnit.MILLISECONDS);
        }
        // Wait for the next 2 write attempts to ensure there was at least one attempt with the terminal message signal established
        testingMessageOutput.getNextWriteAttemptLatch().await(5, TimeUnit.SECONDS);
        testingMessageOutput.getNextWriteAttemptLatch().await(5, TimeUnit.SECONDS);
        testingMessageOutput.setThrowOnWrite(false);

        // Wait for the async futures to complete.
        spamFutureA.get(5, TimeUnit.SECONDS);
        spamFutureB.get(5, TimeUnit.SECONDS);
        closeFuture.get(5, TimeUnit.SECONDS);

        // Assert that both messages submitted before close are present (in spite of the errors and capacity churning)
        assertLogStreamContents(testingMessageOutput.getFlushedMessages(), "A-TestLogger", 1, String::valueOf);
        assertLogStreamContents(testingMessageOutput.getFlushedMessages(), "B-TestLogger", 1, String::valueOf);
    }

    @Test
    public void testIgnoreWriteAfterClose()
    {
        TestingMessageOutput testingMessageOutput = new TestingMessageOutput();
        BufferedHandler bufferedHandler = new BufferedHandler(
                testingMessageOutput,
                testingFormatter(),
                TestBufferedHandler::serializeMultiset,
                new ErrorManager(),
                RateLimiter.create(10),
                Duration.ofSeconds(5),
                2,
                2);
        bufferedHandler.initialize();

        LogRecord record = logRecord(INFO, "TestLogger", "Test message");
        bufferedHandler.publish(record);

        bufferedHandler.close();

        bufferedHandler.publish(logRecord(INFO, "TestLogger", "Test message after close"));

        assertThat(bufferedHandler.getDroppedMessages())
                .as("Messages after close are ignored and not counted as dropped")
                .isZero();

        assertThat(testingMessageOutput.getFlushedMessages()).containsExactly(testingFormatter().format(record));
    }

    private static Formatter testingFormatter()
    {
        return new Formatter()
        {
            @Override
            public String format(LogRecord record)
            {
                return new LogEntry(record.getLoggerName(), record.getMessage()).serialize();
            }
        };
    }

    private static LogRecord logRecord(java.util.logging.Level level, String loggerName, String message)
    {
        LogRecord record = new LogRecord(level, message);
        record.setLoggerName(loggerName);
        return record;
    }

    private static int assertLogStreamContents(List<String> actualMessages, String targetLoggerName, int expectedMessageCount, IntFunction<String> indexToExpectedMessage)
    {
        int actualDropCount = 0;

        Iterator<EntryOrDropSummary> iterator = deserializeMessages(actualMessages, targetLoggerName::equals).iterator();
        Multiset<String> currentDropSummary = HashMultiset.create();
        for (int i = 0; i < expectedMessageCount; i++) {
            if (currentDropSummary.isEmpty()) {
                assertThat(iterator)
                        .as("More entries expected in the result")
                        .hasNext();
                EntryOrDropSummary entryOrDropSummary = iterator.next();
                if (entryOrDropSummary.entry() != null) {
                    assertThat(entryOrDropSummary.entry().message())
                            .as("Verify that the message contents match the value sequence")
                            .isEqualTo(indexToExpectedMessage.apply(i));
                    continue;
                }
                currentDropSummary = HashMultiset.create(entryOrDropSummary.dropSummary());
            }
            assertThat(currentDropSummary.remove(targetLoggerName))
                    .as("Expected to contain this logger name next in sequence")
                    .isTrue();
            actualDropCount++;
        }
        assertThat(currentDropSummary)
                .as("Should not have more drop summary entries than total submitted")
                .isEmpty();

        return actualDropCount;
    }

    private static <T> Map<T, Integer> toMap(Multiset<T> multiset)
    {
        return multiset.entrySet().stream()
                .collect(toImmutableMap(Multiset.Entry::getElement, Multiset.Entry::getCount));
    }

    private static String serializeMultiset(Multiset<String> multiset)
    {
        return Joiner.on('\n').withKeyValueSeparator('=').join(toMap(multiset));
    }

    private static Multiset<String> deserializeMultiset(String serializedMultimap)
    {
        Map<String, String> split = Splitter.on('\n').withKeyValueSeparator('=').split(serializedMultimap);
        ImmutableMultiset.Builder<String> builder = ImmutableMultiset.builder();
        for (Map.Entry<String, String> entry : split.entrySet()) {
            builder.addCopies(entry.getKey(), Integer.parseInt(entry.getValue()));
        }
        return builder.build();
    }

    private static List<EntryOrDropSummary> deserializeMessages(List<String> messages, Predicate<String> loggerNameFilter)
    {
        return messages.stream()
                .map(logEntryString -> {
                    LogEntry logEntry = LogEntry.deserialize(logEntryString);
                    return logEntry.loggerName().equals(BufferedHandler.class.getName())
                            ? EntryOrDropSummary.forDropSummary(filter(deserializeMultiset(logEntry.message()), loggerNameFilter))
                            : EntryOrDropSummary.forEntry(logEntry);
                })
                .filter(entry -> entry.entry() != null || !entry.dropSummary().isEmpty()) // Filter out empty summaries (due to applied loggerNameFilter)
                .filter(entry -> entry.dropSummary() != null || loggerNameFilter.apply(entry.entry().loggerName())) // Apply loggerNameFilter to LogEntries
                .collect(toImmutableList());
    }

    private record LogEntry(String loggerName, String message)
    {
        private LogEntry
        {
            requireNonNull(loggerName, "loggerName is null");
            requireNonNull(message, "message is null");
        }

        public String serialize()
        {
            return Joiner.on(':').join(loggerName, message);
        }

        public static LogEntry deserialize(String serialized)
        {
            List<String> splits = Splitter.on(':').splitToList(serialized);
            return new LogEntry(splits.get(0), splits.get(1));
        }
    }

    private record EntryOrDropSummary(@Nullable LogEntry entry, @Nullable Multiset<String> dropSummary)
    {
        private EntryOrDropSummary
        {
            checkArgument((entry == null) != (dropSummary == null), "Exactly one of the values must be non-null");
            dropSummary = dropSummary == null ? null : ImmutableMultiset.copyOf(dropSummary);
        }

        public static EntryOrDropSummary forEntry(LogEntry entry)
        {
            return new EntryOrDropSummary(entry, null);
        }

        public static EntryOrDropSummary forDropSummary(Multiset<String> dropSummary)
        {
            return new EntryOrDropSummary(null, dropSummary);
        }
    }

    private static class TestingMessageOutput
            implements MessageOutput
    {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final Queue<String> writeMessages = new LinkedBlockingQueue<>();
        private final Queue<String> flushedMessages = new LinkedBlockingQueue<>();
        private final BlockingQueue<CountDownLatch> nextWriteAttemptLatches = new LinkedBlockingQueue<>();
        private final CountDownLatch firstWriteAttemptLatch = new CountDownLatch(1);
        private final CountDownLatch firstFlushAttemptLatch = new CountDownLatch(1);
        private final AtomicBoolean throwOnWrite = new AtomicBoolean();
        private final AtomicBoolean throwOnFlush = new AtomicBoolean();

        public TestingMessageOutput setThrowOnWrite(boolean shouldThrow)
        {
            throwOnWrite.set(shouldThrow);
            return this;
        }

        public TestingMessageOutput setThrowOnFlush(boolean shouldThrow)
        {
            throwOnFlush.set(shouldThrow);
            return this;
        }

        public CountDownLatch getNextWriteAttemptLatch()
        {
            CountDownLatch latch = new CountDownLatch(1);
            nextWriteAttemptLatches.add(latch);
            return latch;
        }

        public void awaitFirstWriteAttempt(long timeout, TimeUnit timeUnit)
                throws InterruptedException, TimeoutException
        {
            if (!firstWriteAttemptLatch.await(timeout, timeUnit)) {
                throw new TimeoutException();
            }
        }

        public void awaitFirstFlushAttempt(long timeout, TimeUnit timeUnit)
                throws InterruptedException, TimeoutException
        {
            if (!firstFlushAttemptLatch.await(timeout, timeUnit)) {
                throw new TimeoutException();
            }
        }

        public List<String> getFlushedMessages()
        {
            return ImmutableList.copyOf(flushedMessages);
        }

        private static void signal(BlockingQueue<CountDownLatch> latches)
        {
            List<CountDownLatch> drained = new ArrayList<>();
            latches.drainTo(drained);
            drained.forEach(CountDownLatch::countDown);
        }

        @Override
        public void writeMessage(byte[] message)
        {
            try {
                checkState(!closed.get(), "Already closed");

                if (throwOnWrite.get()) {
                    throw new RuntimeException();
                }

                writeMessages.offer(new String(message, UTF_8));
            }
            finally {
                firstWriteAttemptLatch.countDown();
                signal(nextWriteAttemptLatches);
            }
        }

        @Override
        public void flush()
        {
            try {
                checkState(!closed.get(), "Already closed");

                if (throwOnFlush.get()) {
                    throw new RuntimeException();
                }

                flushInternal();
            }
            finally {
                firstFlushAttemptLatch.countDown();
            }
        }

        @Override
        public void close()
        {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            flushInternal();
        }

        private void flushInternal()
        {
            String message;
            while ((message = writeMessages.poll()) != null) {
                flushedMessages.add(message);
            }
        }
    }
}
