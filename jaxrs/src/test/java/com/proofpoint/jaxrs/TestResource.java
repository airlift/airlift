package com.proofpoint.jaxrs;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;

@Path("/")
public class TestResource
{
    private volatile boolean post;
    private volatile boolean put;
    private volatile boolean get;
    private volatile boolean delete;

    @POST
    public void post()
    {
        post = true;
    }

    @GET
    public boolean get()
    {
        get = true;
        return true;
    }

    @DELETE
    public void delete()
    {
        delete = true;
    }

    @PUT
    public void put()
    {
        put = true;
    }

    public boolean postCalled()
    {
        return post;
    }

    public boolean putCalled()
    {
        return put;
    }

    public boolean getCalled()
    {
        return get;
    }

    public boolean deleteCalled()
    {
        return delete;
    }
}
