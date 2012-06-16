package io.airlift.tracetoken;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;

public class TraceTokenModule
    implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(TraceTokenManager.class).in(Scopes.SINGLETON);
    }
}
