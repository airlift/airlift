package io.airlift.jaxrs;

import com.google.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.jakarta.rs.json.JacksonJsonProvider;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import static tools.jackson.core.StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION;
import static tools.jackson.jakarta.rs.cfg.JakartaRSFeature.ADD_NO_SNIFF_HEADER;

// For backward compatibility with existing consumers
@Provider
@Consumes({MediaType.APPLICATION_JSON, "text/json"})
@Produces({MediaType.APPLICATION_JSON, "text/json"})
public class JaxRsJsonMapper
        implements MessageBodyReader<Object>, MessageBodyWriter<Object>
{
    // Jackson 3 providers only throw unchecked exceptions; delegating (rather than extending)
    // keeps the JAX-RS contract of reporting bad JSON and I/O failures as IOException
    private final JacksonJsonProvider provider;

    @Inject
    public JaxRsJsonMapper(JsonMapper jsonMapper)
    {
        provider = new JacksonJsonProvider(jsonMapper.rebuild()
                .enable(INCLUDE_SOURCE_IN_LOCATION)
                .build());
        provider.enable(ADD_NO_SNIFF_HEADER);
    }

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        return provider.isReadable(type, genericType, annotations, mediaType);
    }

    /**
     * Throws JsonParsingException only when Jakarta-RS container calls to deserialize given JSON value
     * Need to distinguish between:
     * - JacksonException due to Jakarta-RS deserialization
     * - JacksonException due to operation happening in resource body
     */
    @Override
    public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
            throws IOException
    {
        try {
            return provider.readFrom(type, genericType, annotations, mediaType, httpHeaders, entityStream);
        }
        catch (JacksonIOException e) {
            // A truncated body is bad JSON; re-throw real IO exceptions
            if (e.getCause() instanceof EOFException) {
                throw new JsonParsingException(e);
            }
            throw unwrap(e);
        }
        catch (JacksonException e) {
            throw new JsonParsingException(e);
        }
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        return provider.isWriteable(type, genericType, annotations, mediaType);
    }

    @Override
    public void writeTo(Object value, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
            throws IOException
    {
        try {
            provider.writeTo(value, type, genericType, annotations, mediaType, httpHeaders, entityStream);
        }
        catch (JacksonIOException e) {
            throw unwrap(e);
        }
    }

    private static IOException unwrap(JacksonIOException e)
    {
        return (e.getCause() instanceof IOException ioException) ? ioException : new IOException(e);
    }
}
