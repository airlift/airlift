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
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.io.MoreFiles.asByteSource;
import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static io.airlift.log.RollingFileHandler.CompressionType.GZIP;
import static io.airlift.log.RollingFileHandler.CompressionType.NONE;
import static io.airlift.log.RollingFileHandler.createRollingFileHandler;
import static io.airlift.units.DataSize.Unit.BYTE;
import static io.airlift.units.DataSize.Unit.MEGABYTE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

@Test(timeOut = 5 * 60 * 1000)
public class TestRollingFileHandler
{
    @Test
    public void testBasicLogging()
            throws Exception
    {
        Path tempDir = Files.createTempDirectory("logging-test");
        try {
            Path masterFile = tempDir.resolve("launcher.log");
            RollingFileHandler handler = createRollingFileHandler(masterFile.toString(), new DataSize(1, MEGABYTE), new DataSize(10, MEGABYTE), NONE);

            assertTrue(Files.exists(masterFile));
            assertTrue(Files.isSymbolicLink(masterFile));

            handler.publish(new LogRecord(Level.SEVERE, "apple"));

            List<String> lines = waitForExactLines(masterFile, 1);
            assertEquals(lines.size(), 1);
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
            handler.close();
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
            RollingFileHandler handler = createRollingFileHandler(
                    masterFile.toString(),
                    new DataSize(message.length() * 5, BYTE),
                    new DataSize(message.length() * 2 + message.length() * 5 + message.length() * 5, BYTE), // 2 messages + 2 closed files
                    NONE);

            // use a handler that prints the raw message
            handler.setFormatter(new Formatter()
            {
                @Override
                public String format(LogRecord record)
                {
                    return record.getMessage();
                }
            });

            assertLogSizes(masterFile, handler, 0, message.length(), 1);

            // fill the first file
            for (int i = 0; i < 5; i++) {
                handler.publish(new LogRecord(Level.SEVERE, message));
                assertLogSizes(masterFile, handler, i + 1, message.length(), 1);
            }

            // fill the second file
            for (int i = 0; i < 5; i++) {
                handler.publish(new LogRecord(Level.SEVERE, message));
                assertLogSizes(masterFile, handler, i + 1, message.length(), 2);
            }

            // Log 2 messages to the second file (before pruning)
            for (int i = 0; i < 2; i++) {
                handler.publish(new LogRecord(Level.SEVERE, message));
                assertLogSizes(masterFile, handler, i + 1, message.length(), 3);
            }

            // fill the third (first file is pruned)
            for (int i = 0; i < 3; i++) {
                handler.publish(new LogRecord(Level.SEVERE, message));
                assertLogSizes(masterFile, handler, i + 3, message.length(), 2);
            }

            // Log 2 messages to the forth file (before pruning)
            for (int i = 0; i < 2; i++) {
                handler.publish(new LogRecord(Level.SEVERE, message));
                assertLogSizes(masterFile, handler, i + 1, message.length(), 3);
            }

            // fill the forth (another file is pruned)
            for (int i = 0; i < 3; i++) {
                handler.publish(new LogRecord(Level.SEVERE, message));
                assertLogSizes(masterFile, handler, i + 3, message.length(), 2);
            }

            handler.close();
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
            RollingFileHandler handler = createRollingFileHandler(
                    masterFile.toString(),
                    new DataSize(message.length() * 5, BYTE),
                    new DataSize(message.length() + message.length() * 5 + expectedCompressedSize, BYTE), // one message, one uncompressed file, one compressed file
                    GZIP);

            // use a handler that prints the raw message
            handler.setFormatter(new Formatter()
            {
                @Override
                public String format(LogRecord record)
                {
                    return record.getMessage();
                }
            });

            assertLogSizes(masterFile, handler, 0, message.length(), 1);

            // fill the first file
            for (int i = 0; i < 5; i++) {
                handler.publish(new LogRecord(Level.SEVERE, message));
                assertLogSizes(masterFile, handler, i + 1, message.length(), 1);
            }

            // log one more message which will trigger roll and compression
            handler.publish(new LogRecord(Level.SEVERE, message));
            assertCompression(masterFile, handler, message, 2, 5, expectedCompressedSize);

            // fill the second file
            for (int i = 0; i < 4; i++) {
                handler.publish(new LogRecord(Level.SEVERE, message));
                // sizes can't really be tested while logging due to compression
            }
            assertCompression(masterFile, handler, message, 2, 5, expectedCompressedSize);

            // log one more message which will trigger roll and compression
            handler.publish(new LogRecord(Level.SEVERE, message));
            assertCompression(masterFile, handler, message, 3, 5, expectedCompressedSize);

            // fill the third file
            for (int i = 0; i < 4; i++) {
                handler.publish(new LogRecord(Level.SEVERE, message));
                // sizes can't really be tested while logging due to compression
            }
            // the oldest log file should have been pruned
            assertLogSizes(masterFile, handler, 5, message.length(), 2);
            assertCompression(masterFile, handler, message, 2, 5, expectedCompressedSize);

            handler.close();
        }
        finally {
            deleteRecursively(tempDir, ALLOW_INSECURE);
        }
    }

    private static void assertCompression(Path masterFile, RollingFileHandler handler, String message, int expectedFileCount, int expectedLineCount, int expectedCompressedSize)
            throws Exception
    {
        Set<LogFileName> compressedFileNames = waitForCompression(handler, expectedFileCount);
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

    private static void assertLogSizes(Path masterFile, RollingFileHandler handler, int expectedLines, int lineSize, int expectedFileCount)
            throws Exception
    {
        Set<LogFileName> files = waitForExactFiles(handler, expectedFileCount);
        assertEquals(files.size(), expectedFileCount);

        List<String> lines = waitForExactLines(masterFile, expectedLines);
        assertEquals(lines.size(), expectedLines);
        assertEquals(Files.size(masterFile), (expectedLines) * lineSize);
    }

    @Test
    public void testClosedHandler()
            throws Exception
    {
        Path tempDir = Files.createTempDirectory("logging-test");
        try {
            Path masterFile = tempDir.resolve("launcher.log");
            RollingFileHandler handler = createRollingFileHandler(masterFile.toString(), new DataSize(1, MEGABYTE), new DataSize(10, MEGABYTE), NONE);

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
            RollingFileHandler handler = createRollingFileHandler(masterFile.toString(), new DataSize(1, MEGABYTE), new DataSize(10, MEGABYTE), NONE);

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

            handler.close();

            // open new handler
            handler = createRollingFileHandler(masterFile.toString(), new DataSize(1, MEGABYTE), new DataSize(10, MEGABYTE), NONE);

            assertNotEquals(Files.readSymbolicLink(masterFile), firstLogFile);

            handler.publish(new LogRecord(Level.SEVERE, "cherry"));

            lines = waitForExactLines(masterFile, 1);
            assertEquals(lines.size(), 1);
            assertEquals(
                    lines.stream()
                            .filter(line -> line.contains("cherry"))
                            .count(),
                    1);
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
            LegacyRollingFileHandler legacyHandler = new LegacyRollingFileHandler(masterFile.toString(), 10, new DataSize(1, MEGABYTE).toBytes());

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
            RollingFileHandler newHandler = createRollingFileHandler(masterFile.toString(), new DataSize(1, MEGABYTE), new DataSize(10, MEGABYTE), NONE);

            assertTrue(Files.isSymbolicLink(masterFile));
            // should be tracking legacy file and new file
            assertEquals(newHandler.getFiles().size(), 2);

            newHandler.publish(new LogRecord(Level.SEVERE, "cherry"));
            newHandler.publish(new LogRecord(Level.SEVERE, "date"));

            lines = waitForExactLines(masterFile, 2);
            assertEquals(lines.size(), 2);
            assertEquals(
                    lines.stream()
                            .filter(line -> line.contains("cherry") || line.contains("date"))
                            .count(),
                    2);
        }
        finally {
            deleteRecursively(tempDir, ALLOW_INSECURE);
        }
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

    private static Set<LogFileName> waitForExactFiles(RollingFileHandler fileHandler, int exactCount)
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

    private static Set<LogFileName> waitForCompression(RollingFileHandler fileHandler, int exactCount)
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
}
