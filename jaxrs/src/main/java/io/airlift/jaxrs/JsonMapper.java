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
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.util.JSONPObject;
import com.google.common.net.HttpHeaders;
import com.google.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Provider
@Consumes({MediaType.APPLICATION_JSON, "text/json"})
@Produces({MediaType.APPLICATION_JSON, "text/json"})
public class JsonMapper
        extends AbstractJacksonMapper
{
    private final AtomicReference<UriInfo> uriInfo = new AtomicReference<>();

    @Inject
    public JsonMapper(ObjectMapper objectMapper)
    {
        super(objectMapper);
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
    protected JsonFactory getReaderJsonFactory()
    {
        return objectMapper.getFactory();
    }

    @Override
    protected void write(Object value,
            Optional<JavaType> rootType,
            MultivaluedMap<String, Object> httpHeaders,
            OutputStream outputStream)
            throws IOException
    {
        // Prevent broken browser from attempting to render the json as html
        httpHeaders.add(HttpHeaders.X_CONTENT_TYPE_OPTIONS, "nosniff");

        JsonFactory jsonFactory = objectMapper.getFactory();
        jsonFactory.setCharacterEscapes(HTMLCharacterEscapes.INSTANCE);

        JsonGenerator jsonGenerator = jsonFactory.createGenerator(outputStream, JsonEncoding.UTF8);

        // Do not close underlying stream after mapping
        jsonGenerator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

        // Pretty print?
        if (isPrettyPrintRequested()) {
            jsonGenerator.useDefaultPrettyPrinter();
        }

        String jsonpFunctionName = getJsonpFunctionName();
        if (jsonpFunctionName != null) {
            value = new JSONPObject(jsonpFunctionName, value, rootType.orElse(null));
            rootType = Optional.empty();
        }

        ObjectWriter writer = rootType.map(objectMapper::writerFor)
                .orElseGet(objectMapper::writer);

        try {
            writer.writeValue(jsonGenerator, value);

            // add a newline so when you use curl it looks nice
            outputStream.write('\n');
        }
        catch (EOFException e) {
            // ignore EOFException
            // This happens when the client terminates the connection when data
            // is being written.  If the exception is allowed to propagate,
            // the exception will be logged, but this error is not important.
            // This is safe since the output stream is already closed.
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
