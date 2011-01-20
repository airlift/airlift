package com.proofpoint.sample;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestPersonResource
{
    private PersonResource resource;
    private PersonStore store;

    @BeforeMethod
    public void setup()
    {
        store = new PersonStore(new StoreConfig());
        resource = new PersonResource(store);
    }

    @Test
    public void testNotFound()
    {
        Response response = resource.get("foo");
        assertEquals(response.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testGet()
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        Response response = resource.get("foo");
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEquals(response.getEntity(), new Person("foo@example.com", "Mr Foo"));
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testGetNull()
    {
        resource.get(null);
    }

    @Test
    public void testAdd()
    {
        Response response = resource.update("foo", new Person("foo@example.com", "Mr Foo"));

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertNull(response.getEntity());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        assertEquals(store.get("foo"), new Person("foo@example.com", "Mr Foo"));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testPutNullId()
    {
        resource.update(null, new Person("foo@example.com", "Mr Foo"));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testPutNullValue()
    {
        resource.update("foo", null);
    }

    @Test
    public void testReplace()
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        Response response = resource.update("foo", new Person("bar@example.com", "Mr Bar"));

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertNull(response.getEntity());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        assertEquals(store.get("foo"), new Person("bar@example.com", "Mr Bar"));
    }

    @Test
    public void testDelete()
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        Response response = resource.delete("foo");
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertNull(response.getEntity());

        assertNull(store.get("foo"));
    }

    @Test
    public void testDeleteMissing()
    {
        Response response = resource.delete("foo");
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertNull(response.getEntity());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testDeleteNullId()
    {
        resource.delete(null);
    }
}
