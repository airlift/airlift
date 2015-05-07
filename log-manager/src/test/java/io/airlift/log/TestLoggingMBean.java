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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

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
        assertEquals(logging.getRootLevel(), "INFO");

        logging.setRootLevel("WARN");
        assertEquals(logging.getRootLevel(), "WARN");

        logging.setRootLevel("INFO");
        assertEquals(logging.getRootLevel(), "INFO");
    }

    @Test
    public void testGetAndSetNonExisting()
    {
        assertEquals(logging.getRootLevel(), "INFO");

        String name = "this.logger.does.not.exist.yet.Bogus";
        assertFalse(logging.getAllLevels().containsKey(name));
        assertEquals(logging.getLevel(name), "INFO");
        logging.setLevel(name, "WARN");
        assertEquals(logging.getLevel(name), "WARN");
        assertTrue(logging.getAllLevels().containsKey(name));

        assertEquals(logging.getRootLevel(), "INFO");
    }

    @Test
    public void testSetInvalidLevel()
    {
        assertEquals(logging.getRootLevel(), "INFO");
        try {
            logging.setRootLevel("FOO");
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
        }
        assertEquals(logging.getRootLevel(), "INFO");
    }
}
