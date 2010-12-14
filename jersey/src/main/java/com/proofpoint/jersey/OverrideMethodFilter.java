package com.proofpoint.jersey;

import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

/**
 * Clients may override the HTTP method by setting either the X-HTTP-Method-Override header or the _method form or query
 * parameter in a POST or GET request.  If both the X-HTTP-Method-Override header and _method parameter are present in
 * the request then the X-HTTP-Method-Override header will be used.
 */
public class OverrideMethodFilter
        implements ContainerRequestFilter
{
    /**
     * The name of HTTP request header that overrides the HTTP method.
     */
    public static final String HEADER = "X-HTTP-Method-Override";

    /**
     * The name of uri query parameter that overrides the HTTP method.
     */
    public static final String METHOD_PARAM = "_method";

    public ContainerRequest filter(ContainerRequest request)
    {
        String method = request.getRequestHeaders().getFirst(HEADER);
        if (method == null || method.equals("")) {
            method = request.getQueryParameters().getFirst(METHOD_PARAM);
        }

        if (method != null && !method.equals("")) {
            if (request.getMethod().equalsIgnoreCase("POST")) {
                request.setMethod(method);
            }
            else {
                // TODO: how do we return a response message? how to we format the response or control
                // TODO: content-type for the error message
                throw new WebApplicationException(Response.Status.BAD_REQUEST);
            }
        }

        return request;
    }
}
