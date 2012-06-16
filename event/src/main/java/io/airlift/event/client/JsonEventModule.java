package com.proofpoint.event.client;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

public class JsonEventModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(JsonEventSerializer.class).in(Scopes.SINGLETON);

        // Kick off the binding of Set<EventTypeMetadata> in case no events are bound
        Multibinder.newSetBinder(binder, new TypeLiteral<EventTypeMetadata<?>>() {});
    }
}
