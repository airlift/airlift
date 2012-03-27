package com.proofpoint.http.client;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scopes;

import javax.annotation.PreDestroy;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.proofpoint.configuration.ConfigurationModule.bindConfig;
import static java.util.concurrent.Executors.newFixedThreadPool;

@Beta
public class AsyncHttpClientModule implements Module
{
    private final String name;
    private final Class<? extends Annotation> annotation;
    private final List<Class<? extends Annotation>> aliases;

    public AsyncHttpClientModule(Class<? extends Annotation> annotation, Class<? extends Annotation>... aliases)
    {
        this(checkNotNull(annotation, "annotation is null").getSimpleName(), annotation, aliases);
    }

    public AsyncHttpClientModule(String name, Class<? extends Annotation> annotation, Class<? extends Annotation>... aliases)
    {
        checkNotNull(name, "name is null");
        checkNotNull(annotation, "annotation is null");
        this.annotation = annotation;
        this.name = name;

        if (aliases != null) {
            this.aliases = ImmutableList.copyOf(aliases);
        }
        else {
            this.aliases = Collections.emptyList();
        }
    }

    @Override
    public void configure(Binder binder)
    {
        bindConfig(binder).annotatedWith(annotation).prefixedWith(name).to(HttpClientConfig.class);
        bindConfig(binder).annotatedWith(annotation).prefixedWith(name).to(AsyncHttpClientConfig.class);

        binder.bind(AsyncHttpClient.class).annotatedWith(annotation).toProvider(new HttpClientProvider(annotation)).in(Scopes.SINGLETON);
        binder.bind(ExecutorService.class).annotatedWith(annotation).toProvider(new ExecutorServiceProvider(name, annotation)).in(Scopes.SINGLETON);

        Key<AsyncHttpClient> key = Key.get(AsyncHttpClient.class, annotation);
        for (Class<? extends Annotation> alias : aliases) {
            binder.bind(AsyncHttpClient.class).annotatedWith(alias).to(key);
        }
    }

    private static class ExecutorServiceProvider implements Provider<ExecutorService>
    {
        private final String name;
        private final Class<? extends Annotation> annotation;
        private ExecutorService executorService;
        private Injector injector;

        private ExecutorServiceProvider(String name, Class<? extends Annotation> annotation)
        {
            this.name = name;
            this.annotation = annotation;
        }

        @PreDestroy
        public void stop()
        {
            if (executorService != null) {
                executorService.shutdownNow();
                executorService = null;
            }
        }

        @Inject
        public void setInjector(Injector injector)
        {

            this.injector = injector;
        }

        @Override
        public ExecutorService get()
        {
            if (executorService == null) {
                AsyncHttpClientConfig asyncConfig = injector.getInstance(Key.get(AsyncHttpClientConfig.class, annotation));
                executorService = newFixedThreadPool(asyncConfig.getWorkerThreads(), new ThreadFactoryBuilder().setDaemon(true).setNameFormat(name + "-http-client-%s").build());
            }
            return executorService;
        }
    }

    private static class HttpClientProvider implements Provider<AsyncHttpClient>
    {
        private final Class<? extends Annotation> annotation;
        private Injector injector;

        private HttpClientProvider(Class<? extends Annotation> annotation)
        {
            this.annotation = annotation;
        }

        @Inject
        public void setInjector(Injector injector)
        {

            this.injector = injector;
        }

        @Override
        public AsyncHttpClient get()
        {
            ExecutorService executorService = injector.getInstance(Key.get(ExecutorService.class, annotation));

            HttpClientConfig config = injector.getInstance(Key.get(HttpClientConfig.class, annotation));
            ApacheHttpClient httpClient = new ApacheHttpClient(config);

            return new AsyncHttpClient(httpClient, executorService);
        }
    }
}
