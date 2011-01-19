package com.proofpoint.sample;

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
        persons = new MapMaker()
                .expiration((long) config.getTtl().toMillis(), TimeUnit.MILLISECONDS)
                .makeMap();
    }

    public Person get(String id)
    {
        return persons.get(id);
    }

    public void put(String id, Person person)
    {
        persons.put(id, person);
    }

    public void delete(String id)
    {
        persons.remove(id);
    }

    public Collection<Person> getAll()
    {
        return persons.values();
    }
}
