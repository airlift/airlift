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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import java.util.Collection;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public class PersonStore
{
    private final ConcurrentMap<String, Person> persons;

    @Inject
    public PersonStore(StoreConfig config)
    {
        requireNonNull(config, "config must not be null");

        Cache<String, Person> personCache = CacheBuilder.newBuilder()
                .expireAfterWrite(config.getTtl().toMillis(), TimeUnit.MILLISECONDS)
                .build();
        persons = personCache.asMap();
    }

    public Person get(String id)
    {
        requireNonNull(id, "id must not be null");

        return persons.get(id);
    }

    /**
     * @return true if the entry was created for the first time
     */
    public boolean put(String id, Person person)
    {
        requireNonNull(id, "id must not be null");
        requireNonNull(person, "person must not be null");

        return persons.put(id, person) == null;
    }

    /**
     * @return true if the entry was removed
     */
    public boolean delete(String id)
    {
        requireNonNull(id, "id must not be null");
        return persons.remove(id) != null;
    }

    public Collection<Person> getAll()
    {
        return ImmutableList.copyOf(persons.values());
    }
}
