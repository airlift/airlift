package com.proofpoint.event.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

import java.util.List;

import static com.proofpoint.event.client.EventTypeMetadata.getEventTypeMetadata;

public class EventBinder
{
    public static EventBinder eventBinder(Binder binder)
    {
        return new EventBinder(binder);
    }

    private final Binder binder;

    private EventBinder(Binder binder)
    {
        this.binder = binder;
    }

    public void bindEventClient(Class<?>... types)
    {
        bindGenericEventClient(types);
    }

    public void bindGenericEventClient(Class<?>... eventTypes)
    {
        Preconditions.checkNotNull(eventTypes, "eventTypes is null");
        bindGenericEventClient(ImmutableList.copyOf(eventTypes));
    }

    public void bindGenericEventClient(List<Class<?>> eventTypes)
    {
        Preconditions.checkNotNull(eventTypes, "eventTypes is null");
        Preconditions.checkArgument(!eventTypes.isEmpty(), "eventTypes is empty");

        Binder sourcedBinder = binder.withSource(getCaller());
        Multibinder<EventTypeMetadata<?>> metadataBinder = Multibinder.newSetBinder(binder, new TypeLiteral<EventTypeMetadata<?>>() {});

        // Bind event type metadata and bind any errors into Guice
        for (Class<?> eventType : eventTypes) {
            EventTypeMetadata<?> eventTypeMetadata = getEventTypeMetadata(eventType);
            metadataBinder.addBinding().toInstance(eventTypeMetadata);
            for (String error : eventTypeMetadata.getErrors()) {
                sourcedBinder.addError(error);
            }
        }
    }

    private static StackTraceElement getCaller()
    {
        // find the caller of this class to report source
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        boolean foundThisClass = false;
        for (StackTraceElement element : stack) {
            if (!foundThisClass) {
                if (element.getClassName().equals(EventBinder.class.getName())) {
                    foundThisClass = true;
                }
            }
            else {
                if (!element.getClassName().equals(EventBinder.class.getName())) {
                    return element;
                }

            }
        }
        return null;
    }
}
