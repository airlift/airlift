/*
 * Copyright 2015 Proofpoint, Inc.
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
package com.proofpoint.jaxrs;

import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.jersey.server.ExtendedResourceContext;
import org.glassfish.jersey.server.wadl.internal.WadlApplicationContextImpl;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Throwables.propagate;

@Path("/admin/wadl")
public class WadlResource
{
    private final AtomicReference<org.glassfish.jersey.server.wadl.internal.WadlResource> wadlResource = new AtomicReference<>();

    public void setLocator(ServiceLocator locator)
    {
        WadlApplicationContextImpl wadlContext = new WadlApplicationContextImpl(locator,
                locator.getService(Configuration.class),
                locator.getService(ExtendedResourceContext.class));

        org.glassfish.jersey.server.wadl.internal.WadlResource wadlResource =
                new org.glassfish.jersey.server.wadl.internal.WadlResource();
        try {
            Field wadlContextField = org.glassfish.jersey.server.wadl.internal.WadlResource.class.getDeclaredField("wadlContext");
            wadlContextField.setAccessible(true);
            wadlContextField.set(wadlResource, wadlContext);
        }
        catch (NoSuchFieldException | IllegalAccessException e) {
            throw propagate(e);
        }

        this.wadlResource.set(wadlResource);
    }

    @Produces({"application/vnd.sun.wadl+xml", "application/xml"})
    @GET
    public Response getWadl(@Context UriInfo uriInfo)
    {
        return wadlResource.get().getWadl(uriInfo);
    }
}
