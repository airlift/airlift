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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import io.airlift.log.Logger;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

// This code is based on an early version of JacksonJsonProvider
abstract class AbstractJacksonMapper
        implements MessageBodyReader<Object>, MessageBodyWriter<Object>
{
    private static final Set<Class<?>> IO_CLASSES = ImmutableSet.<Class<?>>builder()
            .add(InputStream.class)
            .add(OutputStream.class)
            .add(Reader.class)
            .add(Writer.class)
            .add(byte[].class)
            .add(char[].class)
            .add(StreamingOutput.class)
            .add(Response.class)
            .build();

    protected final Logger log = Logger.get(getClass());

    protected final ObjectMapper objectMapper;

    protected AbstractJacksonMapper(ObjectMapper objectMapper)
    {
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
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
        try {
            JsonParser jsonParser = getReaderJsonFactory().createParser(inputStream);

            // Do not close underlying stream after mapping
            jsonParser.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);

            return objectMapper.readValue(jsonParser, objectMapper.getTypeFactory().constructType(genericType));
        }
        catch (Exception e) {
            // We want to return a 400 for bad JSON but not for a real IO exception
            if (e instanceof IOException && !(e instanceof JsonProcessingException) && !(e instanceof EOFException)) {
                throw e;
            }

            // log the exception at debug so it can be viewed during development
            // Note: we are not logging at a higher level because this could cause a denial of service
            log.debug(e, "Invalid JSON for Java type: %s", type);

            // Invalid JSON request. Throwing exception so the response code can be overridden using a mapper.
            throw new JsonMapperParsingException(type, e);
        }
    }

    @Override
    public long getSize(Object value, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        // In general figuring output size requires actual writing; usually not worth it to write everything twice.
        return -1;
    }

    protected abstract JsonFactory getReaderJsonFactory();

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
        JavaType rootType = null;
        if (genericType != null && value != null) {
            if (genericType.getClass() != Class.class) { // generic types are other implementations of 'java.lang.reflect.Type'
                rootType = objectMapper.getTypeFactory().constructType(genericType);
                if (rootType.getRawClass() == Object.class) {
                    rootType = null;
                }
            }
        }

        write(value, Optional.ofNullable(rootType), httpHeaders, outputStream);
    }

    protected abstract void write(
            Object value,
            Optional<JavaType> rootType,
            MultivaluedMap<String, Object> httpHeaders,
            OutputStream outputStream)
            throws IOException;
}
