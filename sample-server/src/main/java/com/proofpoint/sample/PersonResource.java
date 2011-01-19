package com.proofpoint.sample;

import com.google.inject.Inject;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/v1/person/{id}")
public class PersonResource
{
    private final PersonStore store;

    @Inject
    public PersonResource(PersonStore store)
    {
        this.store = store;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@PathParam("id") String id)
    {
        Person person = store.get(id);

        if (person == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(person).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(@PathParam("id") String id, Person updated)
    {
        store.put(id, updated);

        return Response.ok().build();
    }

    @DELETE
    public Response delete(@PathParam("id") String id)
    {
        store.delete(id);

        return Response.ok().build();
    }
}
