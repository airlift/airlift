package com.proofpoint.configuration;

import com.google.common.io.Files;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;

import static org.testng.Assert.assertEquals;

public class TestConfigurationLoader
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
        Files.deleteRecursively(tempDir);
    }

    @Test
    public void testLoadsFromSystemProperties()
            throws IOException
    {
        System.setProperty("test", "foo");

        ConfigurationLoader loader = new ConfigurationLoader();
        Map<String, String> properties = loader.loadProperties();

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

        ConfigurationLoader loader = new ConfigurationLoader();
        Map<String, String> properties = loader.loadProperties();

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

        ConfigurationLoader loader = new ConfigurationLoader();
        Map<String, String> properties = loader.loadProperties();

        assertEquals(properties.get("config"), file.getAbsolutePath());
        assertEquals(properties.get("key1"), "overridden");
        assertEquals(properties.get("key2"), "original");

        System.getProperties().remove("config");
    }
}
