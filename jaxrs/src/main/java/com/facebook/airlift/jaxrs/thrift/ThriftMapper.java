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
package com.facebook.airlift.jaxrs.thrift;

import com.facebook.airlift.log.Logger;
import com.facebook.drift.codec.ThriftCodec;
import com.facebook.drift.codec.ThriftCodecManager;
import com.facebook.drift.protocol.TFacebookCompactProtocol;
import com.facebook.drift.protocol.TProtocol;
import com.google.common.collect.ImmutableSet;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

// This code is based on JacksonJsonProvider
@Provider
@Consumes("application/x-thrift")
@Produces("application/x-thrift; qs=0.1")
public class ThriftMapper
        implements MessageBodyReader<Object>, MessageBodyWriter<Object>
{
    /**
     * Looks like we need to worry about accidental
     * data binding for types we shouldn't be handling. This is
     * probably not a very good way to do it, but let's start by
     * blacklisting things we are not to handle.
     */
    private static final ImmutableSet<Class<?>> IO_CLASSES = ImmutableSet.<Class<?>>builder()
            .add(InputStream.class)
            .add(java.io.Reader.class)
            .add(OutputStream.class)
            .add(java.io.Writer.class)
            .add(byte[].class)
            .add(char[].class)
            .add(javax.ws.rs.core.StreamingOutput.class)
            .add(Response.class)
            .build();

    public static final Logger log = Logger.get(ThriftMapper.class);

    private final ThriftCodecManager thriftCodecManager;

    @Inject
    public ThriftMapper(ThriftCodecManager thriftCodecManager)
    {
        this.thriftCodecManager = thriftCodecManager;
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
            ThriftCodec<?> codec = thriftCodecManager.getCodec(type);
            TProtocol tProtocol = new TFacebookCompactProtocol(new TInputStreamTransport(inputStream));
            return codec.read(tProtocol);
        }
        catch (Exception e) {
            // we want to return a 400 for bad Thrift but not for a real IO exception
            if (e instanceof IOException && !(e instanceof EOFException)) {
                throw (IOException) e;
            }
            // log the exception at debug so it can be viewed during development
            // Note: we are not logging at a higher level because this could cause a denial of service
            log.debug(e, "Invalid Thrift for Java type %s", type);
            // Invalid thrift request. Throwing exception so the response code can be overridden using a mapper.
            throw new ThriftMapperParsingException(type, e);
        }
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
        try {
            ThriftCodec codec = thriftCodecManager.getCodec(type);
            TProtocol tProtocol = new TFacebookCompactProtocol(new TOutputStreamTransport(outputStream));
            codec.write(value, tProtocol);
        }
        catch (Exception e) {
            log.debug(e, "Can not serialize to thrift for Java type %s", type);
            throw new ThriftMapperParsingException(type, e);
        }
    }
}
