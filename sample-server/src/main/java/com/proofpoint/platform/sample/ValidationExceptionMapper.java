package com.proofpoint.platform.sample;

import com.google.common.collect.ImmutableList;

import javax.validation.ConstraintViolation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class ValidationExceptionMapper
        implements ExceptionMapper<ValidationException>
{
    @Override
    public Response toResponse(ValidationException e)
    {
        ImmutableList.Builder<String> messages = new ImmutableList.Builder<String>();
        for (ConstraintViolation<?> violation : e.getViolations()) {
            messages.add(violation.getPropertyPath().toString() + " " + violation.getMessage());
        }

        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(messages.build())
                .build();
    }
}
