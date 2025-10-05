package io.airlift.jaxrs;

import com.google.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.NoContentException;
import jakarta.ws.rs.ext.Provider;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.jakarta.rs.json.JacksonJsonProvider;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import static tools.jackson.core.StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION;
import static tools.jackson.jakarta.rs.cfg.JakartaRSFeature.ADD_NO_SNIFF_HEADER;

// For backward compatibility with existing consumers
@Provider
@Consumes({MediaType.APPLICATION_JSON, "text/json"})
@Produces({MediaType.APPLICATION_JSON, "text/json"})
public class JacksonJsonMapper
        extends JacksonJsonProvider
{
    @Inject
    public JacksonJsonMapper(JsonMapper jsonMapper)
    {
        super(jsonMapper
                .rebuild()
                .enable(INCLUDE_SOURCE_IN_LOCATION)
                .build());
        enable(ADD_NO_SNIFF_HEADER);
    }

    /**
     * Throws JsonParsingException only when Jakarta-RS container calls to deserialize given JSON value
     * Need to distinguish between:
     * - JsonProcessingException due to Jakarta-RS deserialization
     * - JsonProcessingException due to operation happening in resource body
     */
    @Override
    public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
            throws JacksonException
    {
        try {
            return super.readFrom(type, genericType, annotations, mediaType, httpHeaders, entityStream);
        }
        catch (JacksonException | NoContentException e) {
            // Re-throw real IO exceptions that are not due to bad JSON
            if (e instanceof JacksonIOException ioException) {
                throw new UncheckedIOException(ioException.getCause());
            }
            if (e instanceof StreamReadException e2) {
                throw e2;
            }
            throw new JsonParsingException(e);
        }
    }
}
