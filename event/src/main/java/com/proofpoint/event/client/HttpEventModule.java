package com.proofpoint.event.client;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.proofpoint.http.client.AsyncHttpClientModule;

import static com.proofpoint.configuration.ConfigurationModule.bindConfig;
import static com.proofpoint.discovery.client.DiscoveryBinder.discoveryBinder;
import static org.weakref.jmx.guice.MBeanModule.newExporter;

public class HttpEventModule implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(JsonEventWriter.class).in(Scopes.SINGLETON);

        binder.bind(EventClient.class).to(HttpEventClient.class).in(Scopes.SINGLETON);
        newExporter(binder).export(EventClient.class).withGeneratedName();
        bindConfig(binder).to(HttpEventClientConfig.class);
        discoveryBinder(binder).bindHttpSelector("event");
        discoveryBinder(binder).bindHttpSelector("collector");

        // bind the http client
        binder.install(new AsyncHttpClientModule("event", ForEventClient.class));

        // Kick off the binding of Set<EventTypeMetadata> in case no events are bound
        Multibinder.newSetBinder(binder, new TypeLiteral<EventTypeMetadata<?>>() {});
    }
}
