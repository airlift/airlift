package io.airlift.mcp.internal;

import com.google.inject.Binder;
import com.google.inject.Module;
import io.airlift.mcp.ErrorHandler;
import io.airlift.mcp.McpEntities;
import io.airlift.mcp.legacy.LegacyEventStreaming;
import jakarta.servlet.Filter;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;

public class InternalMcpModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(InternalEntities.class).in(SINGLETON);
        binder.bind(McpEntities.class).to(InternalEntities.class).in(SINGLETON);
        binder.bind(LegacyEventStreaming.class).to(InternalEventStreaming.class).in(SINGLETON);

        newSetBinder(binder, Filter.class).addBinding().to(InternalFilter.class).in(SINGLETON);

        newOptionalBinder(binder, ErrorHandler.class).setDefault().to(InternalErrorHandler.class);
    }
}
