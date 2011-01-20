package com.proofpoint.sample;

import com.google.common.base.Preconditions;
import com.google.common.collect.MapMaker;
import com.google.inject.Inject;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PersonStore
{
    private final Map<String, Person> persons;

    @Inject
    public PersonStore(StoreConfig config)
    {
        Preconditions.checkNotNull(config, "config must not be null");

        persons = new MapMaker()
                .expiration((long) config.getTtl().toMillis(), TimeUnit.MILLISECONDS)
                .makeMap();
    }

    public Person get(String id)
    {
        Preconditions.checkNotNull(id, "id must not be null");

        return persons.get(id);
    }

    public void put(String id, Person person)
    {
        Preconditions.checkNotNull(id, "id must not be null");
        Preconditions.checkNotNull(person, "person must not be null");

        persons.put(id, person);
    }

    public void delete(String id)
    {
        Preconditions.checkNotNull(id, "id must not be null");

        persons.remove(id);
    }

    public Collection<Person> getAll()
    {
        return persons.values();
    }
}
