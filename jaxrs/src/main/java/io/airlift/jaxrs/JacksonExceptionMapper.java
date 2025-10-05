package io.airlift.jaxrs;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import tools.jackson.core.JacksonException;

import static com.google.common.base.Throwables.getRootCause;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;

@Provider
public class JacksonExceptionMapper
        implements ExceptionMapper<JacksonException>
{
    private static final String SERIALIZATION_ERROR_CODE = "JSON_PARSING_ERROR";

    @Override
    public Response toResponse(JacksonException e)
    {
        return Response.status(BAD_REQUEST)
                .type(APPLICATION_JSON_TYPE)
                .entity(new JsonError(SERIALIZATION_ERROR_CODE, getRootCause(e).getMessage()))
                .build();
    }
}
