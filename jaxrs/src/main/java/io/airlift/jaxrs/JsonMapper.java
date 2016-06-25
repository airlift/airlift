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
package io.airlift.jaxrs;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.util.JSONPObject;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import org.apache.bval.jsr.ApacheValidationProvider;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

// This code is based on JacksonJsonProvider
@Provider
@Consumes({MediaType.APPLICATION_JSON, "text/json"})
@Produces({MediaType.APPLICATION_JSON, "text/json"})
public class JsonMapper
        implements MessageBodyReader<Object>, MessageBodyWriter<Object>
{
    private static final Validator VALIDATOR = Validation.byProvider(ApacheValidationProvider.class).configure().buildValidatorFactory().getValidator();

    /**
     * Looks like we need to worry about accidental
     * data binding for types we shouldn't be handling. This is
     * probably not a very good way to do it, but let's start by
     * blacklisting things we are not to handle.
     */
    private static final Set<Class<?>> IO_CLASSES = ImmutableSet.<Class<?>>builder()
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

    private final AtomicReference<UriInfo> uriInfo = new AtomicReference<>();

    @Inject
    public JsonMapper(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    @Context
    public void setUriInfo(UriInfo uriInfo)
    {
        this.uriInfo.set(uriInfo);
    }

    private UriInfo getUriInfo()
    {
        return this.uriInfo.get();
    }

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        return canReadOrWrite(type);
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        return canReadOrWrite(type);
    }

    private static boolean canReadOrWrite(Class<?> type)
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

    @Override
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
            JsonParser jsonParser = objectMapper.getFactory().createJsonParser(inputStream);

            // Important: we are NOT to close the underlying stream after
            // mapping, so we need to instruct parser:
            jsonParser.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);

            object = objectMapper.readValue(jsonParser, objectMapper.getTypeFactory().constructType(genericType));
        }
        catch (Exception e) {
            // We want to handle parsing exceptions differently than regular IOExceptions so just rethrow IOExceptions
            if (e instanceof IOException && !(e instanceof JsonProcessingException) && !(e instanceof EOFException)) {
                throw e;
            }

            // log the exception at debug so it can be viewed during development
            // Note: we are not logging at a higher level because this could cause a denial of service
            log.debug(e, "Invalid json for Java type %s", type);

            // Invalid json request. Throwing exception so the response code can be overridden using a mapper.
            throw new JsonMapperParsingException(type, e);
        }

        // validate object using the bean validation framework
        Set<ConstraintViolation<Object>> violations = VALIDATOR.validate(object);
        if (!violations.isEmpty()) {
            throw new BeanValidationException(violations);
        }

        return object;
    }

    @Override
    public long getSize(Object value, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        // In general figuring output size requires actual writing; usually not
        // worth it to write everything twice.
        return -1;
    }

    @Override
    public void writeTo(Object value,
            Class<?> type,
            Type genericType,
            Annotation[] annotations,
            MediaType mediaType,
            MultivaluedMap<String, Object> httpHeaders,
            OutputStream outputStream)
            throws IOException
    {
        // Prevent broken browser from attempting to render the json as html
        httpHeaders.add(HttpHeaders.X_CONTENT_TYPE_OPTIONS, "nosniff");

        JsonFactory jsonFactory = objectMapper.getJsonFactory();
        jsonFactory.setCharacterEscapes(HTMLCharacterEscapes.INSTANCE);

        JsonGenerator jsonGenerator = jsonFactory.createJsonGenerator(outputStream, JsonEncoding.UTF8);

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
                rootType = objectMapper.getTypeFactory().constructType(genericType);
                // 26-Feb-2011, tatu: To help with [JACKSON-518], we better recognize cases where
                //    type degenerates back into "Object.class" (as is the case with plain TypeVariable,
                //    for example), and not use that.
                //
                if (rootType.getRawClass() == Object.class) {
                    rootType = null;
                }
            }
        }

        String jsonpFunctionName = getJsonpFunctionName();
        if (jsonpFunctionName != null) {
            value = new JSONPObject(jsonpFunctionName, value, rootType);
            rootType = null;
        }

        ObjectWriter writer;
        if (rootType != null) {
            writer = objectMapper.writerWithType(rootType);
        }
        else {
            writer = objectMapper.writer();
        }

        try {
            writer.writeValue(jsonGenerator, value);

            // add a newline so when you use curl it looks nice
            outputStream.write('\n');
        }
        catch (EOFException e) {
            // ignore EOFException
            // This happens when the client terminates the connection when data
            // is being written.  If the exception is allowed to propagate to
            // Jersey, the exception will be logged, but this error is not
            // important.  This is safe since the output stream is already
            // closed.
        }
    }

    private boolean isPrettyPrintRequested()
    {
        UriInfo uriInfo = getUriInfo();
        if (uriInfo == null) {
            return false;
        }

        MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();
        return queryParameters != null && queryParameters.containsKey("pretty");
    }

    private String getJsonpFunctionName()
    {
        UriInfo uriInfo = getUriInfo();
        if (uriInfo == null) {
            return null;
        }

        MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();
        if (queryParameters == null) {
            return null;
        }
        return queryParameters.getFirst("jsonp");
    }

    private static class HTMLCharacterEscapes
            extends CharacterEscapes
    {
        private static final HTMLCharacterEscapes INSTANCE = new HTMLCharacterEscapes();

        private final int[] asciiEscapes;

        private HTMLCharacterEscapes()
        {
            // start with set of characters known to require escaping (double-quote, backslash etc)
            int[] esc = CharacterEscapes.standardAsciiEscapesForJSON();

            // and force escaping of a few others:
            esc['<'] = CharacterEscapes.ESCAPE_STANDARD;
            esc['>'] = CharacterEscapes.ESCAPE_STANDARD;
            esc['&'] = CharacterEscapes.ESCAPE_STANDARD;
            esc['\''] = CharacterEscapes.ESCAPE_STANDARD;

            asciiEscapes = esc;
        }

        @Override
        public int[] getEscapeCodesForAscii()
        {
            return asciiEscapes;
        }

        @Override
        public SerializableString getEscapeSequence(int ch)
        {
            // no further escaping (beyond ASCII chars) needed:
            return null;
        }
    }
}
