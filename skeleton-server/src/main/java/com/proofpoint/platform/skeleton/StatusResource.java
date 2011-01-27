package com.proofpoint.platform.skeleton;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@Path("/v1/status")
public class StatusResource
{
    @GET
    public Response get()
    {
        return Response.ok().build();
    }
}
