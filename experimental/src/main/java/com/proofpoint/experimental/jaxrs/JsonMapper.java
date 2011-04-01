package com.proofpoint.experimental.jaxrs;

import com.google.common.collect.ImmutableSet;
import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.spi.container.WebApplication;
import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.type.TypeFactory;
import org.codehaus.jackson.map.util.JSONPObject;
import org.codehaus.jackson.type.JavaType;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

@Provider
@Consumes({MediaType.APPLICATION_JSON, "text/json"})
@Produces({MediaType.APPLICATION_JSON, "text/json"})
public class JsonMapper implements MessageBodyReader<Object>, MessageBodyWriter<Object>
{
    /**
     * Looks like we need to worry about accidental
     * data binding for types we shouldn't be handling. This is
     * probably not a very good way to do it, but let's start by
     * blacklisting things we are not to handle.
     */
    private final static ImmutableSet<Class<?>> IO_CLASSES = ImmutableSet.<Class<?>>builder()
            .add(java.io.InputStream.class)
            .add(java.io.Reader.class)
            .add(java.io.OutputStream.class)
            .add(java.io.Writer.class)
            .add(byte[].class)
            .add(char[].class)
            .add(javax.ws.rs.core.StreamingOutput.class)
            .add(javax.ws.rs.core.Response.class)
            .build();

    private final ObjectMapper objectMapper;

    private final WebApplication webApplication;

    public JsonMapper(ObjectMapper objectMapper, WebApplication webApplication)
    {
        this.objectMapper = objectMapper;
        this.webApplication = webApplication;
    }

    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        return canReadOrWrite(type);
    }

    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        return canReadOrWrite(type);
    }

    private boolean canReadOrWrite(Class<?> type)
    {
        if (IO_CLASSES.contains(type)) {
            return false;
        }
        for (Class<?> ioClass : IO_CLASSES) {
            if (ioClass.isAssignableFrom(type)) {
                return false;
            }
        }

        return true;
    }

    public Object readFrom(Class<Object> type,
            Type genericType,
            Annotation[] annotations,
            MediaType mediaType,
            MultivaluedMap<String, String> httpHeaders,
            InputStream inputStream)
            throws IOException
    {
        JsonParser jsonParser = objectMapper.getJsonFactory().createJsonParser(inputStream);

        // Important: we are NOT to close the underlying stream after
        // mapping, so we need to instruct parser:
        jsonParser.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);

        return objectMapper.readValue(jsonParser, TypeFactory.type(genericType));
    }

    public long getSize(Object value, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        // In general figuring output size requires actual writing; usually not
        // worth it to write everything twice.
        return -1;
    }

    public void writeTo(Object value,
            Class<?> type,
            Type genericType,
            Annotation[] annotations,
            MediaType mediaType,
            MultivaluedMap<String, Object> httpHeaders,
            OutputStream outputStream)
            throws IOException
    {
        JsonGenerator jsonGenerator = objectMapper.getJsonFactory().createJsonGenerator(outputStream, JsonEncoding.UTF8);

        // Important: we are NOT to close the underlying stream after
        // mapping, so we need to instruct generator:
        jsonGenerator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

        // Pretty print?
        if (isPrettyPrintRequested()) {
            jsonGenerator.useDefaultPrettyPrinter();
        }

        // 04-Mar-2010, tatu: How about type we were given? (if any)
        JavaType rootType = null;
        if (genericType != null && value != null) {
            // 10-Jan-2011, tatu: as per [JACKSON-456], it's not safe to just force root
            //    type since it prevents polymorphic type serialization. Since we really
            //    just need this for generics, let's only use generic type if it's truly
            //    generic.
            if (genericType.getClass() != Class.class) { // generic types are other implementations of 'java.lang.reflect.Type'
                // This is still not exactly right; should root type be further
                // specialized with 'value.getClass()'? Let's see how well this works before
                // trying to come up with more complete solution.
                rootType = TypeFactory.type(genericType);
            }
        }

        String jsonpFunctionName = getJsonpFunctionName();
        if (jsonpFunctionName != null) {
            objectMapper.writeValue(jsonGenerator, new JSONPObject(jsonpFunctionName, value, rootType));
        }
        else if (rootType != null) {
            objectMapper.typedWriter(rootType).writeValue(jsonGenerator, value);
        }
        else {
            objectMapper.writeValue(jsonGenerator, value);
        }
    }

    private boolean isPrettyPrintRequested()
    {
        HttpContext httpContext = webApplication.getThreadLocalHttpContext();
        if (httpContext == null) {
            return false;
        }
        UriInfo uriInfo = httpContext.getUriInfo();
        if (uriInfo == null) {
            return false;
        }
        MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();
        return queryParameters != null && queryParameters.containsKey("pretty");
    }

    private String getJsonpFunctionName()
    {
        HttpContext httpContext = webApplication.getThreadLocalHttpContext();
        if (httpContext == null) {
            return null;
        }
        UriInfo uriInfo = httpContext.getUriInfo();
        if (uriInfo == null) {
            return null;
        }
        MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();
        if (queryParameters == null) {
            return null;
        }
        return queryParameters.getFirst("jsonp");
    }
}
