package io.airlift.mcp.session.memory;

import com.google.inject.Binder;
import com.google.inject.Module;
import io.airlift.mcp.session.McpSessionController;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.configuration.ConfigBinder.configBinder;

public class McpMemorySessionModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        newOptionalBinder(binder, McpSessionController.class).setBinding().to(MemorySessionController.class).in(SINGLETON);
        configBinder(binder).bindConfig(MemorySessionConfig.class);
    }
}
