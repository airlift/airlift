package io.airlift.jfr;

import com.google.inject.Binder;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import io.airlift.configuration.AbstractConfigurationAwareModule;
import jdk.jfr.consumer.RecordedEvent;

import java.util.function.Consumer;

import static com.google.inject.multibindings.MapBinder.newMapBinder;

public class FlightRecorderModule
        extends AbstractConfigurationAwareModule
{
    @Override
    protected void setup(Binder binder)
    {
        newMapBinder(binder, new TypeLiteral<FlightRecorderEvent>(){}, new TypeLiteral<Consumer<RecordedEvent>>() {})
                .permitDuplicates();

        binder.bind(FlightRecorderWatchdog.class).in(Scopes.SINGLETON);
    }
}
