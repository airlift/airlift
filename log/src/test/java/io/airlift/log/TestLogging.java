/*
 * Copyright 2010 Proofpoint, Inc.
 *
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

import com.google.common.io.Files;
import io.airlift.testing.FileUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestLogging
{
    private File tempDir;

    @BeforeMethod
    public void setup()
            throws IOException
    {
        tempDir = createTempDir();
    }

    @AfterMethod
    public void tearDown()
            throws IOException
    {
        FileUtils.deleteRecursively(tempDir);
    }

    @Test
    public void testRecoverTempFiles()
            throws IOException
    {
        LoggingConfiguration    configuration = new LoggingConfiguration();
        configuration.setLogPath(new File(tempDir, "launcher.log").getPath());

        File                    logFile1 = new File(tempDir, "test1.log");
        Files.touch(logFile1);
        File                    logFile2 = new File(tempDir, "test2.log");
        Files.touch(logFile2);
        File                    tempLogFile1 = new File(tempDir, "temp1.tmp");
        Files.touch(tempLogFile1);
        File                    tempLogFile2 = new File(tempDir, "temp2.tmp");
        Files.touch(tempLogFile2);

        Logging logging = Logging.initialize();
        logging.configure(configuration);

        assertTrue(logFile1.exists());
        assertTrue(logFile2.exists());
        assertFalse(tempLogFile1.exists());
        assertFalse(tempLogFile2.exists());

        assertTrue(new File(tempDir, "temp1.log").exists());
        assertTrue(new File(tempDir, "temp2.log").exists());
    }

    private File createTempDir()
            throws IOException
    {
        File dir = File.createTempFile("temp", "_dir");

        if (!dir.delete()) {
            throw new IOException("Could not delete temp file: " + dir.getAbsolutePath());
        }
        if (!dir.mkdirs()) {
            throw new IOException("Could not create temp dir: " + dir.getAbsolutePath());
        }

        return dir;
    }
}
