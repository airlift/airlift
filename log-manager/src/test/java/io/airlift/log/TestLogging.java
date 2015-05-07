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

@Test(singleThreaded = true)
public class TestLogging
{
    private File tempDir;

    @BeforeMethod
    public void setup()
            throws IOException
    {
        tempDir = Files.createTempDir();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown()
            throws IOException
    {
        FileUtils.deleteRecursively(tempDir);
    }

    @Test
    public void testRecoverTempFiles()
            throws IOException
    {
        LoggingConfiguration configuration = new LoggingConfiguration();
        configuration.setLogPath(new File(tempDir, "launcher.log").getPath());

        File logFile1 = new File(tempDir, "test1.log");
        Files.touch(logFile1);
        File logFile2 = new File(tempDir, "test2.log");
        Files.touch(logFile2);
        File tempLogFile1 = new File(tempDir, "temp1.tmp");
        Files.touch(tempLogFile1);
        File tempLogFile2 = new File(tempDir, "temp2.tmp");
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

    @Test
    public void testPropagatesLevels()
            throws Exception
    {
        Logging logging = Logging.initialize();
        Logger logger = Logger.get("testPropagatesLevels");

        logging.setLevel("testPropagatesLevels", Level.ERROR);
        assertFalse(logger.isDebugEnabled());
        assertFalse(logger.isInfoEnabled());

        logging.setLevel("testPropagatesLevels", Level.WARN);
        assertFalse(logger.isDebugEnabled());
        assertFalse(logger.isInfoEnabled());

        logging.setLevel("testPropagatesLevels", Level.INFO);
        assertFalse(logger.isDebugEnabled());
        assertTrue(logger.isInfoEnabled());

        logging.setLevel("testPropagatesLevels", Level.DEBUG);
        assertTrue(logger.isDebugEnabled());
        assertTrue(logger.isInfoEnabled());
    }

    @Test
    public void testPropagatesLevelsHierarchical()
            throws Exception
    {
        Logging logging = Logging.initialize();
        Logger logger = Logger.get("testPropagatesLevelsHierarchical.child");

        logging.setLevel("testPropagatesLevelsHierarchical", Level.ERROR);
        assertFalse(logger.isDebugEnabled());
        assertFalse(logger.isInfoEnabled());

        logging.setLevel("testPropagatesLevelsHierarchical", Level.WARN);
        assertFalse(logger.isDebugEnabled());
        assertFalse(logger.isInfoEnabled());

        logging.setLevel("testPropagatesLevelsHierarchical", Level.INFO);
        assertFalse(logger.isDebugEnabled());
        assertTrue(logger.isInfoEnabled());

        logging.setLevel("testPropagatesLevelsHierarchical", Level.DEBUG);
        assertTrue(logger.isDebugEnabled());
        assertTrue(logger.isInfoEnabled());
    }

    @Test
    public void testChildLevelOverridesParent()
            throws Exception
    {
        Logging logging = Logging.initialize();
        Logger logger = Logger.get("testChildLevelOverridesParent.child");

        logging.setLevel("testChildLevelOverridesParent", Level.DEBUG);
        logging.setLevel("testChildLevelOverridesParent.child", Level.ERROR);
        assertFalse(logger.isDebugEnabled());
        assertFalse(logger.isInfoEnabled());
    }
}
