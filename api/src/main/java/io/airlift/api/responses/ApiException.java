package io.airlift.api.responses;

import io.airlift.api.ApiResponse;
import io.airlift.http.client.HttpStatus;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.Optional;

import static io.airlift.api.internals.Mappers.buildStatus;

public final class ApiException
{
    private ApiException() {}

    public static RuntimeException notFound(String resourceName)
    {
        return buildResponse(new ApiNotFound(resourceName, Optional.empty()));
    }

    public static RuntimeException notFound(String resourceName, String description)
    {
        return buildResponse(new ApiNotFound(resourceName, Optional.of(description)));
    }

    public static RuntimeException badRequest()
    {
        return buildResponse(new ApiBadRequest(Optional.empty()));
    }

    public static RuntimeException badRequest(String details)
    {
        return buildResponse(new ApiBadRequest(Optional.of(details)));
    }

    public static RuntimeException internalError(String details)
    {
        return buildResponse(new ApiInternalError(Optional.of(details)));
    }

    public static RuntimeException unauthorized(String details)
    {
        return buildResponse(new ApiUnauthorized(Optional.of(details)));
    }

    public static RuntimeException forbidden(String details)
    {
        return buildResponse(new ApiForbidden(Optional.of(details)));
    }

    public static RuntimeException response(Object response)
    {
        HttpStatus httpStatus = buildStatus(response);
        Response httpResponse = Response.status(httpStatus.code()).entity(response).build();
        return new WebApplicationException(httpResponse);
    }

    private static WebApplicationException buildResponse(Object response)
    {
        ApiResponse apiResponse = Optional.of(response.getClass().getAnnotation(ApiResponse.class))
                .orElseThrow(() -> new IllegalArgumentException("Response class is not annotated with @%s".formatted(ApiResponse.class.getSimpleName())));

        Response httpResponse = Response.status(apiResponse.status().code()).entity(response).build();
        return new WebApplicationException(httpResponse);
    }
}
