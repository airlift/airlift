package io.airlift.api.responses;

import io.airlift.api.ApiResponse;
import io.airlift.http.client.HttpStatus;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Optional;

import static io.airlift.api.internals.Mappers.buildStatus;

public final class ApiException
{
    private ApiException() {}

    public static RuntimeException notFound(String message)
    {
        return buildResponse(new ApiNotFound(message, Optional.empty(), Optional.empty()));
    }

    public static RuntimeException notFound(String message, String description)
    {
        return buildResponse(new ApiNotFound(message, Optional.of(description), Optional.empty()));
    }

    public static RuntimeException notFound(String message, String description, List<String> fields)
    {
        return buildResponse(new ApiNotFound(message, Optional.of(description), Optional.of(fields)));
    }

    public static RuntimeException notFound(String message, List<String> fields)
    {
        return buildResponse(new ApiNotFound(message, Optional.empty(), Optional.of(fields)));
    }

    public static RuntimeException badRequest()
    {
        return buildResponse(new ApiBadRequest(Optional.empty(), Optional.empty()));
    }

    public static RuntimeException badRequest(String message)
    {
        return buildResponse(new ApiBadRequest(Optional.of(message), Optional.empty()));
    }

    public static RuntimeException badRequest(String message, List<String> fields)
    {
        return buildResponse(new ApiBadRequest(Optional.of(message), Optional.of(fields)));
    }

    public static RuntimeException internalError(String message)
    {
        return buildResponse(new ApiInternalError(Optional.of(message)));
    }

    public static RuntimeException unauthorized(String message)
    {
        return buildResponse(new ApiUnauthorized(Optional.of(message)));
    }

    public static RuntimeException forbidden(String message)
    {
        return buildResponse(new ApiForbidden(Optional.of(message)));
    }

    public static RuntimeException alreadyExists(String message)
    {
        return buildResponse(new ApiAlreadyExists(Optional.of(message)));
    }

    public static RuntimeException aborted(String message)
    {
        return buildResponse(new ApiAborted(Optional.of(message)));
    }

    public static RuntimeException tooManyRequests(String message)
    {
        return buildResponse(new ApiTooManyRequests(Optional.of(message)));
    }

    public static RuntimeException response(Object apiResponseInstance)
    {
        HttpStatus httpStatus = buildStatus(apiResponseInstance);
        Response httpResponse = Response.status(httpStatus.code()).entity(apiResponseInstance).build();
        return new WebApplicationException(httpResponse);
    }

    private static WebApplicationException buildResponse(Object apiResponseInstance)
    {
        ApiResponse apiResponse = Optional.ofNullable(apiResponseInstance.getClass().getAnnotation(ApiResponse.class))
                .orElseThrow(() -> new IllegalArgumentException("Response class is not annotated with @%s".formatted(ApiResponse.class.getSimpleName())));

        Response httpResponse = Response.status(apiResponse.status().code()).entity(apiResponseInstance).build();
        return new WebApplicationException(httpResponse);
    }
}
