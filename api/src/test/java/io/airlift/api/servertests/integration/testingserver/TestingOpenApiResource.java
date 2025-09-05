package io.airlift.api.servertests.integration.testingserver;

import com.google.common.io.Resources;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Path("testing/openapi/v1")
public class TestingOpenApiResource
{
    @GET
    @Produces(MediaType.TEXT_HTML)
    public String getRedoclyPage(@Context UriInfo uriInfo)
            throws IOException
    {
        String content = Resources.toString(Resources.getResource("openapi.html"), StandardCharsets.UTF_8);
        return content.replace("$HOST$", uriInfo.getBaseUri().getHost());
    }
}
