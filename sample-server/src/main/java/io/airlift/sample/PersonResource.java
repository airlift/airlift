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
package io.airlift.sample;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import static io.airlift.sample.PersonRepresentation.from;

@Path("/v1/person/{id: \\w+}")
public class PersonResource
{
    private final PersonStore store;

    @Inject
    public PersonResource(PersonStore store)
    {
        Preconditions.checkNotNull(store, "store must not be null");

        this.store = store;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@PathParam("id") String id, @Context UriInfo uriInfo)
    {
        Preconditions.checkNotNull(id, "id must not be null");

        Person person = store.get(id);

        if (person == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("[" + id + "]").build();
        }

        return Response.ok(from(person, uriInfo.getRequestUri())).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response put(@PathParam("id") String id, PersonRepresentation person)
    {
        Preconditions.checkNotNull(id, "id must not be null");
        Preconditions.checkNotNull(person, "person must not be null");

        boolean added = store.put(id, person.toPerson());
        if (added) {
            UriBuilder uri = UriBuilder.fromResource(PersonResource.class);
            return Response.created(uri.build(id)).build();
        }

        return Response.noContent().build();
    }

    @DELETE
    public Response delete(@PathParam("id") String id)
    {
        Preconditions.checkNotNull(id, "id must not be null");

        if (!store.delete(id)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.noContent().build();
    }
}
