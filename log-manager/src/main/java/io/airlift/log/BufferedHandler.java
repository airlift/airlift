package io.airlift.log;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;
import com.google.common.util.concurrent.RateLimiter;
import com.google.errorprone.annotations.ThreadSafe;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import jakarta.annotation.Nullable;
import org.weakref.jmx.Managed;

import java.time.Duration;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.logging.ErrorManager.CLOSE_FAILURE;
import static java.util.logging.ErrorManager.FLUSH_FAILURE;
import static java.util.logging.ErrorManager.FORMAT_FAILURE;
import static java.util.logging.ErrorManager.GENERIC_FAILURE;
import static java.util.logging.ErrorManager.WRITE_FAILURE;
import static java.util.stream.Collectors.joining;

@ThreadSafe
class BufferedHandler
        extends Handler
{
    public interface DropSummaryFormatter
    {
        String formatDropSummary(Multiset<String> dropCountBySource);
    }

    private static final MessageAndSource TERMINAL_MESSAGE = new MessageAndSource(new byte[] {}, "");

    private final ExecutorService bufferDrainExecutor = newSingleThreadExecutor(daemonThreadsNamed("log-buffer-drainer"));
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final AtomicBoolean inputClosed = new AtomicBoolean();
    private final AtomicLong droppedMessages = new AtomicLong();

    private final MessageOutput messageOutput;
    private final DropSummaryFormatter dropSummaryFormatter;
    private final RateLimiter errorRetryLimiter;
    private final Duration maxCloseTime;
    private final int messageFlushCount;

    // queueDrainLock ensures that queue and dropCountBySource are kept in sync as data is extracted or moved between these structures
    private final ReentrantLock queueDrainLock = new ReentrantLock();
    private final Condition recordEnqueued = queueDrainLock.newCondition();
    private final Deque<MessageAndSource> queue;
    @GuardedBy("queueDrainLock")
    private final Multiset<String> dropCountBySource = HashMultiset.create();
    @GuardedBy("queueDrainLock")
    private boolean terminalMessageDequeued;

    public BufferedHandler(MessageOutput messageOutput, Formatter formatter, ErrorManager errorManager)
    {
        this(
                messageOutput,
                formatter,
                BufferedHandler::defaultFormatDropSummary,
                errorManager,
                RateLimiter.create(0.5), // Throttle down to 1 retry every 2 seconds
                Duration.ofSeconds(10),
                512,
                1024);
    }

    public BufferedHandler(
            MessageOutput messageOutput,
            Formatter formatter,
            DropSummaryFormatter dropSummaryFormatter,
            ErrorManager errorManager,
            RateLimiter errorRetryLimiter,
            Duration maxCloseTime,
            int messageFlushCount,
            int maxBufferSize)
    {
        this.messageOutput = requireNonNull(messageOutput, "messageOutput is null");
        setFormatter(requireNonNull(formatter, "formatter is null"));
        this.dropSummaryFormatter = requireNonNull(dropSummaryFormatter, "dropSummaryFormatter is null");
        setErrorManager(requireNonNull(errorManager, "errorManager is null"));
        this.errorRetryLimiter = requireNonNull(errorRetryLimiter, "errorRetryLimiter is null");
        this.maxCloseTime = requireNonNull(maxCloseTime, "maxCloseTime is null");
        checkArgument(messageFlushCount > 0, "messageFlushCount must be greater than zero");
        this.messageFlushCount = messageFlushCount;
        checkArgument(maxBufferSize > 0, "maxBufferSize must be greater than zero");
        queue = new LinkedBlockingDeque<>(maxBufferSize);
    }

    private static String defaultFormatDropSummary(Multiset<String> dropCountBySource)
    {
        return dropCountBySource.entrySet().stream()
                .sorted(comparing(Multiset.Entry::getElement))
                .map(entry -> "%s messages dropped: %s".formatted(entry.getElement(), entry.getCount()))
                .collect(joining("\n", "Log buffer dropped messages:\n", ""));
    }

    public void initialize()
    {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        bufferDrainExecutor.execute(this::bufferDrainLoop);
    }

    private void bufferDrainLoop()
    {
        int flushCounter = 0;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Multiset<String> dropSnapshot = ImmutableMultiset.of();
                MessageAndSource message = null;
                try {
                    // Extract work to do
                    queueDrainLock.lock();
                    try {
                        while (true) {
                            if (!dropCountBySource.isEmpty()) {
                                dropSnapshot = ImmutableMultiset.copyOf(dropCountBySource);
                                dropCountBySource.clear();
                            }

                            // Always check if there are any messages (even if we have a drop snapshot to process) to ensure that we
                            // are processing at least some messages during overload conditions, and not just only printing drop summaries.
                            // Note: queuePollFirst() will return null and set terminalMessageDequeued when encountering TERMINAL_MESSAGE.
                            message = queuePollFirst();

                            if (!dropSnapshot.isEmpty() || message != null) {
                                // Work found
                                break;
                            }

                            if (terminalMessageDequeued) {
                                // No more work and terminal message was already located
                                return; // Graceful way to exit the drain loop (other than via interruption)
                            }

                            recordEnqueued.await();
                        }
                    }
                    finally {
                        queueDrainLock.unlock();
                    }
                    // For safety: dropSnapshot and message will be requeued in the finally block if not explicitly unreferenced

                    // Dropped messages occurred before the current, and so drop snapshot need to be written first
                    if (!dropSnapshot.isEmpty()) {
                        if (!writeMessageOutputSafe(formatMessageBytes(createDropSummaryRecord(dropSnapshot)))) {
                            errorRetryLimiter.acquire();
                            continue;
                        }
                        dropSnapshot = ImmutableMultiset.of();
                        flushCounter++;
                    }

                    // Then write the message
                    if (message != null) {
                        if (!writeMessageOutputSafe(message.logMessage())) {
                            errorRetryLimiter.acquire();
                            continue;
                        }
                        message = null;
                        flushCounter++;
                    }

                    // Flush after some number of messages or if there is nothing more to process at the moment
                    if (flushCounter >= messageFlushCount || !hasDrainingWork()) {
                        flushMessageOutputSafe();
                        flushCounter = 0;
                    }
                }
                finally {
                    if (message != null || !dropSnapshot.isEmpty()) {
                        // Return messages that failed
                        queueDrainLock.lock();
                        try {
                            // Return a failed message to the queue, or drop it if at capacity.
                            if (message != null) {
                                // If TERMINAL_MESSAGE has been dequeued, the queue is finished and this message must now become part of the drop summary.
                                // If dropCountBySource is nonempty, then need to drop the current message because subsequent messages have already been dropped (regardless of the current queue size).
                                // If the queue is full, then also need to drop the current message. NOTE: this must be the last condition checked.
                                if (terminalMessageDequeued || !dropCountBySource.isEmpty() || !queue.offerFirst(message)) {
                                    dropCountBySource.add(message.sourceName());
                                    droppedMessages.incrementAndGet();
                                }
                            }

                            // Merge back dropped messages snapshot
                            dropSnapshot.forEachEntry(dropCountBySource::add);
                        }
                        finally {
                            queueDrainLock.unlock();
                        }
                    }
                }
            }
            catch (LogFormatException e) {
                reportError(null, e, FORMAT_FAILURE);
            }
            catch (InterruptedException e) {
                reportError("Log draining thread interrupted, will exit!", e, GENERIC_FAILURE);
                Thread.currentThread().interrupt();
            }
            catch (Exception e) {
                reportError("Unexpected buffer drain loop exception", e, GENERIC_FAILURE);
            }
        }
    }

    /**
     * INVARIANT: Once TERMINAL_MESSAGE has been dequeued, no further records will be dequeued.
     */
    @Nullable
    private MessageAndSource queuePollFirst()
    {
        checkState(queueDrainLock.isLocked());
        if (terminalMessageDequeued) {
            return null;
        }

        MessageAndSource message = queue.pollFirst();

        if (message == TERMINAL_MESSAGE) {
            terminalMessageDequeued = true;
            return null;
        }

        return message;
    }

    private boolean hasDrainingWork()
    {
        queueDrainLock.lock();
        try {
            return !dropCountBySource.isEmpty() || !queue.isEmpty();
        }
        finally {
            queueDrainLock.unlock();
        }
    }

    private LogRecord createDropSummaryRecord(Multiset<String> droppedSnapshot)
    {
        try {
            LogRecord record = new LogRecord(Level.SEVERE, dropSummaryFormatter.formatDropSummary(droppedSnapshot));
            record.setLoggerName(BufferedHandler.class.getName());
            return record;
        }
        catch (Exception e) {
            // Wrap exception with the proper classification
            throw new LogFormatException(e);
        }
    }

    private static class LogFormatException
            extends RuntimeException
    {
        public LogFormatException(Throwable cause)
        {
            super(cause);
        }
    }

    @Override
    public void publish(LogRecord record)
    {
        try {
            if (!isLoggable(record)) {
                return;
            }

            if (inputClosed.get()) {
                return;
            }

            // Generate the message on the publishing thread to ensure we get the correct thread name.
            MessageAndSource message = toMessageAndSource(record);

            // Messages may be inserted after being closed, but they won't be processed if they come after the terminal message
            queueInsert(message);
        }
        catch (LogFormatException e) {
            reportError(null, e, FORMAT_FAILURE);
        }
        catch (Exception e) {
            reportError(null, e, GENERIC_FAILURE);
        }
    }

    private MessageAndSource toMessageAndSource(LogRecord record)
    {
        return new MessageAndSource(formatMessageBytes(record), determineSourceName(record));
    }

    private byte[] formatMessageBytes(LogRecord logRecord)
    {
        try {
            return getFormatter().format(logRecord).getBytes(UTF_8);
        }
        catch (Exception e) {
            // Wrap exception with the proper classification
            throw new LogFormatException(e);
        }
    }

    private static String determineSourceName(LogRecord record)
    {
        return requireNonNullElse(record.getLoggerName(), "UNKNOWN");
    }

    private void queueInsert(MessageAndSource message)
    {
        while (!queue.offerLast(message)) {
            queueDrainLock.lock();
            try {
                MessageAndSource toDrop = queuePollFirst();
                if (toDrop == null) {
                    if (terminalMessageDequeued) {
                        return; // Queue already terminated
                    }
                    continue;
                }
                dropCountBySource.add(toDrop.sourceName());
                droppedMessages.incrementAndGet();
            }
            finally {
                queueDrainLock.unlock();
            }
        }

        // Notify drainer about new message
        queueDrainLock.lock();
        try {
            recordEnqueued.signal();
        }
        finally {
            queueDrainLock.unlock();
        }
    }

    @Override
    public void flush()
    {
        if (inputClosed.get()) {
            return;
        }

        // This technically does not follow traditional buffer flush semantics, but it is not needed for airlift logging uses
        flushMessageOutputSafe();
    }

    @Override
    public void close()
    {
        if (!inputClosed.compareAndSet(false, true)) {
            return;
        }
        queueInsert(TERMINAL_MESSAGE);

        try {
            bufferDrainExecutor.shutdown();
            if (!bufferDrainExecutor.awaitTermination(maxCloseTime.toMillis(), TimeUnit.MILLISECONDS)) {
                reportError("Timed out waiting for data flush during close", null, CLOSE_FAILURE);
            }
        }
        catch (InterruptedException e) {
            reportError("Interrupted awaiting data flush during close", e, CLOSE_FAILURE);
            Thread.currentThread().interrupt();
        }
        finally {
            closeMessageOutputSafe();
        }
    }

    @VisibleForTesting
    boolean isTerminalMessageDequeued()
    {
        queueDrainLock.lock();
        try {
            return terminalMessageDequeued;
        }
        finally {
            queueDrainLock.unlock();
        }
    }

    @VisibleForTesting
    MessageOutput getMessageOutput()
    {
        return messageOutput;
    }

    @Managed
    public long getDroppedMessages()
    {
        return droppedMessages.get();
    }

    private boolean writeMessageOutputSafe(byte[] message)
    {
        try {
            messageOutput.writeMessage(message);
            return true;
        }
        catch (Exception e) {
            reportError("Could not write to the MessageOutput", e, WRITE_FAILURE);
            return false;
        }
    }

    private void flushMessageOutputSafe()
    {
        try {
            messageOutput.flush();
        }
        catch (Exception e) {
            reportError("Could not flush the MessageOutput", e, FLUSH_FAILURE);
        }
    }

    private void closeMessageOutputSafe()
    {
        try {
            messageOutput.close();
        }
        catch (Exception e) {
            reportError("Could not close the MessageOutput", e, CLOSE_FAILURE);
        }
    }

    private record MessageAndSource(byte[] logMessage, String sourceName)
    {
        private MessageAndSource
        {
            requireNonNull(logMessage, "logMessage is null");
            requireNonNull(sourceName, "sourceName is null");
        }
    }
}
