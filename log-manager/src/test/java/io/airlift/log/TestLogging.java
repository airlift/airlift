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

import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static java.nio.file.Files.createTempDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;

@TestInstance(PER_CLASS)
@Execution(SAME_THREAD)
public class TestLogging {
    private File tempDir;

    @BeforeEach
    public void setup() throws IOException {
        tempDir = createTempDirectory(null).toFile();
    }

    @AfterEach
    public void tearDown() throws IOException {
        deleteRecursively(tempDir.toPath(), ALLOW_INSECURE);
    }

    @Test
    public void testRecoverTempFiles() throws IOException {
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

        assertThat(logFile1).exists();
        assertThat(logFile2).exists();
        assertThat(tempLogFile1).doesNotExist();
        assertThat(tempLogFile2).doesNotExist();

        assertThat(new File(tempDir, "temp1.log")).exists();
        assertThat(new File(tempDir, "temp2.log")).exists();
    }

    @Test
    public void testPropagatesLevels() {
        Logging logging = Logging.initialize();
        Logger logger = Logger.get("testPropagatesLevels");

        logging.setLevel("testPropagatesLevels", Level.ERROR);
        assertThat(logger.isDebugEnabled()).isFalse();
        assertThat(logger.isInfoEnabled()).isFalse();

        logging.setLevel("testPropagatesLevels", Level.WARN);
        assertThat(logger.isDebugEnabled()).isFalse();
        assertThat(logger.isInfoEnabled()).isFalse();

        logging.setLevel("testPropagatesLevels", Level.INFO);
        assertThat(logger.isDebugEnabled()).isFalse();
        assertThat(logger.isInfoEnabled()).isTrue();

        logging.setLevel("testPropagatesLevels", Level.DEBUG);
        assertThat(logger.isDebugEnabled()).isTrue();
        assertThat(logger.isInfoEnabled()).isTrue();
    }

    @Test
    public void testPropagatesLevelsHierarchical() {
        Logging logging = Logging.initialize();
        Logger logger = Logger.get("testPropagatesLevelsHierarchical.child");

        logging.setLevel("testPropagatesLevelsHierarchical", Level.ERROR);
        assertThat(logger.isDebugEnabled()).isFalse();
        assertThat(logger.isInfoEnabled()).isFalse();

        logging.setLevel("testPropagatesLevelsHierarchical", Level.WARN);
        assertThat(logger.isDebugEnabled()).isFalse();
        assertThat(logger.isInfoEnabled()).isFalse();

        logging.setLevel("testPropagatesLevelsHierarchical", Level.INFO);
        assertThat(logger.isDebugEnabled()).isFalse();
        assertThat(logger.isInfoEnabled()).isTrue();

        logging.setLevel("testPropagatesLevelsHierarchical", Level.DEBUG);
        assertThat(logger.isDebugEnabled()).isTrue();
        assertThat(logger.isInfoEnabled()).isTrue();
    }

    @Test
    public void testChildLevelOverridesParent() {
        Logging logging = Logging.initialize();
        Logger logger = Logger.get("testChildLevelOverridesParent.child");

        logging.setLevel("testChildLevelOverridesParent", Level.DEBUG);
        logging.setLevel("testChildLevelOverridesParent.child", Level.ERROR);
        assertThat(logger.isDebugEnabled()).isFalse();
        assertThat(logger.isInfoEnabled()).isFalse();
    }

    @Test
    public void testClearLevel() {
        Logging logging = Logging.initialize();
        Logger logger = Logger.get("testClearLevel");

        logging.setLevel("testClearLevel", Level.DEBUG);
        assertThat(logger.isDebugEnabled()).isTrue();
        logging.clearLevel("testClearLevel");
        assertThat(logger.isDebugEnabled()).isFalse();
    }
}
