package com.proofpoint.http.client;

import com.google.inject.Binder;
import com.google.inject.Module;

import java.lang.annotation.Annotation;

import static com.google.common.base.Preconditions.checkNotNull;

abstract class AbstractHttpClientModule
        implements Module
{
    protected final String name;
    protected final Class<? extends Annotation> annotation;
    protected Binder binder;

    protected AbstractHttpClientModule(String name, Class<? extends Annotation> annotation)
    {
        this.name = checkNotNull(name, "name is null");
        this.annotation = checkNotNull(annotation, "annotation is null");
    }

    @Override
    public final void configure(Binder binder)
    {
        this.binder = binder;
        configure();
    }

    public abstract void configure();

    public abstract void addAlias(Class<? extends Annotation> alias);

    public abstract Annotation getFilterQualifier();
}
