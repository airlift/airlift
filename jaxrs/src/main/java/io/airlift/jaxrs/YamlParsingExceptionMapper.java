package io.airlift.jaxrs;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import static com.google.common.base.Throwables.getRootCause;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;

@Provider
public class YamlParsingExceptionMapper
        implements ExceptionMapper<YamlParsingException>
{
    private static final String SERIALIZATION_ERROR_CODE = "YAML_PARSING_ERROR";

    @Override
    public Response toResponse(YamlParsingException e)
    {
        // Errors are returned as JSON regardless of the request format (common convention across various public APIs).
        return Response.status(BAD_REQUEST)
                .type(APPLICATION_JSON_TYPE)
                .entity(new JsonError(SERIALIZATION_ERROR_CODE, getRootCause(e).getMessage()))
                .build();
    }
}
