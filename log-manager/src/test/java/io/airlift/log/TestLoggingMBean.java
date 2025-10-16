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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;

@TestInstance(PER_CLASS)
@Execution(SAME_THREAD)
public class TestLoggingMBean {
    private final LoggingMBean logging = new LoggingMBean(Logging.initialize());
    private String rootLevel;

    @BeforeEach
    public void setRootLevel() {
        rootLevel = logging.getRootLevel();
        logging.setRootLevel("INFO");
    }

    @AfterEach
    public void restoreRootLevel() {
        logging.setRootLevel(rootLevel);
    }

    @Test
    public void testGetAndSetRoot() {
        assertThat(logging.getRootLevel()).isEqualTo("INFO");

        logging.setRootLevel("WARN");
        assertThat(logging.getRootLevel()).isEqualTo("WARN");

        logging.setRootLevel("INFO");
        assertThat(logging.getRootLevel()).isEqualTo("INFO");
    }

    @Test
    public void testGetAndSetNonExisting() {
        assertThat(logging.getRootLevel()).isEqualTo("INFO");

        String name = "this.logger.does.not.exist.yet.Bogus";
        assertThat(logging.getAllLevels()).doesNotContainKey(name);
        assertThat(logging.getLevel(name)).isEqualTo("INFO");
        logging.setLevel(name, "WARN");
        assertThat(logging.getLevel(name)).isEqualTo("WARN");
        assertThat(logging.getAllLevels()).containsKey(name);

        assertThat(logging.getRootLevel()).isEqualTo("INFO");
    }

    @Test
    public void testSetInvalidLevel() {
        assertThat(logging.getRootLevel()).isEqualTo("INFO");
        try {
            logging.setRootLevel("FOO");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        assertThat(logging.getRootLevel()).isEqualTo("INFO");
    }
}
