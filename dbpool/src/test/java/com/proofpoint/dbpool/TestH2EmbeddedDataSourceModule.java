package com.proofpoint.dbpool;

import com.google.inject.Binder;
import com.google.inject.BindingAnnotation;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import org.testng.annotations.Test;
import org.testng.internal.annotations.ParametersAnnotation;

import javax.management.MBeanServer;
import javax.sql.DataSource;
import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertNotSame;
import static org.testng.AssertJUnit.assertSame;


public class TestH2EmbeddedDataSourceModule
{
    @Retention(RUNTIME)
    @Target({FIELD, PARAMETER, METHOD})
    @BindingAnnotation
    public @interface MainBinding
    {
    }

    @Retention(RUNTIME)
    @Target({FIELD, PARAMETER, METHOD})
    @BindingAnnotation
    public @interface AliasBinding
    {
    }

    public static class ObjectHolder
    {
        public final DataSource dataSource;

        @Inject
        public ObjectHolder (@MainBinding DataSource dataSource)
        {
            this.dataSource = dataSource;
        }
    }

    public static class TwoObjectsHolder
    {
        public final DataSource mainDataSource;
        public final DataSource aliasedDataSource;

        @Inject
        public TwoObjectsHolder (@MainBinding DataSource mainDataSource, @AliasBinding DataSource aliasedDataSource)
        {
            this.mainDataSource = mainDataSource;
            this.aliasedDataSource = aliasedDataSource;
        }
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullPrefixInConstructionThrows()
    {
        Injector injector = Guice.createInjector(new H2EmbeddedDataSourceModule(null, MainBinding.class));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullAnnotationInConstructionThrows()
    {
        Injector injector = Guice.createInjector(new H2EmbeddedDataSourceModule("test", null));
    }

    @Test
    public void testInjectorConstruction()
    {
        Map<String, String> properties = new HashMap<String, String>();

        // Properties aren't actually used until we try to instantiate an H2EmbeddedDataSource
        Injector injector = createModuleInjectorWithProperties(new H2EmbeddedDataSourceModule("test", MainBinding.class), properties);
    }

    @Test
    public void testObjectBindingFromInjector()
            throws Exception
    {
        String fileName = File.createTempFile("h2db-", ".db").getAbsolutePath();

        Map<String,String> properties = createDefaultConfigurationProperties(fileName);

        try
        {
            Injector injector = createModuleInjectorWithProperties(new H2EmbeddedDataSourceModule("", MainBinding.class), properties);

            ObjectHolder objectHolder = injector.getInstance(ObjectHolder.class);

            assert objectHolder.dataSource instanceof H2EmbeddedDataSource : "Expected H2EmbeddedDataSource to be injected";
        }
        finally
        {
            new File(fileName).delete();
        }
    }

    @Test
    public void testBoundObjectIsASingleton()
        throws Exception
    {
        String fileName = File.createTempFile("h2db-", ".db").getAbsolutePath();

        Map<String,String> properties = createDefaultConfigurationProperties(fileName);

        try
        {
            Injector injector = createModuleInjectorWithProperties(new H2EmbeddedDataSourceModule("", MainBinding.class), properties);

            ObjectHolder objectHolder1 = injector.getInstance(ObjectHolder.class);
            ObjectHolder objectHolder2 = injector.getInstance(ObjectHolder.class);

            // Holding objects should be different
            assertNotSame(objectHolder1, objectHolder2, "Expected holding objects to be different");

            // But held data source objects should be the same
            assertSame(objectHolder1.dataSource, objectHolder2.dataSource);
        }
        finally
        {
            new File(fileName).delete();
        }
    }

    @Test
    public void testAliasedBindingBindsCorrectly()
        throws Exception
    {
        String fileName = File.createTempFile("h2db-", ".db").getAbsolutePath();

        Map<String,String> properties = createDefaultConfigurationProperties(fileName);

        try
        {
            Injector injector = createModuleInjectorWithProperties(new H2EmbeddedDataSourceModule("", MainBinding.class, AliasBinding.class), properties);

            ObjectHolder objectHolder = injector.getInstance(ObjectHolder.class);
            TwoObjectsHolder twoObjectsHolder = injector.getInstance(TwoObjectsHolder.class);

            // Held data source objects should all be of the correct type
            assert twoObjectsHolder.mainDataSource instanceof H2EmbeddedDataSource;
            assert twoObjectsHolder.aliasedDataSource instanceof H2EmbeddedDataSource;

            // And should all be references to the same object
            assertSame(objectHolder.dataSource, twoObjectsHolder.mainDataSource);
            assertSame(objectHolder.dataSource, twoObjectsHolder.aliasedDataSource);
        }
        finally
        {
            new File(fileName).delete();
        }
    }

    private static Injector createModuleInjectorWithProperties(H2EmbeddedDataSourceModule module, Map<String, String> properties)
    {
        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties);

        // Required to bind a configuration module and an MBean server when binding an H2EmbeddedDataSourceModule
        Injector injector = Guice.createInjector(module,
                new ConfigurationModule(configurationFactory),
                new Module() {
                    @Override
                    public void configure(Binder binder) {
                        binder.bind(MBeanServer.class).toInstance(mock(MBeanServer.class));
                    }
                });

        return injector;
    }


    private static Map<String, String> createDefaultConfigurationProperties (String filename)
    {
        return createDefaultConfigurationPropertiesWithPrefix("", filename);
    }

    private static Map<String, String> createDefaultConfigurationPropertiesWithPrefix(String prefix, String filename)
    {
        Map<String, String> properties = new HashMap<String, String>();

        properties.put(prefix+"db.filename", filename);
        properties.put(prefix+"db.init-script", "com/proofpoint/dbpool/h2.ddl");
        properties.put(prefix+"db.cipher", "AES");
        properties.put(prefix+"db.file-password", "filePassword");
        return properties;
    }
}

