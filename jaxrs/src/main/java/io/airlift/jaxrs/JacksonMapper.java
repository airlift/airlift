package io.airlift.jaxrs;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.PropertyBindingException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

public class JacksonMapper
        implements ExceptionMapper<JacksonException>
{
    @Override
    public Response toResponse(JacksonException exception)
    {
        return switch (exception) {
            // User errors
            case StreamReadException streamReadException -> Response.status(Response.Status.BAD_REQUEST)
                    .entity(formatErrorMessage(streamReadException, "Could not read JSON value")).build();
            case PropertyBindingException propertyBindingException -> Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(formatErrorMessage(propertyBindingException, "Could not bind JSON value")).build();
            // Server errors
            case StreamWriteException streamWriteException -> Response.status(Response.Status.BAD_REQUEST)
                    .entity(formatErrorMessage(streamWriteException, "Could not write JSON value")).build();
            case JsonMappingException mappingException -> Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Could not map JSON value: " + mappingException.getMessage()).build();
            default -> Response.serverError().entity(exception.getMessage()).build();
        };
    }

    private static String formatErrorMessage(JsonProcessingException e, String message)
    {
        return "%s: %s at location %s".formatted(message, e.getOriginalMessage(), e.getLocation());
    }
}
