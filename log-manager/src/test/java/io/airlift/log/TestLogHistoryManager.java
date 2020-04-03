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
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static io.airlift.units.DataSize.Unit.BYTE;
import static io.airlift.units.DataSize.Unit.GIGABYTE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.testng.Assert.assertEquals;

public class TestLogHistoryManager
{
    private static final int FILE_SIZE = 100;

    @Test
    public void testInitialLoad()
            throws IOException
    {
        Path tempDir = Files.createTempDirectory("logging-test");
        try {
            Path masterLogName = tempDir.resolve("test");
            List<LogFileName> logFileNames = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                logFileNames.add(createTestFile(masterLogName));
            }
            LogHistoryManager logHistoryManager = new LogHistoryManager(masterLogName, new DataSize(1, GIGABYTE));
            assertLogFiles(logHistoryManager, logFileNames);
        }
        finally {
            deleteRecursively(tempDir, ALLOW_INSECURE);
        }
    }

    @Test
    public void testInitialPrune()
            throws IOException
    {
        Path tempDir = Files.createTempDirectory("logging-test");
        try {
            Path masterLogName = tempDir.resolve("test");
            List<LogFileName> logFileNames = new ArrayList<>();
            for (int i = 0; i < FILE_SIZE; i++) {
                logFileNames.add(createTestFile(masterLogName));
            }
            LogHistoryManager logHistoryManager = new LogHistoryManager(masterLogName, new DataSize(10 * FILE_SIZE, BYTE));
            assertLogFiles(logHistoryManager, logFileNames.subList(90, logFileNames.size()));
        }
        finally {
            deleteRecursively(tempDir, ALLOW_INSECURE);
        }
    }

    private static void assertLogFiles(LogHistoryManager logHistoryManager, List<LogFileName> expected)
    {
        assertEquals(logHistoryManager.getTotalSize(), expected.size() * FILE_SIZE);
        List<LogFileName> files = logHistoryManager.getFiles().stream()
                .sorted()
                .collect(toImmutableList());
        assertEquals(files, expected);
    }

    @Test
    public void testRuntimePrune()
            throws IOException, InterruptedException
    {
        Path tempDir = Files.createTempDirectory("logging-test");
        try {
            Path masterLogName = tempDir.resolve("test");
            List<LogFileName> logFileNames = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                logFileNames.add(createTestFile(masterLogName));
            }
            LogHistoryManager logHistoryManager = new LogHistoryManager(masterLogName, new DataSize(100 * FILE_SIZE, BYTE));
            assertLogFiles(logHistoryManager, logFileNames);

            // add 2 more files
            // sleep to avoid using the filenames that were just deleted
            // TODO come up with better way to do this
            Thread.sleep(2000);
            for (int i = 0; i < 2; i++) {
                LogFileName testFile = createTestFile(masterLogName);
                logHistoryManager.addFile(masterLogName.resolveSibling(testFile.getFileName()), testFile, FILE_SIZE);
                logFileNames.add(testFile);
            }
            assertLogFiles(logHistoryManager, logFileNames);

            // prune the 2 oldest files
            logHistoryManager.pruneLogFilesIfNecessary(0);
            logFileNames = logFileNames.subList(2, logFileNames.size());
            assertLogFiles(logHistoryManager, logFileNames);

            // prune 2 more files by declaring other data size
            logHistoryManager.pruneLogFilesIfNecessary(2 * FILE_SIZE);
            logFileNames = logFileNames.subList(2, logFileNames.size());
            assertLogFiles(logHistoryManager, logFileNames);

            // add 4 more files
            // sleep to avoid using the filenames that were just deleted
            // TODO come up with better way to do this
            Thread.sleep(2000);
            for (int i = 0; i < 4; i++) {
                LogFileName testFile = createTestFile(masterLogName);
                logHistoryManager.addFile(masterLogName.resolveSibling(testFile.getFileName()), testFile, FILE_SIZE);
                logFileNames.add(testFile);
            }
            assertLogFiles(logHistoryManager, logFileNames);

            // prune 2 files normally, and  2 more files by declaring other data size
            logHistoryManager.pruneLogFilesIfNecessary(2 * FILE_SIZE);
            logFileNames = logFileNames.subList(4, logFileNames.size());
            assertLogFiles(logHistoryManager, logFileNames);
        }
        finally {
            deleteRecursively(tempDir, ALLOW_INSECURE);
        }
    }

    private static LogFileName createTestFile(Path masterLogName)
            throws IOException
    {
        LogFileName logFileName = LogFileName.generateNextLogFileName(masterLogName, Optional.empty());
        Files.write(masterLogName.resolveSibling(logFileName.getFileName()), new byte[FILE_SIZE], CREATE_NEW);
        return logFileName;
    }
}
