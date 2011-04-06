package com.proofpoint.experimental.event.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Key;

import java.util.List;

import static com.proofpoint.experimental.event.client.EventTypeMetadata.getEventTypeMetadata;

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

        // Build event type metadata and bind any errors into Guice
        ImmutableList.Builder<EventTypeMetadata<?>> builder = ImmutableList.builder();
        for (Class<?> eventType : eventTypes) {
            EventTypeMetadata<?> eventTypeMetadata = getEventTypeMetadata(eventType);
            builder.add(eventTypeMetadata);
            for (String error : eventTypeMetadata.getErrors()) {
                sourcedBinder.addError(error);
            }
        }
        EventClientProvider eventClientProvider = new EventClientProvider(builder.build());

        // create a valid key
        Key<EventClient> key = Key.get(EventClient.class);

        // bind the event client provider
        sourcedBinder.bind(key).toProvider(eventClientProvider);
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
