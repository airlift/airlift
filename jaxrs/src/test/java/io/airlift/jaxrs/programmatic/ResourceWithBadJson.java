package io.airlift.jaxrs.programmatic;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/")
public class ResourceWithBadJson {
    public record DummyModel(String name) {}

    @POST
    @Consumes(APPLICATION_JSON)
    public void takeBadJson(DummyModel ignore) {
        // do nothing
    }
}
