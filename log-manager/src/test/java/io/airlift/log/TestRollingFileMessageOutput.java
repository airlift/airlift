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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import io.airlift.units.DataSize;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.io.MoreFiles.asByteSource;
import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static io.airlift.log.Format.TEXT;
import static io.airlift.log.LogFileName.parseHistoryLogFileName;
import static io.airlift.log.RollingFileMessageOutput.CompressionType.GZIP;
import static io.airlift.log.RollingFileMessageOutput.CompressionType.NONE;
import static io.airlift.units.DataSize.Unit.BYTE;
import static io.airlift.units.DataSize.Unit.MEGABYTE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.util.function.Predicate.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Test(timeOut = 5 * 60 * 1000)
public class TestRollingFileMessageOutput
{
    public static final ImmutableMap<String, String> TESTING_ANNOTATIONS = ImmutableMap.of("environment", "testing");

    @Test
    public void testBasicLogging()
            throws Exception
    {
        Path tempDir = Files.createTempDirectory("logging-test");
        try {
            Path masterFile = tempDir.resolve("launcher.log");

            BufferedHandler handler = createRollingFileHandler(masterFile.toString(), DataSize.of(1, MEGABYTE), DataSize.of(10, MEGABYTE), NONE, TEXT.createFormatter(TESTING_ANNOTATIONS), new ErrorManager());
            assertLogDirectory(masterFile);

            assertThat(masterFile).exists();
            assertThat(masterFile).isSymbolicLink();

            handler.publish(new LogRecord(Level.SEVERE, "apple"));

            List<String> lines = waitForExactLines(masterFile, 1);
            assertThat(lines).hasSize(1);
            assertThat(lines)
                    .filteredOn(line -> line.contains("environment=testing"))
                    .hasSize(1);
            assertThat(lines)
                    .filteredOn(line -> line.contains("apple"))
                    .hasSize(1);

            handler.publish(new LogRecord(Level.SEVERE, "banana"));
            lines = waitForExactLines(masterFile, 2);
            assertThat(lines).hasSize(2);
            assertThat(Files.readAllLines(masterFile, UTF_8))
                    .filteredOn(line -> line.contains("banana"))
                    .hasSize(1);

            assertLogDirectory(masterFile);
            handler.close();
            assertLogDirectory(masterFile);
        }
        finally {
            deleteRecursively(tempDir, ALLOW_INSECURE);
        }
    }

    @Test
    public void testBrokenLink()
            throws Exception
    {
        Path tempDir = Files.createTempDirectory("logging-test");
        try {
            Path masterFile = tempDir.resolve("launcher.log");
            // start with a broken symlink
            Files.createSymbolicLink(masterFile, tempDir.resolve("launcher.log.broken"));

            BufferedHandler handler = createRollingFileHandler(masterFile.toString(), DataSize.of(1, MEGABYTE), DataSize.of(10, MEGABYTE), NONE, TEXT.createFormatter(TESTING_ANNOTATIONS), new ErrorManager());
            assertLogDirectory(masterFile);

            assertThat(masterFile).exists();
            assertThat(masterFile).isSymbolicLink();

            handler.publish(new LogRecord(Level.SEVERE, "apple"));

            List<String> lines = waitForExactLines(masterFile, 1);
            assertThat(lines).hasSize(1);

            assertLogDirectory(masterFile);
            handler.close();
            assertLogDirectory(masterFile);
        }
        finally {
            deleteRecursively(tempDir, ALLOW_INSECURE);
        }
    }

    @Test
    public void testExistingDirectory()
            throws Exception
    {
        Path tempDir = Files.createTempDirectory("logging-test");
        try {
            Path masterFile = tempDir.resolve("launcher.log");

            // master file is a directory
            Files.createDirectories(masterFile);
            assertThatThrownBy(() -> createRollingFileHandler(
                    masterFile.toString(),
                    DataSize.of(1, MEGABYTE),
                    DataSize.of(10, MEGABYTE),
                    NONE,
                    TEXT.createFormatter(TESTING_ANNOTATIONS),
                    new ErrorManager()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Log file is an existing directory");
            Files.delete(masterFile);

            // master file is a symlink to a directory
            Path someDirectory = tempDir.resolve("launcher.log.directory");
            Files.createDirectories(someDirectory);
            Files.createSymbolicLink(masterFile, someDirectory);
            assertThatThrownBy(() -> createRollingFileHandler(
                    masterFile.toString(),
                    DataSize.of(1, MEGABYTE),
                    DataSize.of(10, MEGABYTE),
                    NONE,
                    TEXT.createFormatter(TESTING_ANNOTATIONS),
                    new ErrorManager()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Log file is an existing directory");
        }
        finally {
            deleteRecursively(tempDir, ALLOW_INSECURE);
        }
    }

    @Test
    public void testRollAndPrune()
            throws Exception
    {
        String message = Strings.padEnd("", 99, 'x') + "\n";

        Path tempDir = Files.createTempDirectory("logging-test");
        try {
            Path masterFile = tempDir.resolve("launcher.log");
            BufferedHandler handler = createRollingFileHandler(
                    masterFile.toString(),
                    DataSize.of(message.length() * 5L, BYTE),
                    DataSize.of(message.length() * 2L + message.length() * 5L + message.length() * 5L, BYTE), // 2 messages + 2 closed files
                    NONE,
                    TEXT.createFormatter(ImmutableMap.of()),
                    new ErrorManager());

            // use a handler that prints the raw message
            handler.setFormatter(new Formatter()
            {
                @Override
                public String format(LogRecord record)
                {
                    return record.getMessage();
                }
            });
            assertLogDirectory(masterFile);

            assertLogSizes(masterFile, handler, 0, message.length(), 1);

            // fill the first file
            for (int i = 0; i < 5; i++) {
                handler.publish(new LogRecord(Level.SEVERE, message));
                assertLogSizes(masterFile, handler, i + 1, message.length(), 1);
            }
            assertLogDirectory(masterFile);

            // fill the second file
            for (int i = 0; i < 5; i++) {
                handler.publish(new LogRecord(Level.SEVERE, message));
                assertLogSizes(masterFile, handler, i + 1, message.length(), 2);
            }
            assertLogDirectory(masterFile);

            // Log 2 messages to the second file (before pruning)
            for (int i = 0; i < 2; i++) {
                handler.publish(new LogRecord(Level.SEVERE, message));
                assertLogSizes(masterFile, handler, i + 1, message.length(), 3);
            }
            assertLogDirectory(masterFile);

            // fill the third (first file is pruned)
            for (int i = 0; i < 3; i++) {
                handler.publish(new LogRecord(Level.SEVERE, message));
                assertLogSizes(masterFile, handler, i + 3, message.length(), 2);
            }
            assertLogDirectory(masterFile);

            // Log 2 messages to the forth file (before pruning)
            for (int i = 0; i < 2; i++) {
                handler.publish(new LogRecord(Level.SEVERE, message));
                assertLogSizes(masterFile, handler, i + 1, message.length(), 3);
            }
            assertLogDirectory(masterFile);

            // fill the forth (another file is pruned)
            for (int i = 0; i < 3; i++) {
                handler.publish(new LogRecord(Level.SEVERE, message));
                assertLogSizes(masterFile, handler, i + 3, message.length(), 2);
            }
            assertLogDirectory(masterFile);

            handler.close();
            assertLogDirectory(masterFile);
        }
        finally {
            deleteRecursively(tempDir, ALLOW_INSECURE);
        }
    }

    @Test
    public void testCompression()
            throws Exception
    {
        String message = Strings.padEnd("", 9, 'x') + "\n";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream compressOut = new GZIPOutputStream(out)) {
            for (int i = 0; i < 5; i++) {
                compressOut.write(message.getBytes(UTF_8));
            }
            compressOut.flush();
        }
        int expectedCompressedSize = out.toByteArray().length;
        // to make testing easier, we assume that compressed file is greater than one message and less than 5 messages
        assertThat(expectedCompressedSize).isBetween(message.length(), message.length() * 5);

        Path tempDir = Files.createTempDirectory("logging-test");
        try {
            Path masterFile = tempDir.resolve("launcher.log");
            BufferedHandler handler = createRollingFileHandler(
                    masterFile.toString(),
                    DataSize.of(message.length() * 5L, BYTE),
                    DataSize.of(message.length() + message.length() * 5L + expectedCompressedSize, BYTE), // one message, one uncompressed file, one compressed file
                    GZIP,
                    TEXT.createFormatter(ImmutableMap.of()),
                    new ErrorManager());

            // use a handler that prints the raw message
            handler.setFormatter(new Formatter()
            {
                @Override
                public String format(LogRecord record)
                {
                    return record.getMessage();
                }
            });
            assertLogDirectory(masterFile);

            assertLogSizes(masterFile, handler, 0, message.length(), 1);

            // fill the first file
            for (int i = 1; i < 6; i++) {
                handler.publish(new LogRecord(Level.SEVERE, message));
                assertLogSizes(masterFile, handler, i, message.length(), 1);
            }
            assertLogDirectory(masterFile);

            // log one more message which will trigger roll and compression
            handler.publish(new LogRecord(Level.SEVERE, message));
            assertCompression(masterFile, handler, message, 2, 5, expectedCompressedSize);
            assertLogDirectory(masterFile);

            // fill the second file
            for (int i = 0; i < 4; i++) {
                handler.publish(new LogRecord(Level.SEVERE, message));
                // sizes can't really be tested while logging due to compression
            }
            assertCompression(masterFile, handler, message, 2, 5, expectedCompressedSize);
            assertLogDirectory(masterFile);

            // log one more message which will trigger roll and compression
            handler.publish(new LogRecord(Level.SEVERE, message));
            assertCompression(masterFile, handler, message, 3, 5, expectedCompressedSize);
            assertLogDirectory(masterFile);

            // fill the third file
            for (int i = 0; i < 4; i++) {
                handler.publish(new LogRecord(Level.SEVERE, message));
                // sizes can't really be tested while logging due to compression
            }
            // the oldest log file should have been pruned
            assertLogSizes(masterFile, handler, 5, message.length(), 2);
            assertCompression(masterFile, handler, message, 2, 5, expectedCompressedSize);
            assertLogDirectory(masterFile);

            handler.close();
            assertLogDirectory(masterFile);
        }
        finally {
            deleteRecursively(tempDir, ALLOW_INSECURE);
        }
    }

    @Test
    public void testClosedHandler()
            throws Exception
    {
        Path tempDir = Files.createTempDirectory("logging-test");
        try {
            Path masterFile = tempDir.resolve("launcher.log");
            BufferedHandler handler = createRollingFileHandler(masterFile.toString(), DataSize.of(1, MEGABYTE), DataSize.of(10, MEGABYTE), NONE, TEXT.createFormatter(TESTING_ANNOTATIONS), new ErrorManager());

            handler.publish(new LogRecord(Level.SEVERE, "apple"));
            handler.publish(new LogRecord(Level.SEVERE, "banana"));

            handler.close();

            handler.publish(new LogRecord(Level.SEVERE, "cherry"));

            List<String> lines = waitForExactLines(masterFile, 2);
            assertThat(lines.size()).isEqualTo(2);
            assertThat(Files.readAllLines(masterFile, UTF_8))
                    .filteredOn(line -> line.contains("apple") || line.contains("banana"))
                    .hasSize(2);

            // these should not throw
            handler.flush();
            handler.close();
        }
        finally {
            deleteRecursively(tempDir, ALLOW_INSECURE);
        }
    }

    @Test
    public void testLoggingInExistingDirectory()
            throws Exception
    {
        Path tempDir = Files.createTempDirectory("logging-test");
        try {
            Path masterFile = tempDir.resolve("launcher.log");
            BufferedHandler handler = createRollingFileHandler(masterFile.toString(), DataSize.of(1, MEGABYTE), DataSize.of(10, MEGABYTE), NONE, TEXT.createFormatter(TESTING_ANNOTATIONS), new ErrorManager());
            assertLogDirectory(masterFile);
            Path firstLogFile = Files.readSymbolicLink(masterFile);

            handler.publish(new LogRecord(Level.SEVERE, "apple"));
            handler.publish(new LogRecord(Level.SEVERE, "banana"));

            List<String> lines = waitForExactLines(masterFile, 2);
            assertThat(lines).hasSize(2);
            assertThat(Files.readAllLines(masterFile, UTF_8))
                    .filteredOn(line -> line.contains("apple") || line.contains("banana"))
                    .hasSize(2);

            assertLogDirectory(masterFile);
            handler.close();
            assertLogDirectory(masterFile);

            // open new handler
            handler = createRollingFileHandler(masterFile.toString(), DataSize.of(1, MEGABYTE), DataSize.of(10, MEGABYTE), NONE, TEXT.createFormatter(TESTING_ANNOTATIONS), new ErrorManager());

            assertLogDirectory(masterFile);
            assertThat(Files.readSymbolicLink(masterFile)).isNotEqualTo(firstLogFile);

            handler.publish(new LogRecord(Level.SEVERE, "cherry"));

            lines = waitForExactLines(masterFile, 1);
            assertThat(lines).hasSize(1);
            assertThat(lines)
                    .filteredOn(line -> line.contains("cherry"))
                    .hasSize(1);

            assertLogDirectory(masterFile);
            handler.close();
            assertLogDirectory(masterFile);
        }
        finally {
            deleteRecursively(tempDir, ALLOW_INSECURE);
        }
    }

    @Test
    public void testLoggingInExistingLegacyDirectory()
            throws Exception
    {
        // test file movement
        // test history visibility
        Path tempDir = Files.createTempDirectory("logging-test");
        try {
            // simulate legacy handler
            Path masterFile = tempDir.resolve("launcher.log");

            Files.writeString(masterFile, new StaticFormatter().formatMessage(new LogRecord(Level.SEVERE, "apple")), CREATE, APPEND);
            Files.writeString(masterFile, new StaticFormatter().formatMessage(new LogRecord(Level.SEVERE, "banana")), CREATE, APPEND);

            assertThat(masterFile).isRegularFile();

            List<String> lines = waitForExactLines(masterFile, 2);
            assertThat(lines).hasSize(2);
            assertThat(Files.readAllLines(masterFile, UTF_8))
                    .filteredOn(line -> line.contains("apple") || line.contains("banana"))
                    .hasSize(2);

            // open new handler
            BufferedHandler newHandler = createRollingFileHandler(masterFile.toString(), DataSize.of(1, MEGABYTE), DataSize.of(10, MEGABYTE), NONE, TEXT.createFormatter(TESTING_ANNOTATIONS), new ErrorManager());
            assertLogDirectory(masterFile);

            assertThat(masterFile).isSymbolicLink();
            // should be tracking legacy file and new file
            assertThat(((RollingFileMessageOutput) newHandler.getMessageOutput()).getFiles().size()).isEqualTo(2);

            newHandler.publish(new LogRecord(Level.SEVERE, "cherry"));
            newHandler.publish(new LogRecord(Level.SEVERE, "date"));

            lines = waitForExactLines(masterFile, 2);
            assertThat(lines).hasSize(2);
            assertThat(lines)
                    .filteredOn(line -> line.contains("cherry") || line.contains("date"))
                    .hasSize(2);

            assertLogDirectory(masterFile);
            newHandler.close();
            assertLogDirectory(masterFile);
        }
        finally {
            deleteRecursively(tempDir, ALLOW_INSECURE);
        }
    }

    private static class GzippedByteSource
            extends ByteSource
    {
        private final ByteSource source;

        public GzippedByteSource(ByteSource gzippedSource)
        {
            source = gzippedSource;
        }

        @Override
        public InputStream openStream()
                throws IOException
        {
            return new GZIPInputStream(source.openStream());
        }
    }

    private static BufferedHandler createRollingFileHandler(
            String filename,
            DataSize maxFileSize,
            DataSize maxTotalSize,
            RollingFileMessageOutput.CompressionType compressionType,
            Formatter formatter,
            ErrorManager errorManager)
    {
        RollingFileMessageOutput output = new RollingFileMessageOutput(filename, maxFileSize, maxTotalSize, compressionType);
        BufferedHandler handler = new BufferedHandler(output, formatter, errorManager);
        handler.initialize();
        return handler;
    }

    private static void assertLogDirectory(Path masterFile)
            throws Exception
    {
        assertThat(masterFile.getParent()).isDirectory();
        assertThat(masterFile).isSymbolicLink();

        Path symbolicLinkTarget = Files.readSymbolicLink(masterFile);
        assertThat(symbolicLinkTarget).isRelative();
        assertThat(symbolicLinkTarget.getNameCount()).isEqualTo(1);
        assertThat(symbolicLinkTarget).hasNoParentRaw();

        List<Path> logFiles = Files.list(masterFile.getParent())
                .filter(not(masterFile::equals))
                .collect(toImmutableList());
        for (Path logFile : logFiles) {
            assertThat(logFile).isRegularFile();
            assertThat(parseHistoryLogFileName(masterFile.getFileName().toString(), logFile.getFileName().toString()).isPresent()).isTrue();
        }
    }

    private static void assertCompression(Path masterFile, BufferedHandler handler, String message, int expectedFileCount, int expectedLineCount, int expectedCompressedSize)
            throws Exception
    {
        Set<LogFileName> compressedFileNames = waitForCompression((RollingFileMessageOutput) handler.getMessageOutput(), expectedFileCount);
        assertThat(compressedFileNames).hasSize(expectedFileCount - 1);

        for (LogFileName compressedFileName : compressedFileNames) {
            Path compressedFile = masterFile.resolveSibling(compressedFileName.getFileName());

            assertThat(compressedFile).hasSize(expectedCompressedSize);

            List<String> lines = new GzippedByteSource(asByteSource(compressedFile))
                    .asCharSource(UTF_8)
                    .readLines();
            assertThat(lines).hasSize(expectedLineCount);
            assertThat(lines).allMatch(message.trim()::equals);
        }
    }

    private static void assertLogSizes(Path masterFile, BufferedHandler handler, int expectedLines, int lineSize, int expectedFileCount)
            throws Exception
    {
        Set<LogFileName> files = waitForExactFiles((RollingFileMessageOutput) handler.getMessageOutput(), expectedFileCount);
        assertThat(files).hasSize(expectedFileCount);

        List<String> lines = waitForExactLines(masterFile, expectedLines);
        assertThat(lines)
                .hasSize(expectedLines);
        assertThat(masterFile)
                .hasSize((long) expectedLines * lineSize);
    }

    private static List<String> waitForExactLines(Path masterFile, int exactCount)
            throws IOException, InterruptedException
    {
        while (true) {
            List<String> lines = Files.readAllLines(masterFile, UTF_8);
            if (lines.size() == exactCount) {
                return lines;
            }
            Thread.sleep(10);
        }
    }

    private static Set<LogFileName> waitForExactFiles(RollingFileMessageOutput fileHandler, int exactCount)
            throws Exception
    {
        while (true) {
            Set<LogFileName> files = fileHandler.getFiles();
            if (files.size() == exactCount) {
                return files;
            }
            Thread.sleep(10);
        }
    }

    private static Set<LogFileName> waitForCompression(RollingFileMessageOutput fileHandler, int exactCount)
            throws Exception
    {
        while (true) {
            Set<LogFileName> files = fileHandler.getFiles();
            if (files.size() == exactCount) {
                Set<LogFileName> compressedFiles = files.stream()
                        .filter(LogFileName::isCompressed)
                        .collect(toImmutableSet());
                if (compressedFiles.size() == exactCount - 1) {
                    return compressedFiles;
                }
            }
            Thread.sleep(10);
        }
    }
}
