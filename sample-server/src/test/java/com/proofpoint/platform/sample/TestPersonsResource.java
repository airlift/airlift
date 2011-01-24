package com.proofpoint.platform.sample;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import java.util.Collection;

import static com.google.common.collect.Lists.newArrayList;
import static com.proofpoint.testing.Assertions.assertInstanceOf;
import static org.testng.Assert.assertEquals;

public class TestPersonsResource
{
    private PersonsResource resource;
    private PersonStore store;

    @BeforeMethod
    public void setup()
    {
        store = new PersonStore(new StoreConfig());
        resource = new PersonsResource(store);
    }

    @Test
    public void testEmpty()
    {
        Response response = resource.listAll();
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertInstanceOf(response.getEntity(), Collection.class);
        assertEquals((Collection<?>) response.getEntity(), newArrayList());
    }

    @Test
    public void testListAll()
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));
        store.put("bar", new Person("bar@example.com", "Mr Bar"));

        Response response = resource.listAll();
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertInstanceOf(response.getEntity(), Collection.class);
        assertEquals((Collection<?>) response.getEntity(), newArrayList(
                new Person("foo@example.com", "Mr Foo"),
                new Person("bar@example.com", "Mr Bar")
        ));
    }


}
