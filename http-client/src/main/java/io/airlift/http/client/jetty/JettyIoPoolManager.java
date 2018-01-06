package io.airlift.http.client.jetty;

import com.google.inject.Injector;
import com.google.inject.Key;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import java.lang.annotation.Annotation;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

public class JettyIoPoolManager
{
    private final String name;
    private final Class<? extends Annotation> annotation;
    private final AtomicBoolean destroyed = new AtomicBoolean();
    private JettyIoPool pool;
    private Injector injector;
    private JettyHttpClient client;

    public JettyIoPoolManager(String name, Class<? extends Annotation> annotation)
    {
        this.name = requireNonNull(name, "name is null");
        this.annotation = requireNonNull(annotation, "annotation is null");
    }

    public void setClient(JettyHttpClient client)
    {
        this.client = requireNonNull(client, "client is null");
    }

    public boolean isDestroyed()
    {
        return destroyed.get();
    }

    @Inject
    public void setInjector(Injector injector)
    {
        this.injector = injector;
    }

    @PreDestroy
    public void destroy()
    {
        // client must be destroyed before the pools or
        // you will create a several second busy wait loop
        client.close();
        if (pool != null) {
            pool.close();
            pool = null;
        }
        destroyed.set(true);
    }

    public JettyIoPool get()
    {
        if (pool == null) {
            JettyIoPoolConfig config = injector.getInstance(keyFromNullable(JettyIoPoolConfig.class, annotation));
            pool = new JettyIoPool(name, config);
        }
        return pool;
    }

    private static <T> Key<T> keyFromNullable(Class<T> type, Class<? extends Annotation> annotation)
    {
        return (annotation != null) ? Key.get(type, annotation) : Key.get(type);
    }
}
