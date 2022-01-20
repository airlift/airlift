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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.zip.GZIPOutputStream;

import static io.airlift.units.DataSize.Unit.MEGABYTE;
import static java.lang.Math.toIntExact;
import static java.lang.String.format;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.logging.ErrorManager.GENERIC_FAILURE;

final class RollingFileMessageOutput
        implements MessageOutput
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
    private static final int MAX_BATCH_BYTES = toIntExact(new DataSize(1, MEGABYTE).toBytes());
    private static final String TEMP_PREFIX = ".tmp.";
    private static final String DELETED_PREFIX = ".deleted.";

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("-yyyyMMdd.HHmmss");

    private final Path symlink;
    private final long maxFileSize;
    private final CompressionType compressionType;
    private final Formatter formatter;

    @GuardedBy("this")
    private Path currentOutputFile;
    @GuardedBy("this")
    private LogFileName currentOutputFileName;
    @GuardedBy("this")
    private long currentFileSize;
    @GuardedBy("this")
    private OutputStream currentOutputStream;

    private final LogHistoryManager historyManager;

    private final ExecutorService compressionExecutor;

    public static BufferedHandler createRollingFileHandler(
            String filename,
            DataSize maxFileSize,
            DataSize maxTotalSize,
            CompressionType compressionType,
            Formatter formatter,
            ErrorManager errorManager)
    {
        RollingFileMessageOutput output = new RollingFileMessageOutput(filename, maxFileSize, maxTotalSize, compressionType, formatter);
        BufferedHandler handler = new BufferedHandler(output, formatter, errorManager);
        handler.start();
        return handler;
    }

    private RollingFileMessageOutput(String filename, DataSize maxFileSize, DataSize maxTotalSize, CompressionType compressionType, Formatter formatter)
    {
        requireNonNull(filename, "filename is null");
        requireNonNull(maxFileSize, "maxFileSize is null");
        requireNonNull(maxTotalSize, "maxTotalSize is null");
        requireNonNull(compressionType, "compressionType is null");
        requireNonNull(formatter, "formatter is null");

        this.maxFileSize = maxFileSize.toBytes();
        this.compressionType = compressionType;
        this.formatter = formatter;

        symlink = Paths.get(filename);

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
            new ErrorManager().error("Unable to recover legacy logging temp files", e, GENERIC_FAILURE);
        }

        if (Files.exists(symlink, NOFOLLOW_LINKS)) {
            try {
                // verify existing file is not a directory or a symlink to a directory
                // Note: do not use NOFOLLOW here, as we want to know if target is a directory
                if (Files.isDirectory(symlink)) {
                    throw new IllegalArgumentException("Log file is an existing directory: " + filename);
                }
                // if existing symlink file is a legacy (non-symlink) log file, rename it so file can be recreated as a symlink
                if (!Files.isSymbolicLink(symlink)) {
                    BasicFileAttributes attributes = Files.readAttributes(symlink, BasicFileAttributes.class);
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

    @Override
    public synchronized void flush()
            throws IOException
    {
        if (currentOutputStream != null) {
            currentOutputStream.flush();
        }
    }

    @Override
    public synchronized void close()
            throws IOException
    {
        IOException exception = new IOException("Exception thrown attempting to close the file output.");

        if (currentOutputStream != null) {
            try {
                currentOutputStream.flush();
            }
            catch (IOException e) {
                exception.addSuppressed(e);
            }
            try {
                currentOutputStream.close();
            }
            catch (IOException e) {
                exception.addSuppressed(e);
            }
        }

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

        currentOutputStream = null;
        currentOutputFile = null;
        currentOutputFileName = null;
        currentFileSize = 0;

        if (exception.getSuppressed().length > 0) {
            throw exception;
        }
    }

    @Override
    public synchronized void writeMessage(byte[] message)
            throws IOException
    {
        if (currentFileSize > 0 && (currentFileSize + message.length > maxFileSize)) {
            try {
                rollFile();
            }
            catch (IOException e) {
                // It is possible the roll worked, but there was a problem cleaning up, and it is possible it failed;
                // Either way, roll will be attempted again once file grows by maxFileSize.
                currentFileSize = 0;
                throw new IOException("Error rolling log file", e);
            }
        }
        historyManager.pruneLogFilesIfNecessary(currentFileSize + message.length);
        currentFileSize += message.length;
        currentOutputStream.write(message);
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
        IOException exception = new IOException(format("Unable to %s log file", currentOutputStream == null ? "setup initial" : "roll"));

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
                compressionExecutor.submit(() -> {
                    try {
                        compressInternal(originalFile, originalLogFileName, originalFileSize);
                    }
                    catch (IOException e) {
                        exception.addSuppressed(e);
                    }
                });
            }
        }

        currentOutputFile = newFile;
        currentOutputFileName = newFileName;
        currentOutputStream = newOutputStream;
        currentFileSize = 0;

        // update symlink
        try {
            Files.deleteIfExists(symlink);
            Files.createSymbolicLink(symlink, newFile);
        }
        catch (IOException e) {
            exception.addSuppressed(new IOException(format("Unable to update symlink %s to %s", symlink, newFile), e));
        }

        if (exception.getSuppressed().length > 0) {
            throw exception;
        }
    }

    private void compressInternal(Path originalFile, LogFileName originalLogFileName, long originalFileSize)
            throws IOException
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
            throw new IOException("Unable to compress log file", e);
        }

        // Size can only be checked after the gzip stream is closed
        long compressedSize;
        try {
            compressedSize = Files.size(tempFile);
        }
        catch (IOException e) {
            throw new IOException("Unable to get size of compress log file", e);
        }

        // file movement must be done while holding the lock to ensure there isn't a roll during the movement
        synchronized (this) {
            // 1. remove the original file from the history manager
            if (!historyManager.removeFile(originalFile)) {
                // file was removed during compression
                try {
                    Files.deleteIfExists(tempFile);
                }
                catch (IOException e) {
                    throw new IOException("Unable to delete compress log file", e);
                }
                return;
            }

            Path compressedFile = originalFile.resolveSibling(originalFile.getFileName() + compressionExtension);

            // 2. Move temp file to final compressed name
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
                    throw new IOException("Unable to delete original file after compression", deleteException);
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

    public synchronized Set<LogFileName> getFiles()
    {
        ImmutableSet.Builder<LogFileName> files = ImmutableSet.<LogFileName>builder()
                .addAll(historyManager.getFiles());
        if (currentOutputFileName != null) {
            files.add(currentOutputFileName);
        }
        return files.build();
    }
}
