package io.airlift.mcp.internal;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import io.airlift.mcp.McpEntities;
import jakarta.servlet.Filter;

import java.lang.annotation.Annotation;
import java.util.Optional;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static java.util.Objects.requireNonNull;

public class InternalMcpModule
        implements Module
{
    private final Optional<Class<? extends Annotation>> filterBindingAnnotation;

    public InternalMcpModule()
    {
        this(Optional.empty());
    }

    public InternalMcpModule(Optional<Class<? extends Annotation>> filterBindingAnnotation)
    {
        this.filterBindingAnnotation = requireNonNull(filterBindingAnnotation, "filterBindingAnnotation is null");
    }

    @Override
    public void configure(Binder binder)
    {
        binder.bind(InternalEntities.class).in(SINGLETON);
        binder.bind(McpEntities.class).to(InternalEntities.class).in(SINGLETON);

        newSetBinder(binder, filterBindingAnnotation
                .map(annotation -> Key.get(Filter.class, annotation))
                .orElseGet(() -> Key.get(Filter.class)))
                .addBinding()
                .to(InternalFilter.class)
                .in(SINGLETON);
    }
}
