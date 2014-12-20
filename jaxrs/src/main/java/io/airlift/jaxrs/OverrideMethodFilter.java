/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.jaxrs;

import com.google.common.base.Strings;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * Allows for overriding the request method via a special header or query param when using POST. It can be useful
 * when testing from a browser that does not support PUT or DELETE.
 * <p>
 * Clients may override the HTTP method by setting either the X-HTTP-Method-Override header or the _method form or query
 * parameter in a POST. If both the X-HTTP-Method-Override header and _method parameter are present in
 * the request then the X-HTTP-Method-Override header will be used.
 */
@Provider
@PreMatching
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

    @Override
    public void filter(ContainerRequestContext request)
    {
        String method = request.getHeaders().getFirst(HEADER);
        if (Strings.isNullOrEmpty(method)) {
            method = request.getUriInfo().getQueryParameters().getFirst(METHOD_PARAM);
        }

        if (!Strings.isNullOrEmpty(method)) {
            if (request.getMethod().equalsIgnoreCase("POST")) {
                request.setMethod(method);
            }
            else {
                // TODO: how do we return a response message? how to we format the response or control
                // TODO: content-type for the error message
                throw new WebApplicationException(Response.Status.BAD_REQUEST);
            }
        }
    }
}
