package io.airlift.jaxrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import com.google.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;

import static com.fasterxml.jackson.core.JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION;
import static com.fasterxml.jackson.jakarta.rs.cfg.JakartaRSFeature.ADD_NO_SNIFF_HEADER;

// For backward compatibility with existing consumers
@Provider
@Consumes({MediaType.APPLICATION_JSON, "text/json"})
@Produces({MediaType.APPLICATION_JSON, "text/json"})
public class JsonMapper
        extends JacksonJsonProvider
{
    @Inject
    public JsonMapper(ObjectMapper objectMapper)
    {
        super(objectMapper);
        enable(ADD_NO_SNIFF_HEADER);
        enable(INCLUDE_SOURCE_IN_LOCATION);
    }
}
