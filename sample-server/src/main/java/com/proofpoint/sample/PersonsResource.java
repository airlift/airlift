package com.proofpoint.sample;

import com.google.inject.Inject;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/v1/person")
public class PersonsResource
{
    private final PersonStore store;

    @Inject
    public PersonsResource(PersonStore store)
    {
        this.store = store;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listAll()
    {
        return Response.ok(store.getAll()).build();
    }
}
