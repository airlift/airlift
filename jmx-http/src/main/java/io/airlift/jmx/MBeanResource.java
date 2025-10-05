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
package io.airlift.jmx;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import tools.jackson.databind.ObjectMapper;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import java.util.List;

import static com.google.common.io.Resources.getResource;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

@Path("/v1/jmx")
public class MBeanResource
{
    private final MBeanServer mbeanServer;
    private final ObjectMapper objectMapper;

    @Inject
    public MBeanResource(MBeanServer mbeanServer, ObjectMapper objectMapper)
    {
        this.mbeanServer = mbeanServer;
        this.objectMapper = objectMapper;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String getMBeansUi()
            throws Exception
    {
        return Resources.toString(getResource(getClass(), "mbeans.html"), UTF_8);
    }

    @GET
    @Path("mbean")
    @Produces(MediaType.APPLICATION_JSON)
    public List<MBeanRepresentation> getMBeans()
            throws JMException
    {
        ImmutableList.Builder<MBeanRepresentation> mbeans = ImmutableList.builder();
        for (ObjectName objectName : mbeanServer.queryNames(ObjectName.WILDCARD, null)) {
            mbeans.add(new MBeanRepresentation(mbeanServer, objectName, objectMapper));
        }

        return mbeans.build();
    }

    @GET
    @Path("mbean/{objectName}")
    @Produces(MediaType.APPLICATION_JSON)
    public MBeanRepresentation getMBean(@PathParam("objectName") ObjectName objectName)
            throws JMException
    {
        requireNonNull(objectName, "objectName is null");
        return new MBeanRepresentation(mbeanServer, objectName, objectMapper);
    }

    @GET
    @Path("mbean/{objectName}/{attributeName}")
    @Produces(MediaType.APPLICATION_JSON)
    public Object getMBean(@PathParam("objectName") ObjectName objectName, @PathParam("attributeName") String attributeName)
            throws JMException
    {
        requireNonNull(objectName, "objectName is null");
        return mbeanServer.getAttribute(objectName, attributeName);
    }
}
