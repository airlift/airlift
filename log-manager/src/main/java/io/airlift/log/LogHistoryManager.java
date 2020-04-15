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

import io.airlift.units.DataSize;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.airlift.log.LogFileName.parseHistoryLogFileName;
import static java.util.Objects.requireNonNull;

@ThreadSafe
final class LogHistoryManager
{
    private final Path masterLogFile;
    private final long maxTotalSize;

    @GuardedBy("this")
    private long totalSize;
    @GuardedBy("this")
    private final PriorityQueue<LogFile> files = new PriorityQueue<>();

    public LogHistoryManager(Path masterLogFile, DataSize maxTotalSize)
    {
        requireNonNull(masterLogFile, "masterLogFile is null");
        requireNonNull(maxTotalSize, "maxTotalSize is null");

        this.masterLogFile = masterLogFile;
        this.maxTotalSize = maxTotalSize.toBytes();

        // list existing logs
        try {
            Files.list(masterLogFile.getParent())
                    .map(this::createLogFile)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(files::add);
            totalSize = files.stream()
                    .mapToLong(LogFile::getSize)
                    .sum();
        }
        catch (IOException e) {
            throw new UncheckedIOException("Unable to list existing history log files for " + masterLogFile, e);
        }
        pruneLogFilesIfNecessary(0);
    }

    public synchronized long getTotalSize()
    {
        return totalSize;
    }

    public synchronized Set<LogFileName> getFiles()
    {
        return files.stream()
                .map(LogFile::getLogFileName)
                .collect(toImmutableSet());
    }

    public synchronized void pruneLogFilesIfNecessary(long otherDataSize)
    {
        while (totalSize + otherDataSize > maxTotalSize) {
            LogFile logFile = files.poll();
            if (logFile == null) {
                break;
            }

            // always reduce the cached total file size as we will either delete the file or stop tracking it
            totalSize -= logFile.getSize();

            // attempt to delete the file which may fail, because the file was already deleted or is not deletable
            // failure is ok as this is a best effort system
            try {
                Files.deleteIfExists(logFile.getPath());
            }
            catch (IOException ignored) {
            }
        }
    }

    private Optional<LogFile> createLogFile(Path path)
    {
        Optional<LogFileName> logFileName = parseHistoryLogFileName(masterLogFile.getFileName().toString(), path.getFileName().toString());
        if (!logFileName.isPresent()) {
            return Optional.empty();
        }

        BasicFileAttributes fileAttributes;
        try {
            fileAttributes = Files.readAttributes(path, BasicFileAttributes.class);
        }
        catch (IOException e) {
            return Optional.empty();
        }

        return Optional.of(new LogFile(path, logFileName.get(), fileAttributes.size()));
    }

    public synchronized void addFile(Path file, LogFileName fileName, long size)
    {
        files.add(new LogFile(file, fileName, size));
        totalSize += size;
    }

    public synchronized boolean removeFile(Path path)
    {
        return files.stream()
                .filter(file -> file.getPath().equals(path))
                .findFirst()
                .map(this::removeFile)
                .orElse(false);
    }

    private synchronized boolean removeFile(LogFile file)
    {
        if (!files.remove(file)) {
            return false;
        }
        totalSize -= file.getSize();
        return true;
    }

    private static class LogFile
            implements Comparable<LogFile>
    {
        private final Path path;
        private final LogFileName logFileName;
        private final long size;

        public LogFile(Path path, LogFileName logFileName, long size)
        {
            this.path = requireNonNull(path, "path is null");
            this.logFileName = requireNonNull(logFileName, "logFileName is null");
            checkArgument(size >= 0, "size is negative");
            this.size = size;
        }

        public Path getPath()
        {
            return path;
        }

        public LogFileName getLogFileName()
        {
            return logFileName;
        }

        public long getSize()
        {
            return size;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            LogFile logFile = (LogFile) o;
            return logFileName.equals(logFile.logFileName);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(logFileName);
        }

        @Override
        public int compareTo(LogFile o)
        {
            return logFileName.compareTo(o.logFileName);
        }

        @Override
        public String toString()
        {
            return logFileName.toString();
        }
    }
}
