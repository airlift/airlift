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
package com.proofpoint.jaxrs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.proofpoint.log.Logger;
import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.spi.container.WebApplication;
import org.apache.bval.jsr303.ApacheValidationProvider;
import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.type.TypeFactory;
import org.codehaus.jackson.map.util.JSONPObject;
import org.codehaus.jackson.type.JavaType;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Set;

// This code is based on JacksonJsonProvider
@Provider
@Consumes({MediaType.APPLICATION_JSON, "text/json"})
@Produces({MediaType.APPLICATION_JSON, "text/json"})
class JsonMapper implements MessageBodyReader<Object>, MessageBodyWriter<Object>
{
    private static final Validator VALIDATOR = Validation.byProvider(ApacheValidationProvider.class).configure().buildValidatorFactory().getValidator();

    /**
     * Looks like we need to worry about accidental
     * data binding for types we shouldn't be handling. This is
     * probably not a very good way to do it, but let's start by
     * blacklisting things we are not to handle.
     */
    private final static ImmutableSet<Class<?>> IO_CLASSES = ImmutableSet.<Class<?>>builder()
            .add(InputStream.class)
            .add(java.io.Reader.class)
            .add(OutputStream.class)
            .add(java.io.Writer.class)
            .add(byte[].class)
            .add(char[].class)
            .add(javax.ws.rs.core.StreamingOutput.class)
            .add(Response.class)
            .build();
    public static final Logger log = Logger.get(JsonMapper.class);

    private final ObjectMapper objectMapper;

    private final WebApplication webApplication;

    @Inject
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
        Object object;
        try {
            JsonParser jsonParser = objectMapper.getJsonFactory().createJsonParser(inputStream);

            // Important: we are NOT to close the underlying stream after
            // mapping, so we need to instruct parser:
            jsonParser.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);

            object = objectMapper.readValue(jsonParser, TypeFactory.type(genericType));
        }
        catch (Exception e) {
            // we want to return a 400 for bad JSON but not for a real IO exception
            if (e instanceof IOException && !(e instanceof JsonProcessingException) && !(e instanceof EOFException)) {
                throw (IOException) e;
            }

            // log the exception at debug so it can be viewed during development
            // Note: we are not logging at a higher level because this could cause a denial of service
            log.debug(e, "Invalid json for Java type %s", type);

            // invalid json request
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("Invalid json for Java type " + type)
                            .build());
        }

        // validate object using the bean validation framework
        Set<ConstraintViolation<Object>> violations = VALIDATOR.validate(object);
        if (!violations.isEmpty()) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity(messagesFor(violations))
                            .build());
        }


        return object;
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
        // add a newline so when you use curl it looks nice
        outputStream.write('\n');
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

    private static List<String> messagesFor(Collection<? extends ConstraintViolation<?>> violations)
    {
        ImmutableList.Builder<String> messages = new ImmutableList.Builder<String>();
        for (ConstraintViolation<?> violation : violations) {
            messages.add(violation.getPropertyPath().toString() + " " + violation.getMessage());
        }

        return messages.build();
    }
}
