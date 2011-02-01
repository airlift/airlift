package com.proofpoint.platform.sample;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;

import java.net.URI;

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
        Response response = resource.get("foo", MockUriInfo.from(URI.create("http://localhost/v1/person/1")));
        assertEquals(response.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testGet()
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        Response response = resource.get("foo", MockUriInfo.from(URI.create("http://localhost/v1/person/1")));
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEquals(response.getEntity(), new PersonRepresentation("foo@example.com", "Mr Foo", URI.create("http://localhost/v1/person/1")));
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testGetNull()
    {
        resource.get(null, MockUriInfo.from(URI.create("http://localhost/v1/person/1")));
    }

    @Test
    public void testAdd()
    {
        Response response = resource.put("foo", new PersonRepresentation("foo@example.com", "Mr Foo", null));

        assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());
        assertNull(response.getEntity());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        assertEquals(store.get("foo"), new Person("foo@example.com", "Mr Foo"));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testPutNullId()
    {
        resource.put(null, new PersonRepresentation("foo@example.com", "Mr Foo", null));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testPutNullValue()
    {
        resource.put("foo", null);
    }

    @Test
    public void testReplace()
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        Response response = resource.put("foo", new PersonRepresentation("bar@example.com", "Mr Bar", null));

        assertEquals(response.getStatus(), Response.Status.NO_CONTENT.getStatusCode());
        assertNull(response.getEntity());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        assertEquals(store.get("foo"), new Person("bar@example.com", "Mr Bar"));
    }

    @Test
    public void testDelete()
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        Response response = resource.delete("foo");
        assertEquals(response.getStatus(), Response.Status.NO_CONTENT.getStatusCode());
        assertNull(response.getEntity());

        assertNull(store.get("foo"));
    }

    @Test
    public void testDeleteMissing()
    {
        Response response = resource.delete("foo");
        assertEquals(response.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
        assertNull(response.getEntity());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testDeleteNullId()
    {
        resource.delete(null);
    }
}
