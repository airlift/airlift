package com.proofpoint.platform.sample;

import com.google.common.base.Preconditions;
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
import javax.ws.rs.core.UriBuilder;

@Path("/v1/person/{id: \\w+}")
public class PersonResource
{
    private final PersonStore store;

    @Inject
    public PersonResource(PersonStore store)
    {
        Preconditions.checkNotNull(store, "store must not be null");

        this.store = store;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@PathParam("id") String id)
    {
        Preconditions.checkNotNull(id, "id must not be null");

        Person person = store.get(id);

        if (person == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(person).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(@PathParam("id") String id, Person person)
    {
        Preconditions.checkNotNull(id, "id must not be null");
        Preconditions.checkNotNull(person, "person must not be null");

        boolean added = store.put(id, person);
        if (added) {
            UriBuilder uri = UriBuilder.fromResource(PersonResource.class);
            return Response.created(uri.build(id))
                            .build();
        }

        return Response.noContent().build();
    }

    @DELETE
    public Response delete(@PathParam("id") String id)
    {
        Preconditions.checkNotNull(id, "id must not be null");

        if (!store.delete(id)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.noContent().build();
    }
}
