package com.proofpoint.dbpool;

import static com.proofpoint.configuration.ConfigurationModule.bindConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scopes;
import org.weakref.jmx.guice.MBeanModule;

import javax.sql.DataSource;
import java.lang.annotation.Annotation;

public class MySqlDataSourceModule extends MBeanModule
{
    private final Class<? extends Annotation> annotation;
    private final String propertyPrefix;

    public MySqlDataSourceModule(Class<? extends Annotation> annotation, String propertyPrefix)
    {
        if (annotation == null) {
            throw new NullPointerException("annotation is null");
        }
        if (propertyPrefix == null) {
            throw new NullPointerException("propertyPrefix is null");
        }
        this.annotation = annotation;
        this.propertyPrefix = propertyPrefix;
    }

    @Override
    public void configureMBeans()
    {
        // bind the configuration
        bindConfig(binder()).annotatedWith(annotation).prefixedWith(propertyPrefix).to(MySqlDataSourceConfig.class); 

        // Bind the datasource
        bind(DataSource.class).annotatedWith(annotation).toProvider(new MySqlDataSourceProvider(annotation)).in(Scopes.SINGLETON);
        export(DataSource.class).annotatedWith(annotation).withGeneratedName();
    }

    private static class MySqlDataSourceProvider implements Provider<MySqlDataSource>
    {
        private final Class<? extends Annotation> annotation;
        private Injector injector;

        private MySqlDataSourceProvider(Class<? extends Annotation> annotation)
        {
            this.annotation = annotation;
        }

        @Inject
        public void setInjector(Injector injector) {

            this.injector = injector;
        }
        @Override
        public MySqlDataSource get()
        {
            MySqlDataSourceConfig config = injector.getInstance(Key.get(MySqlDataSourceConfig.class, annotation));
            return new MySqlDataSource(config);
        }
    }
}
