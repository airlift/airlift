package io.airlift.mcp.internal;

import com.google.inject.Binder;
import com.google.inject.Module;
import io.airlift.mcp.ErrorHandler;
import io.airlift.mcp.McpServer;
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
        binder.bind(InternalMcpServer.class).in(SINGLETON);
        binder.bind(PaginationUtil.class).in(SINGLETON);
        binder.bind(McpServer.class).to(InternalMcpServer.class).in(SINGLETON);

        newSetBinder(binder, Filter.class).addBinding().to(InternalFilter.class).in(SINGLETON);

        newOptionalBinder(binder, ErrorHandler.class).setDefault().to(InternalErrorHandler.class);
    }
}
