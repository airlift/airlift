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
package com.proofpoint.configuration;

import com.google.common.io.Files;
import com.proofpoint.testing.FileUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;

import static org.testng.Assert.assertEquals;

public class TestConfigurationFactoryBuilder
{
    private File tempDir;

    @BeforeMethod
    public void setup()
            throws IOException
    {
        tempDir = Files.createTempDir()
                .getCanonicalFile(); // getCanonicalFile needed to get around Issue 365 (http://code.google.com/p/guava-libraries/issues/detail?id=365)

    }

    @AfterMethod
    public void teardown()
            throws IOException
    {
        FileUtils.deleteRecursively(tempDir);
    }

    @Test
    public void testLoadsFromSystemProperties()
            throws IOException
    {
        System.setProperty("test", "foo");

        final Map<String, String> properties = new ConfigurationFactoryBuilder()
                .withSystemProperties()
                .build()
                .getProperties();

        assertEquals(properties.get("test"), "foo");

        System.getProperties().remove("test");
    }

    @Test
    public void testLoadsFromFile()
            throws IOException
    {
        final File file = File.createTempFile("config", ".properties", tempDir);
        try (PrintStream out = new PrintStream(new FileOutputStream(file))) {
            out.print("test: foo");
        }

        System.setProperty("config", file.getAbsolutePath());

        final Map<String, String> properties = new ConfigurationFactoryBuilder()
                .withFile(System.getProperty("config"))
                .withSystemProperties()
                .build()
                .getProperties();

        assertEquals(properties.get("test"), "foo");
        assertEquals(properties.get("config"), file.getAbsolutePath());

        System.getProperties().remove("config");
    }

    @Test
    public void testSystemOverridesFile()
            throws IOException
    {
        final File file = File.createTempFile("config", ".properties", tempDir);
        try (PrintStream out = new PrintStream(new FileOutputStream(file))) {
            out.println("key1: original");
            out.println("key2: original");
        }

        System.setProperty("config", file.getAbsolutePath());
        System.setProperty("key1", "overridden");

        final Map<String, String> properties = new ConfigurationFactoryBuilder()
                .withFile(System.getProperty("config"))
                .withSystemProperties()
                .build()
                .getProperties();

        assertEquals(properties.get("config"), file.getAbsolutePath());
        assertEquals(properties.get("key1"), "overridden");
        assertEquals(properties.get("key2"), "original");

        System.getProperties().remove("config");
    }
}
