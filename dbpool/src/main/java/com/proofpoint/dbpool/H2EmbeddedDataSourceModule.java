package com.proofpoint.dbpool;

import static com.proofpoint.configuration.ConfigurationModule.bindConfig;
import com.google.inject.Provider;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.ProvisionException;
import com.google.common.collect.ImmutableList;
import org.weakref.jmx.guice.MBeanModule;

import javax.sql.DataSource;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Collections;

public class H2EmbeddedDataSourceModule extends MBeanModule
{
    private final Class<? extends Annotation> annotation;
    private final List<Class<? extends Annotation>> aliases;
    private final String propertyPrefix;

    public H2EmbeddedDataSourceModule(String propertyPrefix, Class<? extends Annotation> annotation, Class<? extends Annotation>... aliases)
    {
        if (annotation == null) {
            throw new NullPointerException("annotation is null");
        }
        if (propertyPrefix == null) {
            throw new NullPointerException("propertyPrefix is null");
        }
        if (propertyPrefix.isEmpty()) {
            throw new IllegalArgumentException("propertyPrefix is empty");
        }
        this.annotation = annotation;
        this.propertyPrefix = propertyPrefix;
        if (aliases != null) {
            this.aliases = ImmutableList.copyOf(aliases);
        } else {
            this.aliases = Collections.emptyList();
        }
    }

    @Override
    public void configureMBeans()
    {
        // bind the configuration
        bindConfig(binder()).annotatedWith(annotation).prefixedWith(propertyPrefix).to(H2EmbeddedDataSourceConfig.class);

        // Bind the datasource
        bind(DataSource.class).annotatedWith(annotation).toProvider(new H2EmbeddedDataSourceProvider(annotation)).in(Scopes.SINGLETON);
        export(DataSource.class).annotatedWith(annotation).withGeneratedName();

        // Bind aliases
        Key<DataSource> key = Key.get(DataSource.class, annotation);
        for (Class<? extends Annotation> alise : aliases) {
            bind(DataSource.class).annotatedWith(alise).to(key);
        }
    }

    private static class H2EmbeddedDataSourceProvider implements Provider<H2EmbeddedDataSource>
    {
        private final Class<? extends Annotation> annotation;
        private Injector injector;

        private H2EmbeddedDataSourceProvider(Class<? extends Annotation> annotation)
        {
            this.annotation = annotation;
        }

        @Inject
        public void setInjector(Injector injector) {

            this.injector = injector;
        }
        @Override
        public H2EmbeddedDataSource get()
        {
            H2EmbeddedDataSourceConfig config = injector.getInstance(Key.get(H2EmbeddedDataSourceConfig.class, annotation));
            try {
                return new H2EmbeddedDataSource(config);
            }
            catch (Exception e) {
                throw new ProvisionException("Error creating a H2EmbeddedDataSource", e);
            }
        }
    }
}