package io.airlift.api.servertests.exceptions;

import com.google.common.collect.ImmutableList;
import io.airlift.api.ApiCreate;
import io.airlift.api.ApiService;
import io.airlift.api.ApiUpdate;
import io.airlift.api.ServiceType;
import jakarta.ws.rs.WebApplicationException;

import static io.airlift.api.responses.ApiException.badRequest;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.status;

@ApiService(type = ServiceType.class, name = "exception", description = "Throws exceptions")
public class ExceptionApi
{
    @ApiUpdate(description = "Throws a known exception")
    public void throwsKnownException()
    {
        throw badRequest("This is the message", ImmutableList.of("field1", "field2"));
    }

    @ApiCreate(description = "Throws an unknown exception", quotas = "dummy")
    public void throwsUnknownException()
    {
        throw new WebApplicationException(status(INTERNAL_SERVER_ERROR).build());
    }
}
