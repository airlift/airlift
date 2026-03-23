package io.airlift.mcp.operations;

import com.google.inject.Binder;
import com.google.inject.Module;
import io.airlift.mcp.ErrorHandler;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;

public class OperationsModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(LegacyVersionsController.class).in(SINGLETON);
        binder.bind(OperationsCommon.class).in(SINGLETON);
        newOptionalBinder(binder, ErrorHandler.class).setDefault().to(ErrorHandlerImpl.class);

        binder.bind(LegacyCancellationController.class).in(SINGLETON);
    }
}
