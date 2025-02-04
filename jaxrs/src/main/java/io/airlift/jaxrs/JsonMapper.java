package io.airlift.jaxrs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import com.google.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

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

    /**
     * Throws JsonParsingException only when Jakarta-RS container calls to deserialize given JSON value
     * Need to distinguish between:
     * - JsonProcessingException due to Jakarta-RS deserialization
     * - JsonProcessingException due to operation happening in resource body
     */
    @Override
    public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
            throws IOException
    {
        try {
            return super.readFrom(type, genericType, annotations, mediaType, httpHeaders, entityStream);
        }
        catch (IOException e) {
            // Re-throw real IO exceptions that are not due to bad JSON
            if (!(e instanceof JsonProcessingException) && !(e instanceof EOFException)) {
                throw e;
            }
            throw new JsonParsingException(e);
        }
    }
}
