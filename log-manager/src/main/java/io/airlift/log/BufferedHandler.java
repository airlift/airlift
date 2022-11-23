package io.airlift.log;

import com.google.common.annotations.VisibleForTesting;
import org.weakref.jmx.Managed;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.logging.ErrorManager.CLOSE_FAILURE;
import static java.util.logging.ErrorManager.FLUSH_FAILURE;
import static java.util.logging.ErrorManager.FORMAT_FAILURE;
import static java.util.logging.ErrorManager.WRITE_FAILURE;

class BufferedHandler
        extends Handler
{
    private static final int MAX_BATCH_COUNT = 1024;
    private static final byte[] POISON_MESSAGE = new byte[0];

    private final MessageOutput messageOutput;
    private final Thread thread;
    private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(MAX_BATCH_COUNT);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong droppedMessages = new AtomicLong(0);

    public BufferedHandler(MessageOutput messageOutput, Formatter formatter, ErrorManager errorManager)
    {
        this.messageOutput = requireNonNull(messageOutput, "messageOutput is null");
        setErrorManager(requireNonNull(errorManager, "errorManager is null"));
        setFormatter(requireNonNull(formatter, "formatter is null"));

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
            if (queue.remainingCapacity() == 0) {
                queue.remove();
            }
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
        byte[] message = new byte[0];
        boolean poisonMessageSeen = false;
        while (!closed.get() || !poisonMessageSeen) {
            try {
                message = queue.poll(1, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (message == POISON_MESSAGE) {
                poisonMessageSeen = true;
            }
            else if (message != null) {
                try {
                    messageOutput.writeMessage(message);
                }
                catch (Exception e) {
                    droppedMessages.getAndIncrement();
                    reportError(null, e, WRITE_FAILURE);
                }
            }
            flush();
        }
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

    @Managed
    public long getDroppedMessages()
    {
        return droppedMessages.get();
    }
}
