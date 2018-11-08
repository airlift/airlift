/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.event.client;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

import java.util.List;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.event.client.EventTypeMetadata.getEventTypeMetadata;
import static java.util.Objects.requireNonNull;

@Beta
public class EventBinder
{
    public static EventBinder eventBinder(Binder binder)
    {
        return new EventBinder(binder);
    }

    private final Binder binder;

    private EventBinder(Binder binder)
    {
        this.binder = requireNonNull(binder, "binder is null").skipSources(getClass());
    }

    public void bindEventClient(Class<?>... types)
    {
        bindGenericEventClient(types);
    }

    public void bindGenericEventClient(Class<?>... eventTypes)
    {
        requireNonNull(eventTypes, "eventTypes is null");
        bindGenericEventClient(ImmutableList.copyOf(eventTypes));
    }

    public void bindGenericEventClient(List<Class<?>> eventTypes)
    {
        requireNonNull(eventTypes, "eventTypes is null");
        Preconditions.checkArgument(!eventTypes.isEmpty(), "eventTypes is empty");

        Binder sourcedBinder = binder.withSource(getCaller());
        Multibinder<EventTypeMetadata<?>> metadataBinder = newSetBinder(binder, new TypeLiteral<EventTypeMetadata<?>>() {});

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
