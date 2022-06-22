package io.airlift.log;

import com.google.common.annotations.VisibleForTesting;
import io.airlift.units.Duration;
import org.weakref.jmx.Managed;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.ErrorManager.CLOSE_FAILURE;
import static java.util.logging.ErrorManager.FLUSH_FAILURE;
import static java.util.logging.ErrorManager.FORMAT_FAILURE;
import static java.util.logging.ErrorManager.WRITE_FAILURE;

class BufferedHandler
        extends Handler
{
    private static final Logger log = Logger.get(BufferedHandler.class);
    private static final int MAX_BATCH_COUNT = 1024;
    private static final byte[] POISON_MESSAGE = new byte[0];
    private static final int MIN_WAIT_MILLIS = 100;
    private static final int MAX_WAIT_MILLIS = 360000;
    private static final int MAX_RETRY_BATCH_COUNT = 8192;

    private final MessageOutput messageOutput;
    private final Thread thread;
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> retrySchedule;
    private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(MAX_BATCH_COUNT);
    private BlockingQueue<byte[]> retryQueue = new ArrayBlockingQueue<>(MAX_BATCH_COUNT);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong droppedMessages = new AtomicLong(0);
    private final AtomicLong retryMessages = new AtomicLong(0);
    private AtomicLong initialDelay = new AtomicLong(100);
    private final ExponentialBackOff errorBackOff;

    public BufferedHandler(MessageOutput messageOutput, Formatter formatter, ErrorManager errorManager)
    {
        this.messageOutput = requireNonNull(messageOutput, "messageOutput is null");
        setErrorManager(requireNonNull(errorManager, "errorManager is null"));
        setFormatter(requireNonNull(formatter, "formatter is null"));
        errorBackOff = new ExponentialBackOff(
                new Duration(MIN_WAIT_MILLIS, MILLISECONDS),
                new Duration(MAX_WAIT_MILLIS, MILLISECONDS),
                "Log TCP listener is up",
                "Log TCP listener is down",
                log);

        thread = new Thread(this::logging);
        thread.setName("log-writer");
        thread.setDaemon(true);
    }

    @Override
    public void publish(LogRecord record)
    {
        // if closed messages are dropped
        if (closed.get()) {
            droppedMessages.getAndIncrement();
            return;
        }

        if (!isLoggable(record)) {
            return;
        }

        byte[] message;
        try {
            message = getFormatter().format(record).getBytes(UTF_8);
        }
        catch (Exception e) {
            // catch any exception to assure logging always works
            reportError(null, e, FORMAT_FAILURE);
            return;
        }

        try {
            putUninterruptibly(queue, message);
        }
        catch (Exception e) {
            // catch any exception to assure logging always works
            reportError(null, e, WRITE_FAILURE);
        }

        // if closed while queueing, try to remove the message just to clean up
        if (closed.get()) {
            queue.remove(message);
        }
    }

    @Override
    public synchronized void flush()
    {
        try {
            messageOutput.flush();
        }
        catch (Exception e) {
            reportError(null, e, FLUSH_FAILURE);
        }
    }

    @Override
    public void close()
    {
        closed.set(true);

        putUninterruptibly(queue, POISON_MESSAGE);

        // wait for logging to finish
        try {
            thread.join();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        queue.clear();
    }

    @VisibleForTesting
    MessageOutput getMessageOutput()
    {
        return messageOutput;
    }

    protected void start()
    {
        thread.start();
    }

    private void logging()
    {
        while (!closed.get() || !queue.isEmpty()) {
            processQueue();
        }

        // logging is closed, so close the current output file
        synchronized (messageOutput) {
            try {
                messageOutput.close();
            }
            catch (IOException e) {
                reportError("Could not close the MessageOutput", e, CLOSE_FAILURE);
            }
        }

        queue.clear();
    }

    private void processQueue()
    {
        List<byte[]> batch = new ArrayList<>(MAX_BATCH_COUNT);
        boolean poisonMessageSeen = false;
        while (!closed.get() || !poisonMessageSeen) {
            if (queue.isEmpty()) {
                try {
                    batch.add(queue.take());
                }
                catch (InterruptedException ignored) {
                }
            }
            else {
                queue.drainTo(batch, MAX_BATCH_COUNT);
            }

            int poisonMessageIndex = getPoisonMessageIndex(batch);
            if (poisonMessageIndex >= 0) {
                poisonMessageSeen = true;
                batch = batch.subList(0, poisonMessageIndex);
            }

            logMessageBatch(batch);
            batch.clear();

            if (!retryQueue.isEmpty() && (retrySchedule == null || retrySchedule.isCancelled())) {
                retrySchedule = scheduler.schedule(this::processRetryQueue, initialDelay.get(), MILLISECONDS);
            }
        }
    }

    private void processRetryQueue()
    {
        boolean isSuccess = logRetryMessageBatch();
        if (isSuccess) {
            errorBackOff.success();
            initialDelay = new AtomicLong(100);
            if (retryQueue.size() == 0) {
                retryQueue = new ArrayBlockingQueue<>(MAX_BATCH_COUNT);
            }
        }
        else {
            initialDelay = new AtomicLong(errorBackOff.failed().toMillis());
            log.warn("Processing log records to TCP listener failed. Next retry will happen in %sms", initialDelay);
        }
        retrySchedule.cancel(true);
    }

    private synchronized void logMessageBatch(List<byte[]> batch)
    {
        for (byte[] message : batch) {
            try {
                messageOutput.writeMessage(message);
            }
            catch (Exception e) {
                boolean isSuccess = offerUninterruptibly(retryQueue, message);
                if (!isSuccess && retryQueue.remainingCapacity() == 0 && retryQueue.size() < MAX_RETRY_BATCH_COUNT) {
                    retryQueue = new ArrayBlockingQueue<>(2 * retryQueue.size(), false, retryQueue);
                    isSuccess = offerUninterruptibly(retryQueue, message);
                }
                if (isSuccess) {
                    retryMessages.getAndIncrement();
                }
                else {
                    droppedMessages.getAndIncrement();
                }
                reportError(null, e, WRITE_FAILURE);
            }
        }
        // always flush at the end of a batch, so logs aren't delayed
        flush();
    }

    private synchronized boolean logRetryMessageBatch()
    {
        boolean success = true;
        for (Iterator<byte[]> it = retryQueue.iterator(); it.hasNext(); ) {
            try {
                messageOutput.writeMessage(it.next());
                it.remove();
            }
            catch (Exception e) {
                reportError(null, e, WRITE_FAILURE);
                success = false;
                break;
            }
        }
        // always flush at the end of a batch, so logs aren't delayed
        flush();
        return success;
    }

    private static <T> void putUninterruptibly(BlockingQueue<T> queue, T element)
    {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    queue.put(element);
                    return;
                }
                catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        }
        finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static <T> boolean offerUninterruptibly(BlockingQueue<T> queue, T element)
    {
        boolean isSuccess = false;
        try {
            isSuccess = queue.offer(element, 10, MILLISECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return isSuccess;
    }

    private static int getPoisonMessageIndex(List<byte[]> messages)
    {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) == POISON_MESSAGE) {
                return i;
            }
        }
        return -1;
    }

    @Managed
    public long getDroppedMessages()
    {
        return droppedMessages.get();
    }

    @Managed
    public long getRetryMessages()
    {
        return retryMessages.get();
    }
}
