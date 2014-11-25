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

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.inject.Binder;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.spi.Message;
import com.proofpoint.configuration.ConfigurationFactoryTest.AnnotatedSetter;
import com.proofpoint.testing.FileUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import static com.proofpoint.configuration.ConfigurationModule.bindConfig;
import static com.proofpoint.testing.Assertions.assertContainsAllOf;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestConfigurationFactoryBuilder
{
    private static final ConfigurationDefaultingModule TEST_DEFAULTING_MODULE = new ConfigurationDefaultingModule()
    {
        @Override
        public Map<String, String> getConfigurationDefaults()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void configure(Binder binder)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString()
        {
            return "testing module";
        }
    };

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

    private static Injector createInjector(ConfigurationFactory configurationFactory, Module module)
    {
        List<Message> messages = new ConfigurationValidator(configurationFactory, null).validate(module);
        return Guice.createInjector(new ConfigurationModule(configurationFactory), module, new ValidationErrorModule(messages));
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
        try (PrintWriter out = new PrintWriter(file, "UTF-8")) {
            out.print("test: f\u014do");
        }

        System.setProperty("config", file.getAbsolutePath());

        final Map<String, String> properties = new ConfigurationFactoryBuilder()
                .withFile(System.getProperty("config"))
                .withSystemProperties()
                .build()
                .getProperties();

        assertEquals(properties.get("test"), "f\u014do");
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

    @Test
    public void testUnusedConfigFromFileThrowsError()
            throws IOException
    {
        final File file = File.createTempFile("config", ".properties", tempDir);
        try (PrintStream out = new PrintStream(new FileOutputStream(file))) {
            out.print("unused: foo");
        }

        System.setProperty("config", file.getAbsolutePath());

        TestMonitor monitor = new TestMonitor();
        final ConfigurationFactory configurationFactory = new ConfigurationFactoryBuilder()
                .withMonitor(monitor)
                .withFile(System.getProperty("config"))
                .withSystemProperties()
                .build();

        System.getProperties().remove("config");

        try {
            createInjector(configurationFactory, new Module()
            {
                @Override
                public void configure(Binder binder)
                {
                    bindConfig(binder).to(AnnotatedSetter.class);
                }
            });

            fail("Expected an exception in object creation due to unused configuration");
        } catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Configuration property 'unused' was not used");
            assertContainsAllOf(e.getMessage(), "Configuration property 'unused' was not used");
        }
    }

    @Test
    public void testUnusedConfigFromModuleDefaultsThrowsError()
            throws IOException
    {
        final File file = File.createTempFile("config", ".properties", tempDir);

        System.setProperty("config", file.getAbsolutePath());

        TestMonitor monitor = new TestMonitor();
        final ConfigurationFactory configurationFactory = new ConfigurationFactoryBuilder()
                .withMonitor(monitor)
                .withModuleDefaults(ImmutableMap.of("unused", "foo"), ImmutableMap.of("unused", TEST_DEFAULTING_MODULE))
                .withFile(System.getProperty("config"))
                .withSystemProperties()
                .build();

        System.getProperties().remove("config");

        try {
            createInjector(configurationFactory, new Module()
            {
                @Override
                public void configure(Binder binder)
                {
                    bindConfig(binder).to(AnnotatedSetter.class);
                }
            });

            fail("Expected an exception in object creation due to unused configuration");
        } catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Configuration property 'unused' was not used");
            assertContainsAllOf(e.getMessage(), "Configuration property 'unused' was not used");
        }
    }

    @Test
    public void testUnusedConfigFromApplicationDefaultsThrowsError()
            throws IOException
    {
        final File file = File.createTempFile("config", ".properties", tempDir);

        System.setProperty("config", file.getAbsolutePath());

        TestMonitor monitor = new TestMonitor();
        final ConfigurationFactory configurationFactory = new ConfigurationFactoryBuilder()
                .withMonitor(monitor)
                .withApplicationDefaults(ImmutableMap.of("unused", "foo"))
                .withFile(System.getProperty("config"))
                .withSystemProperties()
                .build();

        System.getProperties().remove("config");

        try {
            createInjector(configurationFactory, new Module()
            {
                @Override
                public void configure(Binder binder)
                {
                    bindConfig(binder).to(AnnotatedSetter.class);
                }
            });

            fail("Expected an exception in object creation due to unused configuration");
        } catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Configuration property 'unused' was not used");
            assertContainsAllOf(e.getMessage(), "Configuration property 'unused' was not used");
        }
    }

    @Test
    public void testDuplicatePropertiesInFileThrowsError()
            throws IOException
    {
        final File file = File.createTempFile("config", ".properties", tempDir);
        try (PrintStream out = new PrintStream(new FileOutputStream(file))) {
            out.print("string-value: foo\n");
            out.print("string-value: foo");
        }

        System.setProperty("config", file.getAbsolutePath());

        TestMonitor monitor = new TestMonitor();
        final ConfigurationFactory configurationFactory = new ConfigurationFactoryBuilder()
                .withMonitor(monitor)
                .withFile(System.getProperty("config"))
                .withSystemProperties()
                .build();

        System.getProperties().remove("config");

        try {
            createInjector(configurationFactory, new Module()
            {
                @Override
                public void configure(Binder binder)
                {
                    bindConfig(binder).to(AnnotatedSetter.class);
                }
            });

            fail("Expected an exception in object creation due to duplicate configuration");
        } catch (CreationException e) {
            monitor.assertNumberOfErrors(1);
            monitor.assertNumberOfWarnings(0);
            monitor.assertMatchingErrorRecorded("Duplicate configuration property 'string-value' in file " + file.getAbsolutePath());
            assertContainsAllOf(e.getMessage(), "Duplicate configuration property 'string-value' in file " + file.getAbsolutePath());
        }

    }


}
