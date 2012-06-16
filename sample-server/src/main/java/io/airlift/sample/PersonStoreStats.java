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
package io.airlift.sample;

import io.airlift.event.client.EventClient;
import org.weakref.jmx.Managed;

import java.util.concurrent.atomic.AtomicLong;

public class PersonStoreStats
{
    private final AtomicLong fetched = new AtomicLong();
    private final AtomicLong added = new AtomicLong();
    private final AtomicLong updated = new AtomicLong();
    private final AtomicLong removed = new AtomicLong();
    private final EventClient eventClient;

    public PersonStoreStats(EventClient eventClient)
    {
        this.eventClient = eventClient;
    }

    @Managed
    public long getFetched()
    {
        return fetched.get();
    }

    @Managed
    public long getAdded()
    {
        return added.get();
    }

    @Managed
    public long getUpdated()
    {
        return updated.get();
    }

    @Managed
    public long getRemoved()
    {
        return removed.get();
    }

    public void personFetched()
    {
        fetched.getAndIncrement();
    }

    public void personAdded(String id, Person person)
    {
        added.getAndIncrement();
        eventClient.post(PersonEvent.personAdded(id, person));
    }

    public void personUpdated(String id, Person person)
    {
        updated.getAndIncrement();
        eventClient.post(PersonEvent.personUpdated(id, person));
    }

    public void personRemoved(String id, Person person)
    {
        removed.getAndIncrement();
        eventClient.post(PersonEvent.personRemoved(id, person));
    }
}
