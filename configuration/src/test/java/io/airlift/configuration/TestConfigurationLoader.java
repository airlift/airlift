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

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.function.Consumer;

import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static io.airlift.configuration.ConfigurationLoader.loadProperties;
import static java.nio.file.Files.createTempDirectory;
import static org.testng.Assert.assertEquals;

public class TestConfigurationLoader
{
    private File tempDir;

    @BeforeClass
    public void setup()
            throws IOException
    {
        tempDir = createTempDirectory(null).toFile();
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

        System.clearProperty("test");
    }

    @Test
    public void testLoadsFromFile()
            throws IOException
    {
        File file = createConfigFile(out -> out.print("test: foo"));
        System.setProperty("config", file.getAbsolutePath());

        Map<String, String> properties = loadProperties();

        assertEquals(properties.get("test"), "foo");
        assertEquals(properties.get("config"), file.getAbsolutePath());

        System.clearProperty("config");
    }

    @Test
    public void testTrimWhitespaceFromFile()
            throws IOException
    {
        File file = createConfigFile(out -> {
            out.println(" \t trim-whitespace-key1 \t =  \t key1-value \t ");
            out.println(" \t trim-whitespace-key2 \t =  \t key2-value \t ");
        });
        System.setProperty("config", file.getAbsolutePath());
        System.setProperty("trim-whitespace-key2", " \t key2-value \t ");

        Map<String, String> properties = loadProperties();

        // config files are often human-managed and we need to be permissive, stripping trailing whitespace if any
        assertEquals(properties.get("trim-whitespace-key1"), "key1-value");

        // trailing whitespace in JVM property value is not so likely to be unintentional, so preserve it
        assertEquals(properties.get("trim-whitespace-key2"), " \t key2-value \t ");

        System.clearProperty("trim-whitespace-key2");
        System.clearProperty("config");
    }

    @Test
    public void testSystemOverridesFile()
            throws IOException
    {
        File file = createConfigFile(out -> {
            out.println("key1: original");
            out.println("key2: original");
        });
        System.setProperty("config", file.getAbsolutePath());
        System.setProperty("key1", "overridden");

        Map<String, String> properties = loadProperties();

        assertEquals(properties.get("config"), file.getAbsolutePath());
        assertEquals(properties.get("key1"), "overridden");
        assertEquals(properties.get("key2"), "original");

        System.clearProperty("key1");
        System.clearProperty("config");
    }

    private File createConfigFile(Consumer<PrintStream> contentProvider)
            throws IOException
    {
        File file = File.createTempFile("config", ".properties", tempDir);
        try (PrintStream out = new PrintStream(new FileOutputStream(file))) {
            contentProvider.accept(out);
        }
        return file;
    }
}
