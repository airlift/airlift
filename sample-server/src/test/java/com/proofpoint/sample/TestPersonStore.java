package com.proofpoint.sample;

import com.proofpoint.stats.Duration;
import com.proofpoint.testing.Assertions;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestPersonStore
{
    @Test
    public void testStartsEmpty()
    {
        PersonStore store = new PersonStore(new StoreConfig());
        assertTrue(store.getAll().isEmpty());
    }

    @Test
    public void testTtl()
            throws InterruptedException
    {
        StoreConfig config = new StoreConfig();
        config.setTtl(new Duration(1, TimeUnit.MILLISECONDS));

        PersonStore store = new PersonStore(config);
        store.put("foo", new Person("foo@example.com", "Mr Foo"));
        Thread.sleep(2);
        Assert.assertNull(store.get("foo"));
    }

    @Test
    public void testPut()
    {
        PersonStore store = new PersonStore(new StoreConfig());
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        assertEquals(new Person("foo@example.com", "Mr Foo"), store.get("foo"));
        assertEquals(store.getAll().size(), 1);
    }

    @Test
    public void testIdempotentPut()
    {
        PersonStore store = new PersonStore(new StoreConfig());
        store.put("foo", new Person("foo@example.com", "Mr Foo"));
        store.put("foo", new Person("foo@example.com", "Mr Bar"));

        assertEquals(new Person("foo@example.com", "Mr Bar"), store.get("foo"));
        assertEquals(store.getAll().size(), 1);
    }

    @Test
    public void testDelete()
    {
        PersonStore store = new PersonStore(new StoreConfig());
        store.put("foo", new Person("foo@example.com", "Mr Foo"));
        store.delete("foo");

        assertNull(store.get("foo"));
        assertTrue(store.getAll().isEmpty());
    }

    @Test
    public void testIdempotentDelete()
    {
        PersonStore store = new PersonStore(new StoreConfig());
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        store.delete("foo");
        assertTrue(store.getAll().isEmpty());
        assertNull(store.get("foo"));

        store.delete("foo");
        assertTrue(store.getAll().isEmpty());
        assertNull(store.get("foo"));
    }

    @Test
    public void testGetAll()
    {
        PersonStore store = new PersonStore(new StoreConfig());

        store.put("foo", new Person("foo@example.com", "Mr Foo"));
        store.put("bar", new Person("bar@example.com", "Mr Bar"));

        assertEquals(store.getAll().size(), 2);
        assertEquals(store.getAll(), asList(new Person("foo@example.com", "Mr Foo"), new Person("bar@example.com", "Mr Bar")));
    }

}
