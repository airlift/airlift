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

import com.google.common.collect.ImmutableList;

import javax.annotation.concurrent.GuardedBy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class InMemoryEventClient
        extends AbstractEventClient
{
    @GuardedBy("this")
    private final List<Object> events = new ArrayList<>();

    @Override
    protected synchronized <T> void postEvent(T event)
            throws IOException
    {
        events.add(event);
    }

    public synchronized List<Object> getEvents()
    {
        return ImmutableList.copyOf(events);
    }
}
