package com.proofpoint.sample;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import javax.validation.ConstraintViolation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.List;

@Provider
public class ValidationExceptionMapper
    implements ExceptionMapper<ValidationException>
{
    @Override
    public Response toResponse(ValidationException e)
    {
        List<String> messages = Lists.newArrayList(Iterables.transform(e.getViolations(), asString()));

        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(messages)
                .build();
    }

    private Function<ConstraintViolation<?>, String> asString()
    {
        return new Function<ConstraintViolation<?>, String>()
        {
            @Override
            public String apply(ConstraintViolation<?> input)
            {
                return input.getPropertyPath().toString() + " " + input.getMessage();
            }
        };
    }

}
