package io.airlift.jmx;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import org.codehaus.jackson.map.ObjectMapper;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/v1/jmx/mbean")
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
    @Produces(MediaType.APPLICATION_JSON)
    public List<MBeanRepresentation> getMBeans()
            throws JMException
    {
        ImmutableList.Builder<MBeanRepresentation> mbeans = ImmutableList.builder();
        for (ObjectName objectName : mbeanServer.queryNames(new ObjectName("*:*"), null)) {
            mbeans.add(new MBeanRepresentation(mbeanServer, objectName, objectMapper));
        }

        return mbeans.build();
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String getMBeansUi()
            throws Exception
    {
        String html = Resources.toString(Resources.getResource(getClass(), "mbeans.html"), Charsets.UTF_8);
        return html;
    }

    @GET
    @Path("{objectName}")
    @Produces(MediaType.APPLICATION_JSON)
    public MBeanRepresentation getMBean(@PathParam("objectName") ObjectName objectName)
            throws JMException
    {
        Preconditions.checkNotNull(objectName, "objectName is null");
        return new MBeanRepresentation(mbeanServer, objectName, objectMapper);
    }
}
