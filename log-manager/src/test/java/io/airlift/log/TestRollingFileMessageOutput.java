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
import io.airlift.log.RollingFileMessageOutput.FileTimeRoller;
import io.airlift.testing.TestingTicker;
import io.airlift.units.DataSize;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
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
import static io.airlift.log.RollingFileMessageOutput.createRollingFileHandler;
import static io.airlift.units.DataSize.Unit.BYTE;
import static io.airlift.units.DataSize.Unit.MEGABYTE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.function.Predicate.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

@Test(timeOut = 5 * 60 * 1000)
public class TestRollingFileMessageOutput
{
    public static final ImmutableMap<String, String> TESTING_ANNOTATIONS = ImmutableMap.of("environment", "testing");

    private static final long TESTING_NOW = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).plusMinutes(10).toInstant().toEpochMilli();
    private static final FileTimeRoller TESTING_FILE_TIME_ROLLER = new FileTimeRoller(() -> TESTING_NOW, new TestingTicker());

    @Test
    public void testBasicLogging()
            throws Exception
    {
        Path tempDir = Files.createTempDirectory("logging-test");
        try {
            Path masterFile = tempDir.resolve("launcher.log");

            BufferedHandler handler = createRollingFileHandler(
                    masterFile.toString(),
                    new DataSize(1, MEGABYTE),
                    new DataSize(10, MEGABYTE),
                    NONE,
                    TEXT.createFormatter(TESTING_ANNOTATIONS),
                    new ErrorManager(),
                    TESTING_FILE_TIME_ROLLER);
            assertLogDirectory(masterFile);

            assertTrue(Files.exists(masterFile));
            assertTrue(Files.isSymbolicLink(masterFile));

            handler.publish(new LogRecord(Level.SEVERE, "apple"));

            List<String> lines = waitForExactLines(masterFile, 1);
            assertEquals(lines.size(), 1);
            assertEquals(
                    lines.stream()
                            .filter(line -> line.contains("environment=testing"))
                            .count(),
                    1);
            assertEquals(
                    lines.stream()
                            .filter(line -> line.contains("apple"))
                            .count(),
                    1);

            handler.publish(new LogRecord(Level.SEVERE, "banana"));
            lines = waitForExactLines(masterFile, 2);
            assertEquals(lines.size(), 2);
            assertEquals(
                    Files.readAllLines(masterFile, UTF_8).stream()
                            .filter(line -> line.contains("banana"))
                            .count(),
                    1);

            assertLogDirectory(masterFile);
            handler.close();
            assertLogDirectory(masterFile);
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
                    new DataSize(message.length() * 5, BYTE),
                    new DataSize(message.length() * 2 + message.length() * 5 + message.length() * 5, BYTE), // 2 messages + 2 closed files
                    NONE,
                    TEXT.createFormatter(ImmutableMap.of()),
                    new ErrorManager(),
                    TESTING_FILE_TIME_ROLLER);

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
    public void testRollDaily()
            throws Exception
    {
        String message = Strings.padEnd("", 99, 'x') + "\n";

        Path tempDir = Files.createTempDirectory("logging-test");
        try {
            TestingTicker ticker = new TestingTicker();
            AtomicLong systemMillis = new AtomicLong(TESTING_NOW);

            Path masterFile = tempDir.resolve("launcher.log");
            BufferedHandler handler = createRollingFileHandler(
                    masterFile.toString(),
                    new DataSize(1, MEGABYTE),
                    new DataSize(10, MEGABYTE),
                    NONE,
                    TEXT.createFormatter(TESTING_ANNOTATIONS),
                    new ErrorManager(),
                    new FileTimeRoller(systemMillis::get, ticker));

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

            // log some messages
            for (int i = 0; i < 5; i++) {
                handler.publish(new LogRecord(Level.SEVERE, message));
                assertLogSizes(masterFile, handler, i + 1, message.length(), 1);
            }
            assertLogDirectory(masterFile);

            for (int rollCount = 1; rollCount < 5; rollCount++) {
                // advance one day
                ticker.increment(1, DAYS);
                systemMillis.addAndGet(DAYS.toMillis(1));

                // log one message, which causes log file to roll
                handler.publish(new LogRecord(Level.SEVERE, message));
                assertLogSizes(masterFile, handler, 1, message.length(), 1 + rollCount);

                // log some more messages
                for (int i = 1; i < 5; i++) {
                    handler.publish(new LogRecord(Level.SEVERE, message));
                    assertLogSizes(masterFile, handler, i + 1, message.length(), 1 + rollCount);
                }
                assertLogDirectory(masterFile);
            }

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
        assertTrue(expectedCompressedSize < message.length() * 5);
        assertTrue(expectedCompressedSize > message.length());

        Path tempDir = Files.createTempDirectory("logging-test");
        try {
            Path masterFile = tempDir.resolve("launcher.log");
            BufferedHandler handler = createRollingFileHandler(
                    masterFile.toString(),
                    new DataSize(message.length() * 5, BYTE),
                    new DataSize(message.length() + message.length() * 5 + expectedCompressedSize, BYTE), // one message, one uncompressed file, one compressed file
                    GZIP,
                    TEXT.createFormatter(ImmutableMap.of()),
                    new ErrorManager(),
                    TESTING_FILE_TIME_ROLLER);

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
            BufferedHandler handler = createRollingFileHandler(
                    masterFile.toString(),
                    new DataSize(1, MEGABYTE),
                    new DataSize(10, MEGABYTE),
                    NONE,
                    TEXT.createFormatter(TESTING_ANNOTATIONS),
                    new ErrorManager(),
                    TESTING_FILE_TIME_ROLLER);

            handler.publish(new LogRecord(Level.SEVERE, "apple"));
            handler.publish(new LogRecord(Level.SEVERE, "banana"));

            handler.close();

            handler.publish(new LogRecord(Level.SEVERE, "cherry"));

            List<String> lines = waitForExactLines(masterFile, 2);
            assertEquals(lines.size(), 2);
            assertEquals(
                    Files.readAllLines(masterFile, UTF_8).stream()
                            .filter(line -> line.contains("apple") || line.contains("banana"))
                            .count(),
                    2);

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
            BufferedHandler handler = createRollingFileHandler(
                    masterFile.toString(),
                    new DataSize(1, MEGABYTE),
                    new DataSize(10, MEGABYTE),
                    NONE,
                    TEXT.createFormatter(TESTING_ANNOTATIONS),
                    new ErrorManager(),
                    TESTING_FILE_TIME_ROLLER);
            assertLogDirectory(masterFile);
            Path firstLogFile = Files.readSymbolicLink(masterFile);

            handler.publish(new LogRecord(Level.SEVERE, "apple"));
            handler.publish(new LogRecord(Level.SEVERE, "banana"));

            List<String> lines = waitForExactLines(masterFile, 2);
            assertEquals(lines.size(), 2);
            assertEquals(
                    Files.readAllLines(masterFile, UTF_8).stream()
                            .filter(line -> line.contains("apple") || line.contains("banana"))
                            .count(),
                    2);

            assertLogDirectory(masterFile);
            handler.close();
            assertLogDirectory(masterFile);

            // open new handler
            handler = createRollingFileHandler(
                    masterFile.toString(),
                    new DataSize(1, MEGABYTE),
                    new DataSize(10, MEGABYTE),
                    NONE,
                    TEXT.createFormatter(TESTING_ANNOTATIONS),
                    new ErrorManager(),
                    TESTING_FILE_TIME_ROLLER);

            assertLogDirectory(masterFile);
            assertNotEquals(Files.readSymbolicLink(masterFile), firstLogFile);

            handler.publish(new LogRecord(Level.SEVERE, "cherry"));

            lines = waitForExactLines(masterFile, 1);
            assertEquals(lines.size(), 1);
            assertEquals(
                    lines.stream()
                            .filter(line -> line.contains("cherry"))
                            .count(),
                    1);

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
            // open a legacy handler
            Path masterFile = tempDir.resolve("launcher.log");
            LegacyRollingFileHandler legacyHandler = new LegacyRollingFileHandler(masterFile.toString(), 10, new DataSize(1, MEGABYTE).toBytes(), TEXT.createFormatter(TESTING_ANNOTATIONS));

            assertTrue(Files.isRegularFile(masterFile));

            legacyHandler.publish(new LogRecord(Level.SEVERE, "apple"));
            legacyHandler.publish(new LogRecord(Level.SEVERE, "banana"));

            List<String> lines = waitForExactLines(masterFile, 2);
            assertEquals(lines.size(), 2);
            assertEquals(
                    Files.readAllLines(masterFile, UTF_8).stream()
                            .filter(line -> line.contains("apple") || line.contains("banana"))
                            .count(),
                    2);

            legacyHandler.close();

            // open new handler
            BufferedHandler newHandler = createRollingFileHandler(
                    masterFile.toString(),
                    new DataSize(1, MEGABYTE),
                    new DataSize(10, MEGABYTE),
                    NONE,
                    TEXT.createFormatter(TESTING_ANNOTATIONS),
                    new ErrorManager(),
                    TESTING_FILE_TIME_ROLLER);
            assertLogDirectory(masterFile);

            assertTrue(Files.isSymbolicLink(masterFile));
            // should be tracking legacy file and new file
            assertEquals(((RollingFileMessageOutput) newHandler.getMessageOutput()).getFiles().size(), 2);

            newHandler.publish(new LogRecord(Level.SEVERE, "cherry"));
            newHandler.publish(new LogRecord(Level.SEVERE, "date"));

            lines = waitForExactLines(masterFile, 2);
            assertEquals(lines.size(), 2);
            assertEquals(
                    lines.stream()
                            .filter(line -> line.contains("cherry") || line.contains("date"))
                            .count(),
                    2);

            assertLogDirectory(masterFile);
            newHandler.close();
            assertLogDirectory(masterFile);
        }
        finally {
            deleteRecursively(tempDir, ALLOW_INSECURE);
        }
    }

    @Test
    public void testDateRoll()
    {
        assertDateRoll(0);
        assertDateRoll(1_234_567);
        assertDateRoll(-1_234_567);
        assertDateRoll(Long.MIN_VALUE + 1_234_567);
        assertDateRoll(Long.MAX_VALUE - 1_234_567);
    }

    private void assertDateRoll(long initialSystemNanos)
    {
        // use an arbitrary ticker (System.nanoTime())
        TestingTicker ticker = new TestingTicker(initialSystemNanos);

        // use an arbitrary test time
        LocalDateTime testDateTime = LocalDateTime.of(2022, 3, 4, 5, 6, 7, (int) MILLISECONDS.toNanos(89));

        // set system time (System.currentTimeMillis()) to just after test time
        AtomicLong systemTime = new AtomicLong(testDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        systemTime.addAndGet(1234);

        // verify next roll time
        FileTimeRoller fileTimeRoller = new FileTimeRoller(systemTime::get, ticker);
        fileTimeRoller.updateNextRollTime(testDateTime);
        assertFalse(fileTimeRoller.shouldRoll());

        // verify exact roll time
        long nextRollNanos = fileTimeRoller.getNextRollNanos();
        long nextRollSystemMillis = systemTime.get() + NANOSECONDS.toMillis(nextRollNanos - ticker.read());
        LocalDateTime nextRollTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(nextRollSystemMillis), ZoneId.systemDefault());
        assertThat(nextRollTime).isEqualTo(testDateTime.toLocalDate().plusDays(1).atStartOfDay());

        // advance time and verify should role change
        ticker.increment(nextRollNanos - ticker.read() - 1, NANOSECONDS);
        assertFalse(fileTimeRoller.shouldRoll());
        ticker.increment(1, NANOSECONDS);
        assertTrue(fileTimeRoller.shouldRoll());
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

    private static void assertLogDirectory(Path masterFile)
            throws Exception
    {
        assertTrue(Files.isDirectory(masterFile.getParent()));
        assertTrue(Files.isSymbolicLink(masterFile));
        List<Path> logFiles = Files.list(masterFile.getParent())
                .filter(not(masterFile::equals))
                .collect(toImmutableList());
        for (Path logFile : logFiles) {
            assertFalse(Files.isSymbolicLink(logFile));
            assertTrue(Files.isRegularFile(logFile));
            assertTrue(parseHistoryLogFileName(masterFile.getFileName().toString(), logFile.getFileName().toString()).isPresent());
        }
    }

    private static void assertCompression(Path masterFile, BufferedHandler handler, String message, int expectedFileCount, int expectedLineCount, int expectedCompressedSize)
            throws Exception
    {
        Set<LogFileName> compressedFileNames = waitForCompression((RollingFileMessageOutput) handler.getMessageOutput(), expectedFileCount);
        assertEquals(compressedFileNames.size(), expectedFileCount - 1);

        for (LogFileName compressedFileName : compressedFileNames) {
            Path compressedFile = masterFile.resolveSibling(compressedFileName.getFileName());

            assertEquals(Files.size(compressedFile), expectedCompressedSize);

            List<String> lines = new GzippedByteSource(asByteSource(compressedFile))
                    .asCharSource(UTF_8)
                    .readLines();
            assertEquals(lines.size(), expectedLineCount);
            assertTrue(lines.stream().allMatch(message.trim()::equals));
        }
    }

    private static void assertLogSizes(Path masterFile, BufferedHandler handler, int expectedLines, int lineSize, int expectedFileCount)
            throws Exception
    {
        Set<LogFileName> files = waitForExactFiles((RollingFileMessageOutput) handler.getMessageOutput(), expectedFileCount);
        assertEquals(files.size(), expectedFileCount);

        List<String> lines = waitForExactLines(masterFile, expectedLines);
        assertEquals(lines.size(), expectedLines);
        assertEquals(Files.size(masterFile), (long) (expectedLines) * lineSize);
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
