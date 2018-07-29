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
package io.airlift.configuration;

import com.google.common.io.Files;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;

import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static io.airlift.configuration.ConfigurationLoader.loadProperties;
import static org.testng.Assert.assertEquals;

public class TestConfigurationLoader
{
    private File tempDir;

    @BeforeClass
    public void setup()
            throws IOException
    {
        tempDir = Files.createTempDir()
                .getCanonicalFile(); // getCanonicalFile needed to get around Issue 365 (http://code.google.com/p/guava-libraries/issues/detail?id=365)
    }

    @AfterClass(alwaysRun = true)
    public void teardown()
            throws IOException
    {
        deleteRecursively(tempDir.toPath(), ALLOW_INSECURE);
    }

    @Test
    public void testLoadsFromSystemProperties()
            throws IOException
    {
        System.setProperty("test", "foo");

        Map<String, String> properties = loadProperties();

        assertEquals(properties.get("test"), "foo");

        System.getProperties().remove("test");
    }

    @Test
    public void testLoadsFromFile()
            throws IOException
    {
        final File file = File.createTempFile("config", ".properties", tempDir);
        PrintStream out = new PrintStream(new FileOutputStream(file));
        try {
            out.print("test: foo");
        }
        catch (Exception e) {
            out.close();
        }

        System.setProperty("config", file.getAbsolutePath());

        Map<String, String> properties = loadProperties();

        assertEquals(properties.get("test"), "foo");
        assertEquals(properties.get("config"), file.getAbsolutePath());

        System.getProperties().remove("config");
    }

    @Test
    public void testSystemOverridesFile()
            throws IOException
    {
        final File file = File.createTempFile("config", ".properties", tempDir);
        PrintStream out = new PrintStream(new FileOutputStream(file));
        try {
            out.println("key1: original");
            out.println("key2: original");
        }
        catch (Exception e) {
            out.close();
        }

        System.setProperty("config", file.getAbsolutePath());
        System.setProperty("key1", "overridden");

        Map<String, String> properties = loadProperties();

        assertEquals(properties.get("config"), file.getAbsolutePath());
        assertEquals(properties.get("key1"), "overridden");
        assertEquals(properties.get("key2"), "original");

        System.getProperties().remove("config");
    }
}
