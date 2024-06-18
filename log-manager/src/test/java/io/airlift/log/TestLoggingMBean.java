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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@Test(singleThreaded = true)
public class TestLoggingMBean
{
    private final LoggingMBean logging = new LoggingMBean(Logging.initialize());
    private String rootLevel;

    @BeforeMethod
    public void setRootLevel()
    {
        rootLevel = logging.getRootLevel();
        logging.setRootLevel("INFO");
    }

    @AfterMethod(alwaysRun = true)
    public void restoreRootLevel()
    {
        logging.setRootLevel(rootLevel);
    }

    @Test
    public void testGetAndSetRoot()
    {
        assertThat(logging.getRootLevel()).isEqualTo("INFO");

        logging.setRootLevel("WARN");
        assertThat(logging.getRootLevel()).isEqualTo("WARN");

        logging.setRootLevel("INFO");
        assertThat(logging.getRootLevel()).isEqualTo("INFO");
    }

    @Test
    public void testGetAndSetNonExisting()
    {
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
    public void testSetInvalidLevel()
    {
        assertThat(logging.getRootLevel()).isEqualTo("INFO");
        try {
            logging.setRootLevel("FOO");
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
        assertThat(logging.getRootLevel()).isEqualTo("INFO");
    }
}
