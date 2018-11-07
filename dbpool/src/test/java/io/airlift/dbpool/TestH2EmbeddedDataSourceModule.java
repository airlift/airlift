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
package io.airlift.dbpool;

import com.google.common.collect.ImmutableList;
import com.google.inject.BindingAnnotation;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Scopes;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.configuration.ConfigurationModule;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.testing.TestingMBeanServer;

import javax.inject.Inject;
import javax.management.MBeanServer;
import javax.sql.DataSource;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.airlift.testing.Assertions.assertInstanceOf;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;

@Test(singleThreaded = true)
public class TestH2EmbeddedDataSourceModule
{
    @Retention(RUNTIME)
    @BindingAnnotation
    public @interface MainBinding
    {
    }

    @Retention(RUNTIME)
    @BindingAnnotation
    public @interface AliasBinding
    {
    }

    public static class ObjectHolder
    {
        public final DataSource dataSource;

        @Inject
        public ObjectHolder(@MainBinding DataSource dataSource)
        {
            this.dataSource = dataSource;
        }
    }

    public static class TwoObjectsHolder
    {
        public final DataSource mainDataSource;
        public final DataSource aliasedDataSource;

        @Inject
        public TwoObjectsHolder(@MainBinding DataSource mainDataSource, @AliasBinding DataSource aliasedDataSource)
        {
            this.mainDataSource = mainDataSource;
            this.aliasedDataSource = aliasedDataSource;
        }
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullPrefixInConstructionThrows()
    {
        H2EmbeddedDataSourceModule notActuallyConstructed = new H2EmbeddedDataSourceModule(null, MainBinding.class);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullAnnotationInConstructionThrows()
    {
        H2EmbeddedDataSourceModule notActuallyConstructed = new H2EmbeddedDataSourceModule("test", null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testEmptyPrefixStringInConstructionThrows()
    {
        H2EmbeddedDataSourceModule notActuallyConstructed = new H2EmbeddedDataSourceModule("", MainBinding.class);
    }

    @Test(groups = "requiresTempFile")
    public void testObjectBindingFromInjector()
    {
        final String prefix = "test";
        Map<String, String> properties = createDefaultConfigurationProperties(prefix, temporaryFile.getAbsolutePath());

        Injector injector = createInjector(properties, new H2EmbeddedDataSourceModule(prefix, MainBinding.class));

        ObjectHolder objectHolder = injector.getInstance(ObjectHolder.class);

        assertInstanceOf(objectHolder.dataSource, H2EmbeddedDataSource.class);
    }

    @Test(groups = "requiresTempFile")
    public void testBoundObjectIsASingleton()
    {
        final String prefix = "test";
        Map<String, String> properties = createDefaultConfigurationProperties(prefix, temporaryFile.getAbsolutePath());

        Injector injector = createInjector(properties, new H2EmbeddedDataSourceModule(prefix, MainBinding.class));

        ObjectHolder objectHolder1 = injector.getInstance(ObjectHolder.class);
        ObjectHolder objectHolder2 = injector.getInstance(ObjectHolder.class);

        // Holding objects should be different
        assertNotSame(objectHolder1, objectHolder2, "Expected holding objects to be different");

        // But held data source objects should be the same
        assertSame(objectHolder1.dataSource, objectHolder2.dataSource);
    }

    @Test(groups = "requiresTempFile")
    public void testAliasedBindingBindsCorrectly()
    {
        final String prefix = "test";
        Map<String, String> properties = createDefaultConfigurationProperties(prefix, temporaryFile.getAbsolutePath());

        Injector injector = createInjector(properties,
                new H2EmbeddedDataSourceModule(prefix, MainBinding.class, AliasBinding.class),
                binder -> binder.bind(TwoObjectsHolder.class));

        ObjectHolder objectHolder = injector.getInstance(ObjectHolder.class);
        TwoObjectsHolder twoObjectsHolder = injector.getInstance(TwoObjectsHolder.class);

        // Held data source objects should all be of the correct type
        assertInstanceOf(twoObjectsHolder.mainDataSource, H2EmbeddedDataSource.class);
        assertInstanceOf(twoObjectsHolder.aliasedDataSource, H2EmbeddedDataSource.class);

        // And should all be references to the same object
        assertSame(objectHolder.dataSource, twoObjectsHolder.mainDataSource);
        assertSame(objectHolder.dataSource, twoObjectsHolder.aliasedDataSource);
    }

    @Test(groups = "requiresTempFile")
    public void testCorrectConfigurationPrefix()
    {
        final String expectedPrefix = "expected";
        final String otherPrefix = "additional";

        final String propertySuffixToTest = ".db.connections.max";
        final int expectedValue = 1234;

        // Required properties for construction
        Map<String, String> properties = createDefaultConfigurationProperties(expectedPrefix, temporaryFile.getAbsolutePath());

        // Optional property added with two different prefixes, two different values
        properties.put(otherPrefix + propertySuffixToTest, Integer.toString(expectedValue + 5678));
        properties.put(expectedPrefix + propertySuffixToTest, Integer.toString(expectedValue));

        Injector injector = createInjector(properties, new H2EmbeddedDataSourceModule(expectedPrefix, MainBinding.class));

        ObjectHolder objectHolder = injector.getInstance(ObjectHolder.class);

        // Make sure we picked up the value with the expected prefix
        assertInstanceOf(objectHolder.dataSource, H2EmbeddedDataSource.class);

        H2EmbeddedDataSource created = (H2EmbeddedDataSource) objectHolder.dataSource;

        assertEquals(created.getMaxConnections(), expectedValue, "Property value not loaded from correct prefix");
    }

    @Test(groups = "requiresTempFile", expectedExceptions = CreationException.class)
    public void testIncorrectConfigurationPrefixThrows()
    {
        final String configurationPrefix = "configuration";
        final String constructionPrefix = "differentFromConfiguration";

        Map<String, String> properties = createDefaultConfigurationProperties(configurationPrefix, temporaryFile.getAbsolutePath());

        // Will throw because construction will fail due to the incorrect prefixing.
        createInjector(properties, new H2EmbeddedDataSourceModule(constructionPrefix, MainBinding.class));
    }

    private static Injector createInjector(Map<String, String> properties, Module... modules)
    {
        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties);

        // Required to bind a configuration module and an MBean server when binding an H2EmbeddedDataSourceModule
        List<Module> moduleList = ImmutableList.<Module>builder()
                .add(modules)
                .add(new ConfigurationModule(configurationFactory))
                .add(binder -> {
                    binder.bind(MBeanServer.class).to(TestingMBeanServer.class).in(Scopes.SINGLETON);
                    binder.bind(ObjectHolder.class);
                })
                .build();

        return Guice.createInjector(moduleList);
    }

    // Any test that actually instantiates an H2EmbeddedDataSource requires a temporary file to use for the database
    private File temporaryFile;

    @BeforeMethod(groups = "requiresTempFile")
    private void createTempFile()
            throws IOException
    {
        this.temporaryFile = File.createTempFile("h2db-", ".db");
    }

    @AfterMethod(groups = "requiresTempFile", alwaysRun = true)
    private void deleteTempFile()
    {
        this.temporaryFile.delete();
    }

    private static Map<String, String> createDefaultConfigurationProperties(String prefix, String filename)
    {
        Map<String, String> properties = new HashMap<>();

        if (!prefix.endsWith(".")) {
            prefix = prefix + ".";
        }

        properties.put(prefix + "db.filename", filename);
        properties.put(prefix + "db.init-script", "io/airlift/dbpool/h2.ddl");
        properties.put(prefix + "db.cipher", "AES");
        properties.put(prefix + "db.file-password", "filePassword");
        return properties;
    }
}
