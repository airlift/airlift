package com.proofpoint.jersey;

import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;

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
        if (request.getMethod().equalsIgnoreCase("POST") || request.getMethod().equalsIgnoreCase("GET")) {
            String method = request.getRequestHeaders().getFirst(HEADER);
            if (method == null || method.equals("")) {
                method = request.getQueryParameters().getFirst(METHOD_PARAM);
            }

            if (method != null && !method.equals("")) {
                request.setMethod(method);
            }
        }

        return request;
    }
}
