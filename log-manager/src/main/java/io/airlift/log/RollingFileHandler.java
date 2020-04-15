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
package io.airlift.log;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import com.google.common.io.MoreFiles;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.airlift.units.DataSize;

import javax.annotation.concurrent.GuardedBy;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.zip.GZIPOutputStream;

import static io.airlift.units.DataSize.Unit.MEGABYTE;
import static java.lang.Math.toIntExact;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.logging.ErrorManager.CLOSE_FAILURE;
import static java.util.logging.ErrorManager.FLUSH_FAILURE;
import static java.util.logging.ErrorManager.FORMAT_FAILURE;
import static java.util.logging.ErrorManager.GENERIC_FAILURE;
import static java.util.logging.ErrorManager.OPEN_FAILURE;
import static java.util.logging.ErrorManager.WRITE_FAILURE;

final class RollingFileHandler
        extends Handler
{
    public enum CompressionType
    {
        NONE(Optional.empty()),
        GZIP(Optional.of(".gz"));

        private final Optional<String> extension;

        CompressionType(Optional<String> extension)
        {
            this.extension = requireNonNull(extension, "extension is null");
        }

        public Optional<String> getExtension()
        {
            return extension;
        }
    }

    private static final int MAX_OPEN_NEW_LOG_ATTEMPTS = 100;
    private static final int MAX_BATCH_COUNT = 1024;
    private static final int MAX_BATCH_BYTES = toIntExact(new DataSize(1, MEGABYTE).toBytes());

    private static final String TEMP_PREFIX = ".tmp.";
    private static final String DELETED_PREFIX = ".deleted.";

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("-yyyyMMdd.HHmmss");

    private static final byte[] POISON_MESSAGE = new byte[0];

    private final Path symlink;
    private final long maxFileSize;
    private final CompressionType compressionType;

    @GuardedBy("this")
    private Path currentOutputFile;
    @GuardedBy("this")
    private LogFileName currentOutputFileName;
    @GuardedBy("this")
    private OutputStream currentOutputStream;
    @GuardedBy("this")
    private long currentFileSize;
    private final LogHistoryManager historyManager;

    private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(MAX_BATCH_COUNT);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread thread;

    private final ExecutorService compressionExecutor;

    public static RollingFileHandler createRollingFileHandler(String filename, DataSize maxFileSize, DataSize maxTotalSize, CompressionType compressionType)
    {
        RollingFileHandler handler = new RollingFileHandler(filename, maxFileSize, maxTotalSize, compressionType);
        handler.start();
        return handler;
    }

    private RollingFileHandler(String filename, DataSize maxFileSize, DataSize maxTotalSize, CompressionType compressionType)
    {
        requireNonNull(filename, "filename is null");
        requireNonNull(maxFileSize, "maxFileSize is null");
        requireNonNull(maxTotalSize, "maxTotalSize is null");
        requireNonNull(compressionType, "compressionType is null");

        this.maxFileSize = maxFileSize.toBytes();
        this.compressionType = compressionType;

        symlink = Paths.get(filename);

        thread = new Thread(this::logging);
        thread.setName("log-writer");
        thread.setDaemon(true);

        setFormatter(new StaticFormatter());

        // ensure log directory can be created
        try {
            MoreFiles.createParentDirectories(symlink);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // convert from legacy logger
        try {
            LegacyRollingFileHandler.recoverTempFiles(filename);
        }
        catch (IOException e) {
            reportError(null, e, OPEN_FAILURE);
        }
        if (Files.exists(symlink)) {
            try {
                // if existing link file is a legacy log file, rename it to so link file can be recreated as a symlink
                BasicFileAttributes attributes = Files.readAttributes(symlink, BasicFileAttributes.class);
                if (attributes.isDirectory()) {
                    throw new IllegalArgumentException("Log file is an existing directory: " + filename);
                }
                if (attributes.isRegularFile()) {
                    LocalDateTime createTime = LocalDateTime.ofInstant(attributes.creationTime().toInstant(), ZoneId.systemDefault()).withNano(0);
                    Path logFile = symlink.resolveSibling(symlink.getFileName() + DATE_TIME_FORMATTER.format(createTime) + "--" + randomUUID());
                    Files.move(symlink, logFile, ATOMIC_MOVE);
                }
            }
            catch (IOException e) {
                throw new UncheckedIOException("Unable to update move legacy log file to a new file", e);
            }
        }

        tryCleanupTempFiles(symlink);

        historyManager = new LogHistoryManager(symlink, maxTotalSize);

        // open initial log file
        try {
            rollFile();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (compressionType != CompressionType.NONE) {
            compressionExecutor = newSingleThreadExecutor(new ThreadFactoryBuilder()
                    .setDaemon(true)
                    .setNameFormat("log-compression-%d")
                    .build());
        }
        else {
            compressionExecutor = null;
        }
    }

    private void start()
    {
        thread.start();
    }

    public synchronized Set<LogFileName> getFiles()
    {
        ImmutableSet.Builder<LogFileName> objectBuilder = ImmutableSet.<LogFileName>builder()
                .addAll(historyManager.getFiles());
        if (currentOutputFileName != null) {
            objectBuilder.add(currentOutputFileName);
        }
        return objectBuilder.build();
    }

    @Override
    public void publish(LogRecord record)
    {
        // if closed messages are dropped
        if (closed.get()) {
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
        if (currentOutputStream != null) {
            try {
                currentOutputStream.flush();
            }
            catch (Exception e) {
                reportError(null, e, FLUSH_FAILURE);
            }
        }
    }

    @Override
    public void close()
            throws SecurityException
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

        // there shouldn't be any queued methods, but be safe
        queue.clear();

        // wait for compression to finish
        if (compressionExecutor != null) {
            compressionExecutor.shutdown();
            try {
                compressionExecutor.awaitTermination(1, TimeUnit.MINUTES);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void logging()
    {
        while (!closed.get() || !queue.isEmpty()) {
            processQueue();
        }

        // logging is closed, so close the current output file
        synchronized (this) {
            try {
                currentOutputStream.flush();
            }
            catch (IOException e) {
                reportError("Could not flush output stream", e, CLOSE_FAILURE);
            }
            try {
                currentOutputStream.close();
            }
            catch (IOException e) {
                reportError("Could not close output stream", e, CLOSE_FAILURE);
            }
            currentOutputFile = null;
            currentOutputFileName = null;
            currentOutputStream = null;
            currentFileSize = 0;
        }

        // there shouldn't be any queued methods, but be safe
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
        }
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

    private synchronized void logMessageBatch(List<byte[]> batch)
    {
        for (byte[] message : batch) {
            if (currentFileSize + message.length > maxFileSize) {
                try {
                    rollFile();
                }
                catch (IOException e) {
                    // It is possible the roll worked, but there was a problem cleaning up, and it is possible it failed;
                    // Either way, roll will be attempted again once file grows by maxFileSize.
                    currentFileSize = 0;

                    reportError("Error rolling log file", e, GENERIC_FAILURE);
                }
            }

            historyManager.pruneLogFilesIfNecessary(currentFileSize + message.length);

            currentFileSize += message.length;
            try {
                currentOutputStream.write(message);
            }
            catch (Exception e) {
                reportError(null, e, WRITE_FAILURE);
            }
        }
        // always flush at the end of a batch, so logs aren't delayed
        flush();
    }

    private synchronized void rollFile()
            throws IOException
    {
        // carefully update the stream, so if there is a problem logging can continue

        LogFileName newFileName = null;
        Path newFile = null;
        OutputStream newOutputStream = null;
        for (int i = 0; i < MAX_OPEN_NEW_LOG_ATTEMPTS; i++) {
            try {
                newFileName = LogFileName.generateNextLogFileName(symlink, compressionType.getExtension());
                newFile = symlink.resolveSibling(newFileName.getFileName());
                newOutputStream = new BufferedOutputStream(Files.newOutputStream(newFile, CREATE_NEW), MAX_BATCH_BYTES);
                break;
            }
            catch (FileAlreadyExistsException ignore) {
            }
        }

        // If a new file can not be opened, abort and continue using the existing file
        if (newOutputStream == null) {
            throw new IOException("Could not create new a unique log file: " + newFile);
        }

        // The new file is open, so we will always switch to this new output stream
        // If any error occurs, with the cleanup steps, we add them to this exception as suppressed and throw at the end
        IOException exception = new IOException(String.format("Unable to %s log file", currentOutputStream == null ? "setup initial" : "roll"));

        // close and optionally compress the currently open log (there is no open log during initial setup)
        if (currentOutputStream != null) {
            try {
                currentOutputStream.close();
            }
            catch (IOException e) {
                exception.addSuppressed(new IOException("Unable to close old output stream: " + currentOutputFile, e));
            }
            historyManager.addFile(currentOutputFile, currentOutputFileName, currentFileSize);
            if (compressionExecutor != null) {
                Path originalFile = currentOutputFile;
                LogFileName originalLogFileName = currentOutputFileName;
                long originalFileSize = currentFileSize;
                compressionExecutor.submit(() -> compressInternal(originalFile, originalLogFileName, originalFileSize));
            }
        }

        currentOutputFile = newFile;
        currentOutputFileName = newFileName;
        currentOutputStream = newOutputStream;
        currentFileSize = 0;

        // update symlink
        try {
            if (Files.exists(symlink)) {
                Files.delete(symlink);
            }
            Files.createSymbolicLink(symlink, newFile);
        }
        catch (IOException e) {
            exception.addSuppressed(new IOException("Unable to update symlink", e));
        }

        if (exception.getSuppressed().length > 0) {
            throw exception;
        }
    }

    private void compressInternal(Path originalFile, LogFileName originalLogFileName, long originalFileSize)
    {
        tryCleanupTempFiles(symlink);

        String compressionExtension = compressionType.getExtension().orElseThrow(IllegalStateException::new);

        // compress file
        Path tempFile = originalFile.resolveSibling(TEMP_PREFIX + originalFile.getFileName() + compressionExtension);
        try (
                InputStream input = Files.newInputStream(originalFile);
                GZIPOutputStream gzipOutputStream = new GZIPOutputStream(Files.newOutputStream(tempFile))) {
            ByteStreams.copy(input, gzipOutputStream);
        }
        catch (IOException e) {
            reportError("Unable to compress log file", e, GENERIC_FAILURE);
            return;
        }

        // Size can only be checked after the gzip stream is closed
        long compressedSize;
        try {
            compressedSize = Files.size(tempFile);
        }
        catch (IOException e) {
            reportError("Unable to get size of compress log file", e, GENERIC_FAILURE);
            return;
        }

        // file movement must be done while holding the lock to ensure there isn't a roll during the movement
        synchronized (this) {
            // remove the original file from the history manager
            if (!historyManager.removeFile(originalFile)) {
                // file was removed during compression
                try {
                    Files.deleteIfExists(tempFile);
                }
                catch (IOException e) {
                    reportError("Unable to delete compress log file", e, GENERIC_FAILURE);
                }
                return;
            }

            Path compressedFile = originalFile.resolveSibling(originalFile.getFileName() + compressionExtension);

            // 1. Move temp file to final compressed name
            LogFileName compressedFileName = originalLogFileName.withCompression(compressedFile);
            try {
                Files.move(tempFile, compressedFile, ATOMIC_MOVE);
            }
            catch (IOException e) {
                // add the original file back to the history manager
                historyManager.addFile(originalFile, originalLogFileName, originalFileSize);

                // move failed, delete the temp file
                try {
                    Files.deleteIfExists(tempFile);
                }
                catch (IOException ignored) {
                    // delete failed, system will attempt to delete temp files periodically
                }
            }
            historyManager.addFile(compressedFile, compressedFileName, compressedSize);

            // 3. Delete original file
            try {
                Files.deleteIfExists(originalFile);
            }
            catch (IOException deleteException) {
                // delete failed, try to move the file out of the way
                try {
                    Files.move(originalFile, originalFile.resolveSibling(DELETED_PREFIX + originalFile.getFileName()), ATOMIC_MOVE);
                }
                catch (IOException ignored) {
                    // delete and move failed, there is nothing that can be done
                    reportError("Unable to delete original file after compression", deleteException, GENERIC_FAILURE);
                }
            }
        }
    }

    private static void tryCleanupTempFiles(Path masterLogFile)
    {
        try {
            for (Path file : MoreFiles.listFiles(masterLogFile.getParent())) {
                String fileName = file.getFileName().toString();
                String fileNameWithoutPrefix;
                if (fileName.startsWith(TEMP_PREFIX)) {
                    fileNameWithoutPrefix = fileName.substring(TEMP_PREFIX.length());
                }
                else if (fileName.startsWith(DELETED_PREFIX)) {
                    fileNameWithoutPrefix = fileName.substring(DELETED_PREFIX.length());
                }
                else {
                    continue;
                }
                if (LogFileName.parseHistoryLogFileName(masterLogFile.getFileName().toString(), fileNameWithoutPrefix).isPresent()) {
                    // this is our temp or "to be deleted' file, so try to delete it
                    try {
                        Files.deleteIfExists(file);
                    }
                    catch (IOException ignored) {
                    }
                }
            }
        }
        catch (IOException ignored) {
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
}
