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
package com.proofpoint.platform.sample;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.inject.Inject;
import com.proofpoint.event.client.EventClient;
import com.proofpoint.stats.Gauge;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;

import java.util.Collection;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

public class PersonStore
{
    private final ConcurrentMap<String, Person> persons;
    private final PersonStoreStats stats;

    @Inject
    public PersonStore(StoreConfig config, EventClient eventClient)
    {
        checkNotNull(config, "config must not be null");
        checkNotNull(eventClient, "eventClient is null");

        Cache<String, Person> personCache = CacheBuilder.newBuilder()
                .expireAfterWrite((long) config.getTtl().toMillis(), TimeUnit.MILLISECONDS)
                .build();
        persons = personCache.asMap();
        stats = new PersonStoreStats(eventClient);
    }

    @Managed
    @Flatten
    public PersonStoreStats getStats()
    {
        return stats;
    }

    @Gauge
    private int getSize()
    {
        return persons.size();
    }

    public Person get(String id)
    {
        checkNotNull(id, "id must not be null");

        Person person = persons.get(id);
        if (person != null) {
            stats.personFetched();
        }
        return person;
    }

    /**
     * @return true if the entry was created for the first time
     */
    public boolean put(String id, Person person)
    {
        checkNotNull(id, "id must not be null");
        checkNotNull(person, "person must not be null");

        boolean added = persons.put(id, person) == null;
        if (added) {
            stats.personAdded(id, person);
        }
        else {
            stats.personUpdated(id, person);
        }
        return added;
    }

    /**
     * @return true if the entry was removed
     */
    public boolean delete(String id)
    {
        checkNotNull(id, "id must not be null");

        Person removedPerson = persons.remove(id);
        if (removedPerson != null) {
            stats.personRemoved(id, removedPerson);
        }

        return removedPerson != null;
    }

    public Collection<StoreEntry> getAll()
    {
        Builder<StoreEntry> builder = ImmutableList.builder();
        for (Entry<String, Person> entry : persons.entrySet()) {
            builder.add(new StoreEntry(entry));
        }
        return builder.build();
    }

    public static class StoreEntry
    {
        private final Entry<String,Person> entry;

        private StoreEntry(Entry<String, Person> entry)
        {
            this.entry = entry;
        }

        public String getId()
        {
            return entry.getKey();
        }

        public Person getPerson()
        {
            return entry.getValue();
        }
    }
}
