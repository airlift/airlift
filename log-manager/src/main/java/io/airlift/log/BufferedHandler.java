package io.airlift.log;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.RateLimiter;
import com.google.common.util.concurrent.SettableFuture;
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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.Futures.nonCancellationPropagating;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.logging.ErrorManager.CLOSE_FAILURE;
import static java.util.logging.ErrorManager.FLUSH_FAILURE;
import static java.util.logging.ErrorManager.FORMAT_FAILURE;
import static java.util.logging.ErrorManager.GENERIC_FAILURE;
import static java.util.logging.ErrorManager.WRITE_FAILURE;
import static java.util.stream.Collectors.joining;

@ThreadSafe
public class BufferedHandler
        extends Handler
{
    public interface DropSummaryFormatter
    {
        String formatDropSummary(Multiset<String> dropCountBySource);
    }

    private static final byte[] EMPTY_BYTES = new byte[0];

    private final MessageAndSource terminalMessage = new MessageAndSource(EMPTY_BYTES, "", SettableFuture.create());
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
    // This field holds any pending flush signal set on the most recently dequeued message
    @Nullable
    @GuardedBy("queueDrainLock")
    private SettableFuture<Void> flushedSignal;

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
                SettableFuture<Void> flushedSignal = null;
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
                            // Note: queuePollFirst() will return null and set terminalMessageDequeued when encountering terminalMessage.
                            message = queuePollFirst();

                            // Move any pending flush signal to the local variable for handling
                            flushedSignal = this.flushedSignal;
                            this.flushedSignal = null;

                            if (!dropSnapshot.isEmpty() || message != null || flushedSignal != null) {
                                // Work found
                                break;
                            }

                            if (terminalMessageDequeued) {
                                // Graceful way to exit the drain loop (other than via interruption). The pending flushedSignal
                                // (which will be set, because terminalMessage embeds a flush signal) will be notified
                                // in the outer finally block
                                return;
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

                    // Flush after some number of messages or if a flush was requested or if there is nothing more to process at the moment
                    if (flushCounter >= messageFlushCount || flushedSignal != null || !hasDrainingWork()) {
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
                                // If terminalMessage has been dequeued, the queue is finished and this message must now become part of the drop summary.
                                // If dropCountBySource is nonempty, then need to drop the current message because subsequent messages have already been dropped (regardless of the current queue size).
                                // If the queue is full, then also need to drop the current message. NOTE: this must be the last condition checked.
                                if (terminalMessageDequeued || !dropCountBySource.isEmpty() || !queue.offerFirst(message)) {
                                    dropCountBySource.add(message.sourceName());
                                    droppedMessages.incrementAndGet();
                                }
                            }

                            // Merge back dropped messages snapshot
                            for (Multiset.Entry<String> entry : dropSnapshot.entrySet()) {
                                dropCountBySource.add(entry.getElement(), entry.getCount());
                            }
                        }
                        finally {
                            queueDrainLock.unlock();
                        }
                    }
                    // Ensure a message output is flushed and notify the initiators, if flush has been requested
                    if (flushedSignal != null) {
                        // Ensure a flush occurs before notifying. When the terminal message is encountered message output may not
                        // yet have been flushed
                        if (flushCounter > 0) {
                            flushMessageOutputSafe();
                            flushCounter = 0;
                        }
                        flushedSignal.set(null);
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
     * INVARIANT: Once {@link BufferedHandler#terminalMessage} has been dequeued, no further records will be dequeued.
     */
    @GuardedBy("queueDrainLock")
    @Nullable
    private MessageAndSource queuePollFirst()
    {
        checkState(queueDrainLock.isHeldByCurrentThread());
        if (terminalMessageDequeued) {
            return null;
        }

        MessageAndSource message = queue.pollFirst();
        if (message == null) {
            return null;
        }

        if (message.flushSignal != null) {
            if (this.flushedSignal == null) {
                this.flushedSignal = message.flushSignal;
            }
            else {
                message.flushSignal.setFuture(this.flushedSignal);
            }
            // terminalMessage always contains a flush signal, but flush signals aren't necessarily from the terminalMessage. If
            // this is the terminalMessage, we need to clear other pending flush signals from the queue and notify all of them
            // after completing the terminal flush
            if (message == terminalMessage) {
                terminalMessageDequeued = true;
                SettableFuture<Void> terminalFlushedSignal = requireNonNull(this.flushedSignal, "flushedSignal must be set");
                queue.removeIf(queuedMessage -> {
                    if (queuedMessage.flushSignal != null) {
                        queuedMessage.flushSignal.setFuture(terminalFlushedSignal);
                        return true;
                    }
                    return false;
                });
            }
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
        return new MessageAndSource(formatMessageBytes(record), determineSourceName(record), null);
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
        return firstNonNull(record.getLoggerName(), "UNKNOWN");
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

    /**
     * Requests a full flush of all currently queued messages from the background thread. If the handler has already been closed,
     * The returned {@link ListenableFuture} will already be completed as no more background log processing can occur. Otherwise,
     * the returned future will complete when the background flush has completed.
     */
    public ListenableFuture<Void> requestFullFlush()
    {
        if (inputClosed.get()) {
            return nonCancellationPropagating(requireNonNull(terminalMessage.flushSignal(), "terminalMessage flush signal must not be null"));
        }

        SettableFuture<Void> flushedSignal = SettableFuture.create();
        queueInsert(new MessageAndSource(EMPTY_BYTES, "", flushedSignal));
        // The terminal message may have already been enqueued, but we need to acquire the lock first to see whether
        // we enqueued our flush signal before or after termination. If the queue is closed, we'll return the terminalMessage
        // flush signal instead since that will complete when the final flush occurs.
        queueDrainLock.lock();
        try {
            if (inputClosed.get() || terminalMessageDequeued) {
                flushedSignal = requireNonNull(terminalMessage.flushSignal(), "terminalMessage flush signal must not be null");
            }
        }
        finally {
            queueDrainLock.unlock();
        }
        // Prevent caller cancellation of this future from interfering with other flush signals
        // that may be linked together via setFuture
        return nonCancellationPropagating(flushedSignal);
    }

    @Override
    public void close()
    {
        if (!inputClosed.compareAndSet(false, true)) {
            return;
        }
        queueInsert(terminalMessage);

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

    private record MessageAndSource(
            byte[] logMessage,
            String sourceName,
            // Only present on a control message that indicates a flush should occur. The logMessage will be discarded if this field is set
            @Nullable SettableFuture<Void> flushSignal)
    {
        private MessageAndSource
        {
            requireNonNull(logMessage, "logMessage is null");
            requireNonNull(sourceName, "sourceName is null");
        }
    }
}
