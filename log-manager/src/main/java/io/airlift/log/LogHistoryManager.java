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

import com.google.errorprone.annotations.ThreadSafe;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.airlift.units.DataSize;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Stream;

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
        try (Stream<Path> paths = Files.list(masterLogFile.getParent())) {
            paths.map(this::createLogFile)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(files::add);
            totalSize = files.stream()
                    .mapToLong(LogFile::size)
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
                .map(LogFile::logFileName)
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
            totalSize -= logFile.size();

            // attempt to delete the file which may fail, because the file was already deleted or is not deletable
            // failure is ok as this is a best effort system
            try {
                Files.deleteIfExists(logFile.path());
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

        return Optional.of(new LogFile(path, logFileName.orElseThrow(), fileAttributes.size()));
    }

    public synchronized void addFile(Path file, LogFileName fileName, long size)
    {
        files.add(new LogFile(file, fileName, size));
        totalSize += size;
    }

    public synchronized boolean removeFile(Path path)
    {
        return files.stream()
                .filter(file -> file.path().equals(path))
                .findFirst()
                .map(this::removeFile)
                .orElse(false);
    }

    private synchronized boolean removeFile(LogFile file)
    {
        if (!files.remove(file)) {
            return false;
        }
        totalSize -= file.size();
        return true;
    }

    private record LogFile(Path path, LogFileName logFileName, long size)
            implements Comparable<LogFile>
    {
        private LogFile
        {
            requireNonNull(path, "path is null");
            requireNonNull(logFileName, "logFileName is null");
            checkArgument(size >= 0, "size is negative");
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
